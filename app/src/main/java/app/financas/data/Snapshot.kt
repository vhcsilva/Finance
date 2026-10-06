package app.financas.data

import app.financas.domain.Described
import app.financas.domain.Entry
import app.financas.domain.RecurringDef
import app.financas.domain.Money
import app.financas.domain.TxType
import app.financas.domain.ofx.HistoryItem
import app.financas.domain.ofx.ImportHeuristics
import java.time.LocalDate
import java.time.YearMonth

enum class LedgerKind { ACCOUNT, CARD }

/** Item exibido nas listas de lançamentos (uma transação de conta ou uma parcela de cartão). */
data class LedgerItem(
    val key: String,
    val kind: LedgerKind,
    /** Id da transação (conta) ou da compra (cartão), usado para abrir a edição. */
    val refId: Long,
    val date: LocalDate,
    /** Mês de competência: mês da data (conta) ou mês da fatura (cartão). */
    val period: YearMonth,
    val description: String,
    val amount: Long,
    val type: TxType,
    val category: CategoryEntity?,
    val sourceId: Long,
    val sourceName: String,
    val sourceColor: Int,
    val installmentLabel: String?,
    val personIds: Set<Long>,
    val peopleLabel: String?,
) {
    val ignored: Boolean get() = category?.ignoreInReports == true
}

