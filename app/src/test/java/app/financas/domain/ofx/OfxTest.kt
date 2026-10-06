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

class OfxTest {

    private val bankSgml = """
OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

<OFX>
<SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>20261006<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>
<BANKMSGSRSV1>
<STMTTRNRS>
<TRNUID>1
<STMTRS>
<CURDEF>BRL
<BANKACCTFROM>
<BANKID>0341
<ACCTID>12345-6
<ACCTTYPE>CHECKING
</BANKACCTFROM>
<BANKTRANLIST>
<DTSTART>20261001
<DTEND>20261006
<STMTTRN>
<TRNTYPE>DEBIT
<DTPOSTED>20261005120000[-3:BRT]
<TRNAMT>-312,48
<FITID>A001
<MEMO>SUPERMERCADO SÃO JOÃO
<STMTTRN>
<TRNTYPE>CREDIT
<DTPOSTED>20261005
<TRNAMT>6200.00
<FITID>A002
<MEMO>SALARIO EMPRESA X
<STMTTRN>
<TRNTYPE>XFER
<DTPOSTED>20261004
<TRNAMT>-100.00
<FITID>A003
<MEMO>PIX ENVIADO MARIA
<STMTTRN>
<TRNTYPE>DEBIT
<DTPOSTED>20261003
<TRNAMT>-1642.30
<FITID>A004
<MEMO>PAGTO FATURA CARTAO
</BANKTRANLIST>
<LEDGERBAL>
<BALAMT>5120.10
<DTASOF>20261006
</LEDGERBAL>
</STMTRS>
</STMTTRNRS>
</BANKMSGSRSV1>
</OFX>
""".trimIndent()

    private val cardXml = """
<?xml version="1.0" encoding="UTF-8"?>
<?OFX OFXHEADER="200" VERSION="211"?>
<OFX>
  <CREDITCARDMSGSRSV1>
    <CCSTMTTRNRS>
      <CCSTMTRS>
        <CURDEF>BRL</CURDEF>
        <CCACCTFROM><ACCTID>5555444433334321</ACCTID></CCACCTFROM>
        <BANKTRANLIST>
          <DTSTART>20260926</DTSTART>
          <DTEND>20261025</DTEND>
          <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260928</DTPOSTED><TRNAMT>-214.90</TRNAMT><FITID>c1</FITID><MEMO>SUPERMERC BOM PRECO</MEMO></STMTTRN>
          <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260904</DTPOSTED><TRNAMT>-80.00</TRNAMT><FITID>c2</FITID><MEMO>LOJA ESPORTES PARC 02/06</MEMO></STMTTRN>
          <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20261003</DTPOSTED><TRNAMT>-39.90</TRNAMT><FITID>c3</FITID><MEMO>PAG*STREAMING &amp; CIA</MEMO></STMTTRN>
          <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20261001</DTPOSTED><TRNAMT>1500.00</TRNAMT><FITID>c4</FITID><MEMO>PAGAMENTO RECEBIDO</MEMO></STMTTRN>
          <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20261005</DTPOSTED><TRNAMT>59.90</TRNAMT><FITID>c5</FITID><MEMO>ESTORNO COMPRA</MEMO></STMTTRN>
          <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260927</DTPOSTED><TRNAMT>-168.00</TRNAMT><FITID>c6</FITID><MEMO>REST SABOR CASEIRO</MEMO></STMTTRN>
        </BANKTRANLIST>
        <LEDGERBAL><BALAMT>-1642.30</BALAMT><DTASOF>20261025</DTASOF></LEDGERBAL>
      </CCSTMTRS>
    </CCSTMTTRNRS>
  </CREDITCARDMSGSRSV1>
</OFX>
""".trimIndent()

    @Test
    fun parsesBankSgmlInWindows1252() {
        val bytes = bankSgml.toByteArray(Charset.forName("windows-1252"))
        val st = OfxParser.parse(bytes)
        assertFalse(st.isCreditCard)
        assertEquals("0341", st.bankId)
        assertEquals("3456", st.accountSuffix)
        assertEquals(LocalDate.of(2026, 10, 1), st.start)
        assertEquals(LocalDate.of(2026, 10, 6), st.end)
        assertEquals(512010L, st.balance)
        assertEquals(4, st.transactions.size)
        val first = st.transactions[0]
        assertEquals(-31248L, first.amount)
        assertEquals("SUPERMERCADO SÃO JOÃO", first.memo)
        assertEquals(LocalDate.of(2026, 10, 5), first.date)
        assertEquals(620000L, st.transactions[1].amount)
    }

    @Test
    fun parsesCardXml() {
        val st = OfxParser.parse(cardXml.toByteArray(Charsets.UTF_8))
        assertTrue(st.isCreditCard)
        assertEquals("4321", st.accountSuffix)
        assertEquals(6, st.transactions.size)
        assertEquals("PAG*STREAMING & CIA", st.transactions[2].memo)
        assertEquals(YearMonth.of(2026, 10), ImportPlanner.inferInvoice(st, 25))
    }

