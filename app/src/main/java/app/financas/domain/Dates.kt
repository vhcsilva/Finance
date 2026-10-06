package app.financas.domain

import java.time.LocalDate
import java.time.YearMonth

/** Formatação de datas em português, sem depender do Locale do aparelho. */
object Dates {
    val MONTHS = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    )
    val MONTHS_SHORT = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    val MONTH_INITIALS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    private val WEEKDAYS_SHORT = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    private val WEEKDAYS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

    fun weekdayName(dayOfWeek: Int): String = WEEKDAYS[dayOfWeek - 1]

    /** "Outubro de 2026" */
    fun monthTitle(ym: YearMonth): String =
        MONTHS[ym.monthValue - 1].replaceFirstChar { it.uppercase() } + " de " + ym.year

    /** "out/2026" */
    fun monthShortYear(ym: YearMonth): String = MONTHS_SHORT[ym.monthValue - 1] + "/" + ym.year

    /** "Out 2026" */
    fun monthChip(ym: YearMonth): String =
        MONTHS_SHORT[ym.monthValue - 1].replaceFirstChar { it.uppercase() } + " " + ym.year

    /** "05/10" */
    fun dayMonth(d: LocalDate): String = two(d.dayOfMonth) + "/" + two(d.monthValue)

    /** "05/10/2026" */
    fun full(d: LocalDate): String = dayMonth(d) + "/" + d.year

    /** "Hoje, 06 out", "Ontem, 05 out", "Sáb, 04 out" (com ano quando diferente do atual). */
    fun dayHeading(d: LocalDate, today: LocalDate): String {
        val base = two(d.dayOfMonth) + " " + MONTHS_SHORT[d.monthValue - 1] +
            if (d.year != today.year) " " + d.year else ""
        val prefix = when (d) {
            today -> "Hoje"
            today.minusDays(1) -> "Ontem"
            today.plusDays(1) -> "Amanhã"
            else -> WEEKDAYS_SHORT[d.dayOfWeek.value - 1]
        }
        return "$prefix, $base"
    }

    /** Lê "dd/mm/aaaa". */
    fun parse(text: String): LocalDate? {
        val parts = text.trim().split("/")
        if (parts.size != 3) return null
        return try {
            LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        } catch (e: Exception) {
            null
        }
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')
}
