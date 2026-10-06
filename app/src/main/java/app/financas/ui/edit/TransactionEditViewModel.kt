package app.financas.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.AccountTransactionEntity
import app.financas.data.CardPurchaseEntity
import app.financas.data.FinanceRepository
import app.financas.data.PersonEntity
import app.financas.data.Snapshot
import app.financas.domain.Billing
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.TxType
import app.financas.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class EditMode { ACCOUNT, CARD }

data class SplitRow(val personId: Long, val selected: Boolean, val shareText: String)

data class EditForm(
    val loaded: Boolean = false,
    val mode: EditMode = EditMode.ACCOUNT,
    val editingId: Long? = null,
    val type: TxType = TxType.OUT,
    val amountText: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val notes: String = "",
    val accountId: Long? = null,
    val cardId: Long? = null,
    val categoryId: Long? = null,
    val installments: Int = 1,
    val firstInstallment: Int = 1,
    val firstInvoice: YearMonth? = null,
    val splits: List<SplitRow> = emptyList(),
    val customShares: Boolean = false,
    val fitId: String? = null,
    val importedMemo: String? = null,
    val error: String? = null,
    val message: String? = null,
    val finished: Boolean = false,
)

data class EditUi(val form: EditForm, val data: Snapshot) {
    val people: List<PersonEntity> get() = data.people
    val categories get() = data.categories.filter { it.type == form.type }
    val amount: Long? get() = Money.parse(form.amountText)
    val isEditing: Boolean get() = form.editingId != null

    /** "6× de R$ 80,00" */
    val installmentSummary: String?
        get() {
            val total = amount ?: return null
            if (total <= 0) return null
            val per = Money.splitEven(total, form.installments).first()
            return "${form.installments}× de ${Money.format(per)}"
        }

    /** "1ª parcela na fatura de out/2026 (fecha 25/10)" */
    val invoiceHint: String?
        get() {
            val card = form.cardId?.let { data.cardById[it] } ?: return null
            val invoice = if (form.firstInstallment > 1 && form.firstInvoice != null) form.firstInvoice
            else Billing.invoiceFor(form.date, card.closingDay)
            val label = if (form.firstInstallment > 1) "${form.firstInstallment}ª parcela" else "1ª parcela"
            return "$label na fatura de ${Dates.monthShortYear(invoice)} (fecha ${Dates.dayMonth(Billing.closingDate(invoice, card.closingDay))})"
        }

    val sharesSum: Long get() = form.splits.filter { it.selected }.sumOf { Money.parse(it.shareText) ?: 0L }
}