    @Test
    fun heuristics() {
        assertEquals(InstallmentInfo(2, 6), ImportHeuristics.detectInstallment("LOJA ESPORTES PARC 02/06", false))
        assertEquals(InstallmentInfo(3, 10), ImportHeuristics.detectInstallment("Loja - Parcela 3/10", false))
        assertEquals(InstallmentInfo(1, 3), ImportHeuristics.detectInstallment("MAGAZINE 01/03", true))
        assertNull(ImportHeuristics.detectInstallment("PIX 05/10", false))
        assertNull(ImportHeuristics.detectInstallment("LOJA PARC 07/06", false))
        assertEquals("LOJA ESPORTES", ImportHeuristics.normalize("Loja Esportes PARC 02/06"))
        assertEquals("SUPERMERCADO SAO JOAO", ImportHeuristics.normalize("SUPERMERCADO SÃO JOÃO"))
        assertEquals("Loja Esportes", ImportHeuristics.prettify("LOJA ESPORTES PARC 02/06"))
        assertEquals("Pag Streaming & Cia", ImportHeuristics.prettify("PAG*STREAMING & CIA"))
        assertEquals("Supermercado São João", ImportHeuristics.prettify("SUPERMERCADO SÃO JOÃO"))
        assertTrue(ImportHeuristics.isCardPayment("PAGAMENTO RECEBIDO"))
        assertTrue(ImportHeuristics.isInvoicePayment("PAGTO FATURA CARTAO"))
        assertTrue(ImportHeuristics.isTransfer("PIX ENVIADO MARIA", "DEBIT"))
        assertFalse(ImportHeuristics.isTransfer("SUPERMERCADO", "DEBIT"))
    }

    @Test
    fun planBankStatement() {
        val st = OfxParser.parse(bankSgml)
        val history = listOf(
            HistoryItem("SUPERMERCADO SAO JOAO", "Supermercado", 7L, LocalDate.of(2026, 9, 5)),
            HistoryItem("SALARIO EMPRESA X", "Salário", 8L, LocalDate.of(2026, 9, 5)),
        )
        val existing = listOf(
            ExistingRecord("A002", LocalDate.of(2026, 10, 5), 620000, TxType.IN, "SALARIO EMPRESA X"),
        )
        val ctx = PlanContext(
            isCard = false, existing = existing, history = history,
            transferOutCategoryId = 30, transferInCategoryId = 31, invoicePaymentCategoryId = 40,
        )
        val plan = ImportPlanner.plan(st, ctx, PlanOptions())
        assertEquals(4, plan.size)
        assertEquals("Supermercado", plan[0].description)
        assertEquals(7L, plan[0].categoryId)
        assertTrue(plan[0].selected)
        assertTrue(plan[1].duplicate)
        assertFalse(plan[1].selected)
        assertEquals(30L, plan[2].categoryId)
        assertEquals(TxType.OUT, plan[3].type)
        assertEquals(40L, plan[3].categoryId)

        val keepDup = ImportPlanner.plan(st, ctx, PlanOptions(skipDuplicates = false))
        assertTrue(keepDup[1].duplicate)
        assertTrue(keepDup[1].selected)

        val noSuggest = ImportPlanner.plan(st, ctx, PlanOptions(suggestCategories = false))
        assertNull(noSuggest[0].categoryId)
        assertEquals("Supermercado São João", noSuggest[0].description)
    }

    @Test
    fun planCardStatement() {
        val st = OfxParser.parse(cardXml)
        val oct = YearMonth.of(2026, 10)
        val existing = listOf(
            // Restaurante lançado manualmente, mesma data e valor → duplicada
            ExistingRecord(null, LocalDate.of(2026, 9, 27), 16800, TxType.OUT, "RESTAURANTE", 1, oct),
            // Parcela 2/6 já projetada quando a 1/6 foi importada no mês anterior
            ExistingRecord(null, LocalDate.of(2026, 10, 4), 8000, TxType.OUT, "LOJA ESPORTES", 2, oct),
        )
        val ctx = PlanContext(isCard = true, existing = existing, history = emptyList(), invoice = oct)
        val plan = ImportPlanner.plan(st, ctx, PlanOptions())
        assertEquals(6, plan.size)
        assertFalse(plan[0].duplicate)
        assertEquals(InstallmentInfo(2, 6), plan[1].installment)
        assertTrue(plan[1].duplicate)
        assertEquals("Pagamento da fatura", plan[3].ignoredReason)
        assertFalse(plan[3].selected)
        assertEquals(TxType.IN, plan[4].type)
        assertTrue(plan[4].selected)
        assertTrue(plan[5].duplicate)

        val noDetect = ImportPlanner.plan(st, ctx, PlanOptions(detectInstallments = false))
        assertNull(noDetect[1].installment)
    }

    @Test
    fun repeatedFitIdInSameFileIsDuplicate() {
        val text = bankSgml.replace("<FITID>A002", "<FITID>A001")
        val plan = ImportPlanner.plan(OfxParser.parse(text), PlanContext(false, emptyList(), emptyList()), PlanOptions())
        assertFalse(plan[0].duplicate)
        assertTrue(plan[1].duplicate)
    }
}
