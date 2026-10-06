package app.financas.domain.ofx

import app.financas.domain.Billing
import app.financas.domain.TxType
import java.time.LocalDate
import java.time.YearMonth

/** Lançamento já salvo no destino, usado para detectar duplicadas. */
data class ExistingRecord(
    val fitId: String?,
    val date: LocalDate,
    val amount: Long,
    val type: TxType,
    val normalized: String,
    /** Para parcelas de cartão: número da parcela e fatura em que cai. */
    val installmentNumber: Int? = null,
    val invoice: YearMonth? = null,
)

data class PlanOptions(
    val skipDuplicates: Boolean = true,
    val suggestCategories: Boolean = true,
    val detectInstallments: Boolean = true,
)

data class PlanContext(
    val isCard: Boolean,
    val existing: List<ExistingRecord>,
    val history: List<HistoryItem>,
    /** Fatura onde os lançamentos do arquivo serão colocados (apenas cartão). */
    val invoice: YearMonth? = null,
    val transferOutCategoryId: Long? = null,
    val transferInCategoryId: Long? = null,
    val invoicePaymentCategoryId: Long? = null,
)

/** Uma linha do OFX pronta para revisão. */
data class ImportCandidate(
    val id: Int,
    val fitId: String?,
    val date: LocalDate,
    val amount: Long,
    val type: TxType,
    val rawMemo: String,
    val description: String,
    val categoryId: Long?,
    val installment: InstallmentInfo?,
    val duplicate: Boolean,
    /** Motivo para vir desmarcada mesmo não sendo duplicada (ex.: pagamento da fatura). */
    val ignoredReason: String?,
    val selected: Boolean,
    val personIds: Set<Long> = emptySet(),
)

object ImportPlanner {

    /** Fatura sugerida para um OFX de cartão: a da compra mais recente (ignorando pagamentos). */
    fun inferInvoice(statement: OfxStatement, closingDay: Int): YearMonth? {
        val dates = statement.transactions
            .filterNot { it.amount > 0 && ImportHeuristics.isCardPayment(it.memo) }
            .map { it.date }
        val last = dates.maxOrNull() ?: statement.end ?: return null
        return Billing.invoiceFor(last, closingDay)
    }

    fun plan(statement: OfxStatement, context: PlanContext, options: PlanOptions): List<ImportCandidate> {
        val seenFitIds = mutableSetOf<String>()
        return statement.transactions
            .filter { it.amount != 0L }
            .mapIndexed { index, t -> candidate(index, t, context, options, seenFitIds) }
    }

    private fun candidate(
        index: Int,
        t: OfxTransaction,
        ctx: PlanContext,
        options: PlanOptions,
        seenFitIds: MutableSet<String>,
    ): ImportCandidate {
        val type = if (t.amount < 0) TxType.OUT else TxType.IN
        val amount = kotlin.math.abs(t.amount)
        val installment = if (options.detectInstallments) ImportHeuristics.detectInstallment(t.memo, ctx.isCard) else null
        val normalized = ImportHeuristics.normalize(t.memo)
        val suggestion = if (options.suggestCategories) ImportHeuristics.suggest(normalized, ctx.history) else null

        val heuristicCategory = if (!options.suggestCategories) null else when {
            ctx.isCard -> null
            ImportHeuristics.isInvoicePayment(t.memo) && type == TxType.OUT -> ctx.invoicePaymentCategoryId
            ImportHeuristics.isTransfer(t.memo, t.trnType) ->
                if (type == TxType.OUT) ctx.transferOutCategoryId else ctx.transferInCategoryId
            else -> null
        }

        val ignoredReason = if (ctx.isCard && type == TxType.IN && ImportHeuristics.isCardPayment(t.memo)) {
            "Pagamento da fatura"
        } else null

        val repeatedInFile = t.fitId != null && !seenFitIds.add(t.fitId)
        val duplicate = repeatedInFile || ctx.existing.any { matches(it, t.fitId, t.date, amount, type, normalized, installment, ctx) }

        return ImportCandidate(
            id = index,
            fitId = t.fitId,
            date = t.date,
            amount = amount,
            type = type,
            rawMemo = t.memo,
            description = suggestion?.description ?: ImportHeuristics.prettify(t.memo),
            categoryId = suggestion?.categoryId ?: heuristicCategory,
            installment = installment,
            duplicate = duplicate,
            ignoredReason = ignoredReason,
            selected = !(duplicate && options.skipDuplicates) && ignoredReason == null,
        )
    }

    private fun matches(
        e: ExistingRecord,
        fitId: String?,
        date: LocalDate,
        amount: Long,
        type: TxType,
        normalized: String,
        installment: InstallmentInfo?,
        ctx: PlanContext,
    ): Boolean {
        if (fitId != null && e.fitId == fitId) return true
        if (e.type != type || e.amount != amount) return false
        // Parcela já projetada por uma importação/lançamento anterior (ex.: 3/6 criada quando a 1/6 foi salva).
        if (ctx.isCard && installment != null && installment.number > 1) {
            if (e.installmentNumber != installment.number) return false
            if (ctx.invoice == null || e.invoice == null) return e.normalized == normalized
            val distance = kotlin.math.abs(monthsBetween(e.invoice, ctx.invoice))
            // Mesma fatura: basta valor + número da parcela. Fatura vizinha: exige a mesma descrição.
            return distance == 0L || (distance == 1L && e.normalized == normalized)
        }
        // Lançamento manual (sem FITID) com mesma data, valor e direção.
        return e.fitId == null && e.date == date
    }

    private fun monthsBetween(a: YearMonth, b: YearMonth): Long =
        (b.year - a.year) * 12L + (b.monthValue - a.monthValue)
}
