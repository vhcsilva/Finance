package app.financas.domain

import java.time.LocalDate
import java.time.YearMonth

/** Lançamento de relatório acompanhado da descrição normalizada (para casar com as recorrentes). */
data class Described(val entry: Entry, val normalized: String)

data class RecurringDef(
    val id: Long,
    val name: String,
    val normalized: String,
    val amount: Long,
    val frequency: Frequency,
    val anchor: LocalDate,
)

data class PersonAmount(val personId: Long, val amount: Long)

data class CardMonth(val cardId: Long, val total: Long, val people: List<PersonAmount>)

data class IncomeLine(val categoryId: Long?, val amount: Long)

enum class RecurringSource {
    /** Lançada na conta: vale o valor efetivamente lançado. */
    LAUNCHED,
    /** Ainda não lançada: vale o valor cadastrado × ocorrências no mês. */
    PROJECTED,
    /** Paga no cartão: aparece para referência, mas já está somada na fatura. */
    ON_CARD,
}

data class RecurringLine(
    val recurringId: Long,
    val name: String,
    val amount: Long,
    val occurrences: Int,
    val unitAmount: Long,
    val source: RecurringSource,
    val cardId: Long? = null,
)

data class MonthSummary(
    val month: YearMonth,
    val income: Long,
    val incomes: List<IncomeLine>,
    val cards: List<CardMonth>,
    val cardsTotal: Long,
    val recurring: List<RecurringLine>,
    val recurringTotal: Long,
    val otherExpense: Long,
    val receivable: Long,
) {
    val expense: Long get() = cardsTotal + recurringTotal + otherExpense
    val net: Long get() = income - expense
}

/**
 * Resumo mês a mês de um ano: entradas, cartões (com a parte de cada pessoa),
 * recorrentes (com nome e valor), outras saídas da conta e líquido.
 *
 * Para não contar duas vezes, uma saída da conta com o mesmo nome de uma recorrente
 * conta como a recorrente; se a recorrente foi paga no cartão, ela já está na fatura.
 */
object AnnualSummary {

    fun build(year: Int, items: List<Described>, recurring: List<RecurringDef>, meId: Long?): List<MonthSummary> =
        (1..12).map { month(YearMonth.of(year, it), items, recurring, meId) }

    fun month(ym: YearMonth, items: List<Described>, recurring: List<RecurringDef>, meId: Long?): MonthSummary {
        val inMonth = items.filter { it.entry.period == ym }
        val accountItems = inMonth.filter { it.entry.accountId != null }
        val cardItems = inMonth.filter { it.entry.cardId != null }

        // Entradas da conta, agrupadas por categoria.
        val incomeItems = accountItems.filter { it.entry.type == TxType.IN }
        val incomes = incomeItems.groupBy { it.entry.categoryId }
            .map { (cat, list) -> IncomeLine(cat, list.sumOf { it.entry.amount }) }
            .sortedByDescending { it.amount }

        // Cartões: total da fatura e parte de cada pessoa (créditos reduzem).
        val cards = cardItems.groupBy { it.entry.cardId!! }.map { (cardId, list) ->
            val people = mutableMapOf<Long, Long>()
            var total = 0L
            list.forEach { d ->
                val sign = if (d.entry.type == TxType.IN) -1 else 1
                total += sign * d.entry.amount
                d.entry.shares.forEach { (p, v) -> people[p] = (people[p] ?: 0L) + sign * v }
            }
            CardMonth(
                cardId, total,
                people.map { PersonAmount(it.key, it.value) }
                    .sortedWith(compareByDescending<PersonAmount> { it.personId == meId }.thenByDescending { it.amount }),
            )
        }.sortedByDescending { it.total }

        // Recorrentes.
        val accountOut = accountItems.filter { it.entry.type == TxType.OUT }
        val consumed = mutableSetOf<Int>()
        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        val lines = recurring.mapNotNull { r ->
            val occurrences = Recurrence.between(r.anchor, r.frequency, start, end).size
            val matches = accountOut.indices.filter { it !in consumed && sameName(accountOut[it].normalized, r.normalized) }
            if (matches.isNotEmpty()) {
                consumed += matches
                val amount = matches.sumOf { accountOut[it].entry.amount }
                return@mapNotNull RecurringLine(r.id, r.name, amount, maxOf(occurrences, matches.size), r.amount, RecurringSource.LAUNCHED)
            }
            val onCard = cardItems.filter { it.entry.type == TxType.OUT && sameName(it.normalized, r.normalized) }
            if (onCard.isNotEmpty()) {
                return@mapNotNull RecurringLine(
                    r.id, r.name, onCard.sumOf { it.entry.amount }, maxOf(occurrences, onCard.size), r.amount,
                    RecurringSource.ON_CARD, onCard.first().entry.cardId,
                )
            }
            if (occurrences == 0) return@mapNotNull null
            RecurringLine(r.id, r.name, r.amount * occurrences, occurrences, r.amount, RecurringSource.PROJECTED)
        }.sortedByDescending { it.amount }

        val other = accountOut.indices.filter { it !in consumed }.sumOf { accountOut[it].entry.amount }
        val receivable = cards.sumOf { c -> c.people.filter { it.personId != meId && it.amount > 0 }.sumOf { it.amount } }

        return MonthSummary(
            month = ym,
            income = incomes.sumOf { it.amount },
            incomes = incomes,
            cards = cards,
            cardsTotal = cards.sumOf { it.total },
            recurring = lines,
            recurringTotal = lines.filter { it.source != RecurringSource.ON_CARD }.sumOf { it.amount },
            otherExpense = other,
            receivable = receivable,
        )
    }

    /** "ACADEMIA" casa com "ACADEMIA", "ACADEMIA SMART FIT" e "PAG ACADEMIA". */
    fun sameName(description: String, recurringName: String): Boolean {
        if (recurringName.isBlank() || description.isBlank()) return false
        if (description == recurringName || description.startsWith("$recurringName ")) return true
        return recurringName.length >= 5 && " $description ".contains(" $recurringName ")
    }
}