class TransactionEditViewModel(
    private val repository: FinanceRepository,
    handle: SavedStateHandle,
) : ViewModel() {

    private val form = MutableStateFlow(EditForm())

    val ui: StateFlow<EditUi?> = combine(form, repository.snapshot) { f, s -> EditUi(f, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val mode = if (handle.get<String>("mode") == Routes.MODE_CARD) EditMode.CARD else EditMode.ACCOUNT
        val id = handle.get<Long>("id") ?: -1L
        val recurringId = handle.get<Long>("recurringId") ?: -1L
        viewModelScope.launch { load(mode, id, recurringId) }
    }

    private suspend fun load(mode: EditMode, id: Long, recurringId: Long) {
        val s = repository.snapshot.first()
        var f = EditForm(
            loaded = true,
            mode = mode,
            accountId = s.accounts.firstOrNull()?.id,
            cardId = s.cards.firstOrNull()?.id,
            splits = defaultSplits(s),
        )
        if (id > 0 && mode == EditMode.ACCOUNT) {
            repository.accountTransaction(id)?.let { t ->
                f = f.copy(
                    editingId = t.id, type = t.type, amountText = Money.plain(t.amount), description = t.description,
                    date = t.date, notes = t.notes.orEmpty(), accountId = t.accountId, categoryId = t.categoryId,
                    fitId = t.fitId, importedMemo = t.importedMemo,
                )
            }
        } else if (id > 0 && mode == EditMode.CARD) {
            repository.purchase(id)?.let { p ->
                val splits = repository.splitsOf(p.id).associate { it.personId to it.share }
                f = f.copy(
                    editingId = p.id, type = p.type, amountText = Money.plain(p.totalAmount), description = p.description,
                    date = p.date, cardId = p.cardId, categoryId = p.categoryId, installments = p.installments,
                    firstInstallment = p.firstInstallment, firstInvoice = p.firstInvoice, fitId = p.fitId,
                    importedMemo = p.importedMemo,
                    customShares = splits.isNotEmpty(),
                    splits = if (splits.isEmpty()) defaultSplits(s, p.totalAmount) else s.people.map { person ->
                        val share = splits[person.id]
                        SplitRow(person.id, share != null, share?.let(Money::plain).orEmpty())
                    },
                )
            }
        } else if (recurringId > 0) {
            repository.recurring(recurringId)?.let { r ->
                f = f.copy(
                    type = TxType.OUT, amountText = Money.plain(r.amount), description = r.name,
                    categoryId = r.categoryId,
                )
            }
        }
        form.value = recalcShares(f)
    }

    private fun defaultSplits(s: Snapshot, total: Long = 0): List<SplitRow> =
        s.people.map { SplitRow(it.id, it.isMe, if (it.isMe && total > 0) Money.plain(total) else "") }

    // ---- Edição dos campos ----
    fun setMode(mode: EditMode) = edit {
        it.copy(mode = mode, type = if (mode == EditMode.CARD) TxType.OUT else it.type, categoryId = null)
    }
    fun setType(type: TxType) = edit { if (it.type == type) it else it.copy(type = type, categoryId = null) }
    fun setAmount(text: String) = edit { recalcShares(it.copy(amountText = text)) }
    fun setDescription(text: String) = edit { it.copy(description = text) }
    fun setDate(date: LocalDate) = edit { it.copy(date = date) }
    fun setNotes(text: String) = edit { it.copy(notes = text) }
    fun setAccount(id: Long) = edit { it.copy(accountId = id) }
    fun setCard(id: Long) = edit { it.copy(cardId = id) }
    fun setCategory(id: Long?) = edit { it.copy(categoryId = if (it.categoryId == id) null else id) }
    fun setInstallments(n: Int) = edit { it.copy(installments = n.coerceIn(maxOf(1, it.firstInstallment), 72)) }
    fun togglePerson(personId: Long) = edit { f ->
        recalcShares(f.copy(splits = f.splits.map { if (it.personId == personId) it.copy(selected = !it.selected) else it }), force = true)
    }
    fun setShare(personId: Long, text: String) = edit { f ->
        f.copy(customShares = true, splits = f.splits.map { if (it.personId == personId) it.copy(shareText = text) else it })
    }
    fun splitEvenly() = edit { recalcShares(it.copy(customShares = false), force = true) }
    fun consumeMessage() = form.update { it.copy(message = null) }

    private fun edit(transform: (EditForm) -> EditForm) = form.update { transform(it).copy(error = null) }

    /** Reparte o valor igualmente entre as pessoas marcadas (se o usuário não personalizou). */
    private fun recalcShares(f: EditForm, force: Boolean = false): EditForm {
        if (f.customShares && !force) return f
        val total = Money.parse(f.amountText)?.takeIf { it > 0 } ?: return f.copy(
            splits = f.splits.map { it.copy(shareText = "") }, customShares = false,
        )
        val selected = f.splits.filter { it.selected }
        if (selected.isEmpty()) return f.copy(splits = f.splits.map { it.copy(shareText = "") })
        val parts = Money.splitEven(total, selected.size)
        var i = 0
        return f.copy(
            customShares = false,
            splits = f.splits.map { if (it.selected) it.copy(shareText = Money.plain(parts[i++])) else it.copy(shareText = "") },
        )
    }

    // ---- Salvar / excluir ----
    fun save(andNew: Boolean) {
        val f = form.value
        val amount = Money.parse(f.amountText)
        val error = when {
            amount == null || amount <= 0 -> "Informe um valor maior que zero."
            f.description.isBlank() -> "Informe a descrição."
            f.mode == EditMode.ACCOUNT && f.accountId == null -> "Cadastre uma conta antes de lançar."
            f.mode == EditMode.CARD && f.cardId == null -> "Cadastre um cartão antes de lançar."
            f.mode == EditMode.CARD && f.splits.none { it.selected } -> "Escolha quem vai pagar."
            f.mode == EditMode.CARD && f.customShares && f.splits.filter { it.selected }.sumOf { Money.parse(it.shareText) ?: 0 } != amount ->
                "A soma das partes precisa ser igual ao valor total (${Money.format(amount ?: 0L)})."
            else -> null
        }
        if (error != null) {
            form.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            if (f.mode == EditMode.ACCOUNT) {
                repository.saveAccountTransaction(
                    AccountTransactionEntity(
                        id = f.editingId ?: 0, accountId = f.accountId!!, description = f.description.trim(),
                        amount = amount!!, type = f.type, date = f.date, categoryId = f.categoryId,
                        notes = f.notes.trim().ifEmpty { null }, fitId = f.fitId, importedMemo = f.importedMemo,
                    )
                )
            } else {
                val shares = f.splits.filter { it.selected }
                    .associate { it.personId to (Money.parse(it.shareText) ?: 0L) }
                repository.savePurchase(
                    CardPurchaseEntity(
                        id = f.editingId ?: 0, cardId = f.cardId!!, description = f.description.trim(),
                        totalAmount = amount!!, type = f.type, date = f.date, installments = f.installments,
                        firstInstallment = f.firstInstallment, firstInvoice = f.firstInvoice ?: YearMonth.from(f.date),
                        categoryId = f.categoryId, fitId = f.fitId, importedMemo = f.importedMemo,
                    ),
                    shares,
                )
            }
            if (andNew) {
                val s = repository.snapshot.first()
                form.value = recalcShares(
                    EditForm(
                        loaded = true, mode = f.mode, type = f.type, date = f.date,
                        accountId = f.accountId, cardId = f.cardId, splits = defaultSplits(s),
                        message = "Lançamento salvo",
                    )
                )
            } else {
                form.update { it.copy(finished = true) }
            }
        }
    }

    fun delete() {
        val f = form.value
        val id = f.editingId ?: return
        viewModelScope.launch {
            if (f.mode == EditMode.ACCOUNT) repository.deleteAccountTransaction(id) else repository.deletePurchase(id)
            form.update { it.copy(finished = true) }
        }
    }
}
