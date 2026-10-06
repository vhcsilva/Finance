package app.financas.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.AccountTransactionEntity
import app.financas.data.CardPurchaseEntity
import app.financas.data.DefaultCategories
import app.financas.data.FinanceRepository
import app.financas.data.ImportedPurchase
import app.financas.data.Snapshot
import app.financas.domain.TxType
import app.financas.domain.ofx.ExistingRecord
import app.financas.domain.ofx.ImportCandidate
import app.financas.domain.ofx.ImportHeuristics
import app.financas.domain.ofx.ImportPlanner
import app.financas.domain.ofx.OfxParser
import app.financas.domain.ofx.OfxStatement
import app.financas.domain.ofx.PlanContext
import app.financas.domain.ofx.PlanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

enum class ImportStep { PICK, DESTINATION, REVIEW, DONE }

/** Destino da importação: "a<id>" = conta, "c<id>" = cartão. */
data class DestinationKey(val isCard: Boolean, val id: Long)

data class ImportForm(
    val step: ImportStep = ImportStep.PICK,
    val busy: Boolean = false,
    val error: String? = null,
    val fileName: String? = null,
    val statement: OfxStatement? = null,
    val destination: DestinationKey? = null,
    val invoice: YearMonth? = null,
    val options: PlanOptions = PlanOptions(),
    val candidates: List<ImportCandidate> = emptyList(),
    val onlyPending: Boolean = false,
    val editingId: Int? = null,
    val importedCount: Int = 0,
)

data class ImportUi(val form: ImportForm, val data: Snapshot) {
    val isCardDestination: Boolean get() = form.destination?.isCard == true
    val selected: List<ImportCandidate> get() = form.candidates.filter { it.selected }
    val selectedTotal: Long get() = selected.sumOf { if (it.type == TxType.OUT) it.amount else -it.amount }
    val withoutCategory: Int get() = form.candidates.count { it.selected && it.categoryId == null }
    val duplicates: Int get() = form.candidates.count { it.duplicate }
    val visible: List<ImportCandidate>
        get() = if (form.onlyPending) form.candidates.filter { it.categoryId == null || !it.selected || it.duplicate } else form.candidates
    val destinationName: String
        get() = form.destination?.let { d ->
            if (d.isCard) data.cardById[d.id]?.name else data.accountById[d.id]?.name
        } ?: ""
}

class ImportViewModel(private val repository: FinanceRepository) : ViewModel() {
    private val form = MutableStateFlow(ImportForm())

    val ui: StateFlow<ImportUi?> = combine(form, repository.snapshot) { f, s -> ImportUi(f, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Passo 1: lê e interpreta o arquivo. */
    fun load(bytes: ByteArray?, fileName: String?) {
        if (bytes == null) {
            form.update { it.copy(error = "Não foi possível ler o arquivo.") }
            return
        }
        form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { OfxParser.parse(bytes) } }
            val statement = result.getOrNull()
            if (statement == null) {
                form.update {
                    it.copy(busy = false, error = result.exceptionOrNull()?.message ?: "Arquivo OFX inválido.")
                }
                return@launch
            }
            if (statement.transactions.isEmpty()) {
                form.update { it.copy(busy = false, error = "O arquivo não tem transações.") }
                return@launch
            }
            val s = repository.snapshot.first()
            val destination = suggestDestination(s, statement)
            form.value = ImportForm(
                step = ImportStep.DESTINATION,
                fileName = fileName,
                statement = statement,
                destination = destination,
                invoice = destination?.let { invoiceFor(s, statement, it) },
            )
        }
    }

    private fun suggestDestination(s: Snapshot, st: OfxStatement): DestinationKey? {
        val ofxId = st.accountId
        if (st.isCreditCard) {
            val card = s.cards.firstOrNull { ofxId != null && it.ofxAccountId == ofxId } ?: s.cards.firstOrNull()
            if (card != null) return DestinationKey(true, card.id)
        } else {
            val acc = s.accounts.firstOrNull { ofxId != null && it.ofxAccountId == ofxId } ?: s.accounts.firstOrNull()
            if (acc != null) return DestinationKey(false, acc.id)
        }
        return s.accounts.firstOrNull()?.let { DestinationKey(false, it.id) }
            ?: s.cards.firstOrNull()?.let { DestinationKey(true, it.id) }
    }

    private fun invoiceFor(s: Snapshot, st: OfxStatement, d: DestinationKey): YearMonth? {
        if (!d.isCard) return null
        val card = s.cardById[d.id] ?: return null
        return ImportPlanner.inferInvoice(st, card.closingDay) ?: YearMonth.now()
    }

    // ---- Passo 2: destino e opções ----
    fun setDestination(d: DestinationKey) {
        viewModelScope.launch {
            val s = repository.snapshot.first()
            form.update { f -> f.copy(destination = d, invoice = f.statement?.let { invoiceFor(s, it, d) }) }
        }
    }

    fun shiftInvoice(delta: Long) = form.update { f -> f.copy(invoice = f.invoice?.plusMonths(delta)) }
    fun setOptions(options: PlanOptions) = form.update { it.copy(options = options) }
    fun back() = form.update {
        when (it.step) {
            ImportStep.REVIEW -> it.copy(step = ImportStep.DESTINATION, editingId = null)
            ImportStep.DESTINATION -> ImportForm()
            else -> it
        }
    }

