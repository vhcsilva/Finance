package app.financas.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Um lançamento normalizado para relatórios.
 * - Transações de conta: [period] é o mês da data.
 * - Parcelas de cartão: [period] é o mês da fatura; [shares] diz quanto cabe a cada pessoa.
 */
data class Entry(
    val date: LocalDate,
    val period: YearMonth,
    val amount: Long,
    val type: TxType,
    val categoryId: Long?,
    val accountId: Long? = null,
    val cardId: Long? = null,
    val shares: Map<Long, Long> = emptyMap(),
)

enum class Grouping(val label: String) {
    MONTH("Mês"), CATEGORY("Categoria"), ACCOUNT("Conta"), CARD("Cartão"), PERSON("Pessoa")
}

/** Linha agregada. [key] é o id do grupo (ou o número do mês para [Grouping.MONTH]); null = "sem". */
data class GroupRow(val key: Long?, val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

data class Totals(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

object Reports {

    /** Mantém apenas o ano (e opcionalmente o mês) pedido. */
    fun filter(entries: List<Entry>, year: Int, month: Int? = null): List<Entry> =
        entries.filter { it.period.year == year && (month == null || it.period.monthValue == month) }

    fun totals(entries: List<Entry>): Totals = Totals(
        income = entries.filter { it.type == TxType.IN }.sumOf { it.amount },
        expense = entries.filter { it.type == TxType.OUT }.sumOf { it.amount },
    )

    /** Doze linhas (janeiro a dezembro) do ano informado. */
    fun byMonth(entries: List<Entry>, year: Int): List<GroupRow> {
        val inYear = filter(entries, year)
        return (1..12).map { m ->
            val t = totals(inYear.filter { it.period.monthValue == m })
            GroupRow(m.toLong(), t.income, t.expense)
        }
    }

    /** Agrupa e ordena pela maior saída (depois maior entrada). */
    fun group(entries: List<Entry>, grouping: Grouping): List<GroupRow> {
        if (grouping == Grouping.PERSON) return byPerson(entries)
        val keyOf: (Entry) -> Long? = when (grouping) {
            Grouping.MONTH -> { e -> e.period.monthValue.toLong() }
            Grouping.CATEGORY -> { e -> e.categoryId }
            Grouping.ACCOUNT -> { e -> e.accountId }
            Grouping.CARD -> { e -> e.cardId }
            Grouping.PERSON -> { _ -> null }
        }
        val relevant = when (grouping) {
            Grouping.ACCOUNT -> entries.filter { it.accountId != null }
            Grouping.CARD -> entries.filter { it.cardId != null }
            else -> entries
        }
        val rows = relevant.groupBy(keyOf).map { (k, list) ->
            val t = totals(list)
            GroupRow(k, t.income, t.expense)
        }
        return if (grouping == Grouping.MONTH) rows.sortedBy { it.key }
        else rows.sortedWith(compareByDescending<GroupRow> { it.expense }.thenByDescending { it.income })
    }

    /** Quanto cabe a cada pessoa nas compras de cartão (créditos/estornos reduzem o valor). */
    fun byPerson(entries: List<Entry>): List<GroupRow> {
        val income = mutableMapOf<Long, Long>()
        val expense = mutableMapOf<Long, Long>()
        entries.filter { it.cardId != null }.forEach { e ->
            val target = if (e.type == TxType.IN) income else expense
            e.shares.forEach { (person, cents) -> target[person] = (target[person] ?: 0L) + cents }
        }
        return (income.keys + expense.keys).map { p ->
            GroupRow(p, income[p] ?: 0L, expense[p] ?: 0L)
        }.sortedByDescending { it.expense - it.income }
    }

    /** Percentual (0..100) de [part] em [whole], arredondado. */
    fun percent(part: Long, whole: Long): Int =
        if (whole <= 0L) 0 else ((part * 1000 / whole + 5) / 10).toInt()
}
