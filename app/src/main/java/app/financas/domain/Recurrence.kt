package app.financas.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * Contas recorrentes são definidas por uma data-âncora (primeira ocorrência) e uma frequência:
 * semanal repete no mesmo dia da semana, mensal no mesmo dia do mês e anual no mesmo dia e mês.
 */
object Recurrence {

    /** Próxima ocorrência em [from] ou depois (nunca antes da âncora). */
    fun next(anchor: LocalDate, frequency: Frequency, from: LocalDate): LocalDate {
        val start = if (from.isBefore(anchor)) anchor else from
        return when (frequency) {
            Frequency.WEEKLY -> {
                val diff = (anchor.dayOfWeek.value - start.dayOfWeek.value + 7) % 7
                start.plusDays(diff.toLong())
            }
            Frequency.MONTHLY -> nextMonthly(anchor, start)
            Frequency.YEARLY -> nextYearly(anchor, start)
        }
    }

    private fun nextMonthly(anchor: LocalDate, start: LocalDate): LocalDate {
        var ym = YearMonth.from(start)
        var d = ym.atDay(min(anchor.dayOfMonth, ym.lengthOfMonth()))
        while (d.isBefore(start)) {
            ym = ym.plusMonths(1)
            d = ym.atDay(min(anchor.dayOfMonth, ym.lengthOfMonth()))
        }
        return d
    }

    private fun nextYearly(anchor: LocalDate, start: LocalDate): LocalDate {
        var year = start.year
        var ym = YearMonth.of(year, anchor.month)
        var d = ym.atDay(min(anchor.dayOfMonth, ym.lengthOfMonth()))
        while (d.isBefore(start)) {
            year++
            ym = YearMonth.of(year, anchor.month)
            d = ym.atDay(min(anchor.dayOfMonth, ym.lengthOfMonth()))
        }
        return d
    }

    /** Todas as ocorrências entre [start] e [endInclusive]. */
    fun between(anchor: LocalDate, frequency: Frequency, start: LocalDate, endInclusive: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = next(anchor, frequency, start)
        while (!d.isAfter(endInclusive)) {
            out += d
            d = next(anchor, frequency, d.plusDays(1))
        }
        return out
    }

    /** Custo mensal equivalente, usado no resumo da tela de recorrentes. */
    fun monthlyEstimate(amount: Long, frequency: Frequency): Long = when (frequency) {
        Frequency.WEEKLY -> amount * 52 / 12
        Frequency.MONTHLY -> amount
        Frequency.YEARLY -> amount / 12
    }

    fun label(frequency: Frequency): String = when (frequency) {
        Frequency.WEEKLY -> "Semanal"
        Frequency.MONTHLY -> "Mensal"
        Frequency.YEARLY -> "Anual"
    }

    /** "Toda quarta", "Todo sábado", "Todo dia 15", "Todo 15 de março". */
    fun describe(anchor: LocalDate, frequency: Frequency): String = when (frequency) {
        Frequency.WEEKLY -> {
            val dow = anchor.dayOfWeek.value
            (if (dow >= 6) "Todo " else "Toda ") + Dates.weekdayName(dow)
        }
        Frequency.MONTHLY -> "Todo dia ${anchor.dayOfMonth}"
        Frequency.YEARLY -> "Todo ${anchor.dayOfMonth} de ${Dates.MONTHS[anchor.monthValue - 1]}"
    }
}
