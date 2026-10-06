package app.financas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DomainTest {

    @Test
    fun moneyFormat() {
        assertEquals("R$ 1.234,56", Money.format(123456))
        assertEquals("R$ 0,05", Money.format(5))
        assertEquals("− R$ 312,48", Money.format(-31248))
        assertEquals("+ R$ 6.200,00", Money.signed(620000, TxType.IN))
        assertEquals("− R$ 80,00", Money.signed(8000, TxType.OUT))
        assertEquals("R$ 62.400", Money.compact(6239990))
        assertEquals("1.000.000,00", Money.plain(100000000))
    }

    @Test
    fun moneyParse() {
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(123450L, Money.parse("1234,5"))
        assertEquals(123456L, Money.parse("1234.56"))
        assertEquals(123456L, Money.parse("1,234.56"))
        assertEquals(123400L, Money.parse("1.234"))
        assertEquals(1200L, Money.parse("R$ 12"))
        assertEquals(-3000L, Money.parse("-30,00"))
        assertEquals(50L, Money.parse(",5"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1,2,3"))
    }

    @Test
    fun moneySplits() {
        assertEquals(listOf(34L, 33L, 33L), Money.splitEven(100, 3))
        assertEquals(listOf(8000L, 8000L, 8000L, 8000L, 8000L, 8000L), Money.splitEven(48000, 6))
        val p = Money.proportional(1000, listOf(1, 1, 1))
        assertEquals(1000L, p.sum())
        assertEquals(listOf(500L, 250L, 250L), Money.proportional(1000, listOf(240, 120, 120)))
        assertEquals(listOf(50L, 50L), Money.proportional(100, listOf(0, 0)))
    }

    @Test
    fun billingInvoiceAndDue() {
        // Fecha dia 25, vence dia 5 (mês seguinte)
        assertEquals(YearMonth.of(2026, 10), Billing.invoiceFor(LocalDate.of(2026, 10, 24), 25))
        assertEquals(YearMonth.of(2026, 11), Billing.invoiceFor(LocalDate.of(2026, 10, 25), 25))
        assertEquals(LocalDate.of(2026, 11, 5), Billing.dueDate(YearMonth.of(2026, 10), 25, 5))
        // Fecha dia 20, vence dia 28 (mesmo mês)
        assertEquals(LocalDate.of(2026, 10, 28), Billing.dueDate(YearMonth.of(2026, 10), 20, 28))
        // Fechamento no dia 31 em fevereiro
        assertEquals(LocalDate.of(2026, 2, 28), Billing.closingDate(YearMonth.of(2026, 2), 31))
        assertEquals(YearMonth.of(2026, 3), Billing.invoiceFor(LocalDate.of(2026, 2, 28), 31))
        // Virada de ano
        assertEquals(YearMonth.of(2027, 1), Billing.invoiceFor(LocalDate.of(2026, 12, 30), 10))
    }

    @Test
    fun billingInstallments() {
        val p = Billing.installments(10000, 3, YearMonth.of(2026, 11))
        assertEquals(listOf(3334L, 3333L, 3333L), p.map { it.amount })
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)), p.map { it.invoice })
        val mid = Billing.installments(48000, 6, YearMonth.of(2026, 10), firstNumber = 2)
        assertEquals(listOf(2, 3, 4, 5, 6), mid.map { it.number })
        assertEquals(YearMonth.of(2026, 10), mid.first().invoice)
        assertEquals(YearMonth.of(2027, 2), mid.last().invoice)
    }

    @Test
    fun recurrence() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.next(anchor, Frequency.MONTHLY, LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.next(anchor, Frequency.MONTHLY, LocalDate.of(2026, 3, 1)))
        assertEquals(anchor, Recurrence.next(anchor, Frequency.MONTHLY, LocalDate.of(2025, 5, 1)))
        // Quarta-feira
        val wed = LocalDate.of(2026, 10, 7)
        assertEquals(wed, Recurrence.next(wed, Frequency.WEEKLY, LocalDate.of(2026, 10, 6)))
        assertEquals(LocalDate.of(2026, 10, 14), Recurrence.next(wed, Frequency.WEEKLY, LocalDate.of(2026, 10, 8)))
        val ipva = LocalDate.of(2026, 3, 15)
        assertEquals(LocalDate.of(2027, 3, 15), Recurrence.next(ipva, Frequency.YEARLY, LocalDate.of(2026, 10, 6)))
        assertEquals(5, Recurrence.between(wed, Frequency.WEEKLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 4)).size)
        assertEquals("Toda quarta", Recurrence.describe(wed, Frequency.WEEKLY))
        assertEquals("Todo sábado", Recurrence.describe(LocalDate.of(2026, 10, 10), Frequency.WEEKLY))
        assertEquals("Todo dia 15", Recurrence.describe(ipva, Frequency.MONTHLY))
        assertEquals("Todo 15 de março", Recurrence.describe(ipva, Frequency.YEARLY))
        assertEquals(65000L, Recurrence.monthlyEstimate(15000, Frequency.WEEKLY))
    }

    @Test
    fun dates() {
        val today = LocalDate.of(2026, 10, 6)
        assertEquals("Hoje, 06 out", Dates.dayHeading(today, today))
        assertEquals("Ontem, 05 out", Dates.dayHeading(today.minusDays(1), today))
        assertEquals("Sáb, 03 out", Dates.dayHeading(LocalDate.of(2026, 10, 3), today))
        assertEquals("Outubro de 2026", Dates.monthTitle(YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 10, 5), Dates.parse("05/10/2026"))
        assertNull(Dates.parse("31/02/2026"))
    }

    @Test
    fun reports() {
        val oct = YearMonth.of(2026, 10)
        val nov = YearMonth.of(2026, 11)
        val d = LocalDate.of(2026, 10, 5)
        val entries = listOf(
            Entry(d, oct, 620000, TxType.IN, 1, accountId = 10),
            Entry(d, oct, 31248, TxType.OUT, 2, accountId = 10),
            Entry(d, oct, 16800, TxType.OUT, 3, cardId = 20, shares = mapOf(100L to 8400L, 101L to 8400L)),
            Entry(d, nov, 8000, TxType.OUT, 3, cardId = 20, shares = mapOf(100L to 8000L)),
            Entry(d, oct, 5990, TxType.IN, 3, cardId = 20, shares = mapOf(100L to 5990L)),
        )
        val octOnly = Reports.filter(entries, 2026, 10)
        assertEquals(4, octOnly.size)
        val t = Reports.totals(octOnly)
        assertEquals(625990L, t.income)
        assertEquals(48048L, t.expense)
        val months = Reports.byMonth(entries, 2026)
        assertEquals(12, months.size)
        assertEquals(8000L, months[10].expense)
        val byCat = Reports.group(octOnly, Grouping.CATEGORY)
        assertEquals(2L, byCat.first().key)
        val byCard = Reports.group(octOnly, Grouping.CARD)
        assertEquals(1, byCard.size)
        assertEquals(16800L, byCard[0].expense)
        val byPerson = Reports.group(octOnly, Grouping.PERSON)
        assertEquals(101L, byPerson.first().key) // 8400 contra 8400 - 5990
        assertEquals(50, Reports.percent(1, 2))
        assertEquals(33, Reports.percent(1, 3))
    }
}
