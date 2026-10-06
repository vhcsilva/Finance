package app.financas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AnnualSummaryTest {
    private val oct = YearMonth.of(2026, 10)
    private val me = 1L
    private val ana = 2L

    private fun acc(day: Int, amount: Long, type: TxType, name: String, cat: Long? = null) =
        Described(Entry(LocalDate.of(2026, 10, day), oct, amount, type, cat, accountId = 10), name)

    private fun card(amount: Long, type: TxType, name: String, shares: Map<Long, Long>, cardId: Long = 20) =
        Described(Entry(LocalDate.of(2026, 9, 28), oct, amount, type, 5, cardId = cardId, shares = shares), name)

    private val recurring = listOf(
        RecurringDef(1, "Aluguel", "ALUGUEL", 180000, Frequency.MONTHLY, LocalDate.of(2026, 1, 10)),
        RecurringDef(2, "Diarista", "DIARISTA", 15000, Frequency.WEEKLY, LocalDate.of(2026, 1, 7)),
        RecurringDef(3, "Academia", "ACADEMIA", 11990, Frequency.MONTHLY, LocalDate.of(2026, 1, 12)),
        RecurringDef(4, "IPVA", "IPVA", 138000, Frequency.YEARLY, LocalDate.of(2026, 3, 15)),
    )

    @Test
    fun monthSummary() {
        val items = listOf(
            acc(5, 620000, TxType.IN, "SALARIO", 100),
            acc(10, 180000, TxType.OUT, "ALUGUEL"),
            acc(11, 31248, TxType.OUT, "SUPERMERCADO"),
            card(16800, TxType.OUT, "RESTAURANTE", mapOf(me to 8400L, ana to 8400L)),
            card(11990, TxType.OUT, "ACADEMIA SMART", mapOf(me to 11990L)),
            card(5990, TxType.IN, "ESTORNO", mapOf(me to 5990L)),
            card(10000, TxType.OUT, "LOJA", mapOf(me to 10000L), cardId = 21),
        )
        val m = AnnualSummary.month(oct, items, recurring, me)
        assertEquals(620000L, m.income)
        assertEquals(2, m.cards.size)
        val c20 = m.cards.first { it.cardId == 20L }
        assertEquals(22800L, c20.total) // 16800 + 11990 - 5990
        assertEquals(listOf(me, ana), c20.people.map { it.personId })
        assertEquals(14400L, c20.people[0].amount) // 8400 + 11990 - 5990
        assertEquals(32800L, m.cardsTotal)

        val byName = m.recurring.associateBy { it.name }
        assertEquals(RecurringSource.LAUNCHED, byName["Aluguel"]!!.source)
        assertEquals(RecurringSource.PROJECTED, byName["Diarista"]!!.source)
        assertEquals(4, byName["Diarista"]!!.occurrences) // quartas de outubro/2026: 7, 14, 21, 28
        assertEquals(60000L, byName["Diarista"]!!.amount)
        assertEquals(RecurringSource.ON_CARD, byName["Academia"]!!.source)
        assertTrue("IPVA" !in byName)
        assertEquals(240000L, m.recurringTotal) // aluguel + diarista; academia já está no cartão

        assertEquals(31248L, m.otherExpense) // aluguel não conta de novo
        assertEquals(8400L, m.receivable)
        assertEquals(620000L - (32800L + 240000L + 31248L), m.net)
    }

    @Test
    fun yearHasTwelveMonthsAndAnnualRecurrence() {
        val year = AnnualSummary.build(2026, emptyList(), recurring, me)
        assertEquals(12, year.size)
        assertTrue(year[2].recurring.any { it.name == "IPVA" })
        assertTrue(year[3].recurring.none { it.name == "IPVA" })
        assertTrue(year[0].recurring.none { it.name == "Aluguel" && it.amount == 0L })
    }

    @Test
    fun nameMatching() {
        assertTrue(AnnualSummary.sameName("ACADEMIA SMART FIT", "ACADEMIA"))
        assertTrue(AnnualSummary.sameName("PAG ACADEMIA", "ACADEMIA"))
        assertTrue(!AnnualSummary.sameName("PAG IPVAX", "IPVA"))
        assertTrue(!AnnualSummary.sameName("LUZ", ""))
    }
}