    fun goToReview() {
        val f = form.value
        val st = f.statement ?: return
        val d = f.destination ?: run {
            form.update { it.copy(error = "Cadastre uma conta ou cartão para receber as transações.") }
            return
        }
        viewModelScope.launch {
            val s = repository.snapshot.first()
            val ctx = PlanContext(
                isCard = d.isCard,
                existing = existingFor(s, d),
                history = s.history,
                invoice = f.invoice,
                transferOutCategoryId = s.categories.firstOrNull { it.type == TxType.OUT && it.name == DefaultCategories.TRANSFER }?.id,
                transferInCategoryId = s.categories.firstOrNull { it.type == TxType.IN && it.name == DefaultCategories.TRANSFER }?.id,
                invoicePaymentCategoryId = s.categories.firstOrNull { it.type == TxType.OUT && it.name == DefaultCategories.INVOICE_PAYMENT }?.id,
            )
            val meId = s.me?.id
            val candidates = ImportPlanner.plan(st, ctx, f.options).map { c ->
                // Categoria sugerida precisa ser do mesmo tipo (entrada/saída) da transação.
                val validCategory = c.categoryId?.takeIf { s.categoryById[it]?.type == c.type }
                c.copy(categoryId = validCategory, personIds = if (d.isCard) setOfNotNull(meId) else emptySet())
            }
            form.update { it.copy(step = ImportStep.REVIEW, candidates = candidates, error = null, onlyPending = false) }
        }
    }

    private fun existingFor(s: Snapshot, d: DestinationKey): List<ExistingRecord> =
        if (!d.isCard) {
            s.transactions.filter { it.accountId == d.id }.map {
                ExistingRecord(it.fitId, it.date, it.amount, it.type, ImportHeuristics.normalize(it.importedMemo ?: it.description))
            }
        } else {
            s.installments.filter { it.cardId == d.id }.mapNotNull { inst ->
                val p = s.purchaseById[inst.purchaseId] ?: return@mapNotNull null
                ExistingRecord(
                    fitId = if (inst.number == p.firstInstallment) p.fitId else null,
                    date = s.installmentDate(p, inst.number),
                    amount = inst.amount,
                    type = p.type,
                    normalized = ImportHeuristics.normalize(p.importedMemo ?: p.description),
                    installmentNumber = inst.number,
                    invoice = inst.invoice,
                )
            }
        }

    // ---- Passo 3: revisão ----
    fun toggle(id: Int) = updateCandidate(id) { it.copy(selected = !it.selected) }
    fun setOnlyPending(value: Boolean) = form.update { it.copy(onlyPending = value) }
    fun openEditor(id: Int?) = form.update { it.copy(editingId = id) }

    fun updateCandidate(id: Int, transform: (ImportCandidate) -> ImportCandidate) = form.update { f ->
        f.copy(candidates = f.candidates.map { if (it.id == id) transform(it) else it })
    }

    /** Aplica pessoas a todas as selecionadas (cartão) e categoria às selecionadas sem categoria. */
    fun applyToAll(personIds: Set<Long>?, categoryOut: Long?, categoryIn: Long?) = form.update { f ->
        f.copy(candidates = f.candidates.map { c ->
            if (!c.selected) return@map c
            var n = c
            if (personIds != null && personIds.isNotEmpty()) n = n.copy(personIds = personIds)
            if (n.categoryId == null) {
                val cat = if (n.type == TxType.OUT) categoryOut else categoryIn
                if (cat != null) n = n.copy(categoryId = cat)
            }
            n
        })
    }

    fun commit() {
        val f = form.value
        val d = f.destination ?: return
        val chosen = f.candidates.filter { it.selected }
        if (chosen.isEmpty()) {
            form.update { it.copy(error = "Selecione ao menos uma transação.") }
            return
        }
        form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val s = repository.snapshot.first()
            val ofxId = f.statement?.accountId
            if (!d.isCard) {
                val account = s.accountById[d.id] ?: return@launch
                repository.importIntoAccount(
                    account,
                    chosen.map { c ->
                        AccountTransactionEntity(
                            accountId = d.id, description = c.description.trim().ifEmpty { c.rawMemo },
                            amount = c.amount, type = c.type, date = c.date, categoryId = c.categoryId,
                            fitId = c.fitId, importedMemo = c.rawMemo,
                        )
                    },
                    ofxId,
                )
            } else {
                val card = s.cardById[d.id] ?: return@launch
                val invoice = f.invoice ?: YearMonth.now()
                repository.importIntoCard(
                    card,
                    chosen.map { c ->
                        val inst = c.installment.takeIf { f.options.detectInstallments }
                        val purchase = CardPurchaseEntity(
                            cardId = d.id,
                            description = c.description.trim().ifEmpty { c.rawMemo },
                            totalAmount = if (inst != null) c.amount * inst.total else c.amount,
                            type = c.type,
                            date = c.date,
                            installments = inst?.total ?: 1,
                            firstInstallment = inst?.number ?: 1,
                            firstInvoice = invoice,
                            categoryId = c.categoryId,
                            fitId = c.fitId,
                            importedMemo = c.rawMemo,
                        )
                        ImportedPurchase(purchase, c.personIds)
                    },
                    ofxId,
                )
            }
            form.update { it.copy(busy = false, step = ImportStep.DONE, importedCount = chosen.size) }
        }
    }

    fun restart() {
        form.value = ImportForm()
    }
}