/** Fotografia de todos os dados do app, recalculada a cada mudança no banco. */
data class Snapshot(
    val accounts: List<AccountEntity> = emptyList(),
    val cards: List<CardEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    val transactions: List<AccountTransactionEntity> = emptyList(),
    val purchases: List<CardPurchaseEntity> = emptyList(),
    val installments: List<CardInstallmentEntity> = emptyList(),
    val splits: List<PurchaseSplitEntity> = emptyList(),
    val settlements: List<SettlementEntity> = emptyList(),
) {
    val categoryById: Map<Long, CategoryEntity> by lazy { categories.associateBy { it.id } }
    val accountById: Map<Long, AccountEntity> by lazy { accounts.associateBy { it.id } }
    val cardById: Map<Long, CardEntity> by lazy { cards.associateBy { it.id } }
    val personById: Map<Long, PersonEntity> by lazy { people.associateBy { it.id } }
    val purchaseById: Map<Long, CardPurchaseEntity> by lazy { purchases.associateBy { it.id } }
    val splitsByPurchase: Map<Long, List<PurchaseSplitEntity>> by lazy { splits.groupBy { it.purchaseId } }
    val me: PersonEntity? get() = people.firstOrNull { it.isMe }

    fun isIgnored(categoryId: Long?): Boolean = categoryId?.let { categoryById[it]?.ignoreInReports } == true

    fun accountBalance(accountId: Long): Long {
        val acc = accountById[accountId] ?: return 0
        return acc.initialBalance + transactions.filter { it.accountId == accountId }
            .sumOf { if (it.type == TxType.IN) it.amount else -it.amount }
    }

    val totalBalance: Long get() = accounts.sumOf { accountBalance(it.id) }

    /** Data "de referência" da parcela: a data original da compra avançada um mês por parcela. */
    fun installmentDate(p: CardPurchaseEntity, number: Int): LocalDate =
        p.date.plusMonths((number - 1).toLong())

    /** Divide o valor de uma parcela entre as pessoas da compra (ou tudo para "Eu"). */
    fun sharesFor(purchaseId: Long, amount: Long): Map<Long, Long> {
        val s = splitsByPurchase[purchaseId].orEmpty().filter { it.share > 0 }
        if (s.isEmpty()) return me?.let { mapOf(it.id to amount) } ?: emptyMap()
        val parts = Money.proportional(amount, s.map { it.share })
        return s.map { it.personId }.zip(parts).toMap()
    }

    /** Total da fatura de um cartão em um mês (compras menos créditos). */
    fun invoiceTotal(cardId: Long, invoice: YearMonth): Long =
        installments.filter { it.cardId == cardId && it.invoice == invoice }.sumOf { inst ->
            val p = purchaseById[inst.purchaseId]
            if (p?.type == TxType.IN) -inst.amount else inst.amount
        }

    /** Lançamentos para relatórios (sem categorias marcadas como "ignorar"), com a descrição normalizada. */
    val described: List<Described> by lazy {
        val fromAccounts = transactions.filterNot { isIgnored(it.categoryId) }.map {
            Described(
                Entry(it.date, YearMonth.from(it.date), it.amount, it.type, it.categoryId, accountId = it.accountId),
                ImportHeuristics.normalize(it.description),
            )
        }
        val fromCards = installments.mapNotNull { inst ->
            val p = purchaseById[inst.purchaseId] ?: return@mapNotNull null
            if (isIgnored(p.categoryId)) return@mapNotNull null
            Described(
                Entry(
                    date = installmentDate(p, inst.number),
                    period = inst.invoice,
                    amount = inst.amount,
                    type = p.type,
                    categoryId = p.categoryId,
                    cardId = inst.cardId,
                    shares = sharesFor(p.id, inst.amount),
                ),
                ImportHeuristics.normalize(p.description),
            )
        }
        fromAccounts + fromCards
    }

    val entries: List<Entry> by lazy { described.map { it.entry } }

    val recurringDefs: List<RecurringDef> by lazy {
        recurring.map { RecurringDef(it.id, it.name, ImportHeuristics.normalize(it.name), it.amount, it.frequency, it.anchorDate) }
    }

    val ledger: List<LedgerItem> by lazy {
        val fromAccounts = transactions.map { t ->
            val acc = accountById[t.accountId]
            LedgerItem(
                key = "a${t.id}", kind = LedgerKind.ACCOUNT, refId = t.id, date = t.date,
                period = YearMonth.from(t.date), description = t.description, amount = t.amount, type = t.type,
                category = t.categoryId?.let(categoryById::get), sourceId = t.accountId,
                sourceName = acc?.name ?: "Conta", sourceColor = acc?.color ?: 0xFF5B6661.toInt(),
                installmentLabel = null, personIds = setOfNotNull(me?.id), peopleLabel = null,
            )
        }
        val fromCards = installments.mapNotNull { inst ->
            val p = purchaseById[inst.purchaseId] ?: return@mapNotNull null
            val card = cardById[inst.cardId]
            val personIds = splitsByPurchase[p.id].orEmpty().filter { it.share > 0 }.map { it.personId }
                .ifEmpty { listOfNotNull(me?.id) }
            LedgerItem(
                key = "c${inst.id}", kind = LedgerKind.CARD, refId = p.id, date = installmentDate(p, inst.number),
                period = inst.invoice, description = p.description, amount = inst.amount, type = p.type,
                category = p.categoryId?.let(categoryById::get), sourceId = inst.cardId,
                sourceName = card?.name ?: "Cartão", sourceColor = card?.color ?: 0xFF1F2937.toInt(),
                installmentLabel = if (p.installments > 1) "${inst.number}/${p.installments}" else null,
                personIds = personIds.toSet(),
                peopleLabel = personIds.mapNotNull { personById[it]?.name }.joinToString(" + ").ifEmpty { null },
            )
        }
        (fromAccounts + fromCards).sortedWith(compareByDescending<LedgerItem> { it.date }.thenByDescending { it.key })
    }

    /** Histórico usado pelo importador para sugerir descrição e categoria. */
    val history: List<HistoryItem> by lazy {
        val items = mutableListOf<HistoryItem>()
        transactions.forEach { t ->
            items += HistoryItem(ImportHeuristics.normalize(t.description), t.description, t.categoryId, t.date)
            t.importedMemo?.let { items += HistoryItem(ImportHeuristics.normalize(it), t.description, t.categoryId, t.date) }
        }
        purchases.forEach { p ->
            items += HistoryItem(ImportHeuristics.normalize(p.description), p.description, p.categoryId, p.date)
            p.importedMemo?.let { items += HistoryItem(ImportHeuristics.normalize(it), p.description, p.categoryId, p.date) }
        }
        items.filter { it.normalized.isNotBlank() }
    }

    fun isSettled(personId: Long, invoice: YearMonth): Boolean =
        settlements.any { it.personId == personId && it.invoice == invoice }
}
