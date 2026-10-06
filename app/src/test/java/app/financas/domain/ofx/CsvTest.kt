package app.financas.domain.ofx

import app.financas.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.YearMonth

class CsvTest {

    // Mesmo formato da fatura exportada pelo cartão (dados fictícios).
    private val cardCsv = """
Data;Estabelecimento;Portador;Valor;Parcela
01/10/2026;MERCADO CENTRAL;FULANO;R$ 36,59;-
03/09/2026;LOJA ELETRONICOS;FULANO;R$ 189,86;2 de 2
03/10/2026;CALCADOS ABC;FULANO;R$ 31,67;1 de 3
23/12/2025;SEGURO AUTO;FULANO;R$ 452,63;10 de 10
10/09/2026;Pagamentos Validos Normais;FULANO;R$ -15.137,98;-
18/09/2026;ESTACIONAMENTO;FULANO;R$ 25,00;-
18/09/2026;ESTACIONAMENTO;FULANO;R$ 25,00;-
04/10/2026;FARMACIA SAÚDE;FULANO;R$ 1.103,40;-
""".trimIndent()

    @Test
    fun parsesCardCsv() {
        val st = StatementReader.read(cardCsv.toByteArray(Charset.forName("windows-1252")))
        assertTrue(st.isCreditCard)
        assertEquals(8, st.transactions.size)
        val first = st.transactions[0]
        assertEquals(LocalDate.of(2026, 10, 1), first.date)
        assertEquals(-3659L, first.amount)
        assertEquals("MERCADO CENTRAL", first.memo)
        assertEquals("LOJA ELETRONICOS PARCELA 2 de 2", st.transactions[1].memo)
        assertEquals(1513798L, st.transactions[4].amount) // pagamento vira crédito
        assertEquals(-110340L, st.transactions[7].amount)
        assertEquals("FARMACIA SAÚDE", st.transactions[7].memo)
        // Linhas idênticas recebem identificadores diferentes, mas estáveis entre importações.
        assertFalse(st.transactions[5].fitId == st.transactions[6].fitId)
        val again = CsvParser.parse(cardCsv)
        assertEquals(st.transactions.map { it.fitId }, again.transactions.map { it.fitId })
        assertEquals(LocalDate.of(2025, 12, 23), st.start)
    }

    @Test
    fun planCardCsv() {
        val st = CsvParser.parse(cardCsv)
        assertEquals(YearMonth.of(2026, 10), ImportPlanner.inferInvoice(st, 5))
        val plan = ImportPlanner.plan(st, PlanContext(isCard = true, existing = emptyList(), history = emptyList(), invoice = YearMonth.of(2026, 10)), PlanOptions())
        assertEquals(InstallmentInfo(2, 2), plan[1].installment)
        assertEquals(InstallmentInfo(10, 10), plan[3].installment)
        assertEquals("Seguro Auto", plan[3].description)
        assertEquals("Pagamento da fatura", plan[4].ignoredReason)
        assertFalse(plan[4].selected)
        assertEquals(TxType.OUT, plan[0].type)
        assertFalse(plan[5].duplicate)
        assertFalse(plan[6].duplicate)

        // Reimportar o mesmo arquivo: tudo vira duplicada.
        val existing = plan.map { ExistingRecord(it.fitId, it.date, it.amount, it.type, ImportHeuristics.normalize(it.rawMemo)) }
        val again = ImportPlanner.plan(st, PlanContext(true, existing, emptyList(), YearMonth.of(2026, 10)), PlanOptions())
        assertTrue(again.all { it.duplicate })
    }

    @Test
    fun parsesAccountCsvWithCommaAndQuotes() {
        val csv = """
Extrato da conta
"Data","Descrição","Valor"
2026-10-01,"PIX RECEBIDO, JOAO","1500.00"
2026-10-02,"SUPERMERCADO","-312.48"
""".trimIndent()
        val st = StatementReader.read(csv.toByteArray(Charsets.UTF_8))
        assertFalse(st.isCreditCard)
        assertEquals(2, st.transactions.size)
        assertEquals("PIX RECEBIDO, JOAO", st.transactions[0].memo)
        assertEquals(150000L, st.transactions[0].amount)
        assertEquals(-31248L, st.transactions[1].amount)
    }

    @Test
    fun helpers() {
        assertEquals(InstallmentInfo(2, 3), CsvParser.parseInstallment("2 de 3"))
        assertEquals(InstallmentInfo(5, 12), CsvParser.parseInstallment("05/12"))
        assertNull(CsvParser.parseInstallment("-"))
        assertEquals(-1513798L, CsvParser.parseValue("R$ -15.137,98"))
        assertEquals(-1000L, CsvParser.parseValue("10,00 D"))
        assertEquals(LocalDate.of(2026, 1, 5), CsvParser.parseDate("05/01/26"))
        assertTrue(ImportHeuristics.isCardPayment("Pagamentos Validos Normais"))
        assertFalse(ImportHeuristics.isCardPayment("ESTORNO PAGAMENTO"))
    }
}
