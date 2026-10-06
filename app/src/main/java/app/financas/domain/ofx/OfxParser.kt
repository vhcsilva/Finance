package app.financas.domain.ofx

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate

class OfxException(message: String) : Exception(message)

data class OfxTransaction(
    val fitId: String?,
    val date: LocalDate,
    /** Valor com sinal, em centavos: negativo = débito/compra, positivo = crédito. */
    val amount: Long,
    val trnType: String?,
    val memo: String,
    val name: String?,
)

data class OfxStatement(
    val isCreditCard: Boolean,
    val bankId: String?,
    val organization: String?,
    val accountId: String?,
    val currency: String?,
    val start: LocalDate?,
    val end: LocalDate?,
    val balance: Long?,
    val transactions: List<OfxTransaction>,
) {
    /** Últimos 4 dígitos da conta/cartão, quando disponíveis. */
    val accountSuffix: String?
        get() = accountId?.filter { it.isLetterOrDigit() }?.takeLast(4)?.ifEmpty { null }
}

/**
 * Leitor tolerante de OFX. Funciona tanto com OFX 1.x (SGML, sem tags de fechamento)
 * quanto com OFX 2.x (XML), que é o que os bancos brasileiros costumam exportar.
 */
object OfxParser {

    private val TRANSACTION = Regex(
        "<STMTTRN>(.*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|\\z)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(bytes: ByteArray): OfxStatement = parse(decode(bytes))

    fun parse(text: String): OfxStatement {
        val start = indexOfIgnoreCase(text, "<OFX>")
        if (start < 0) throw OfxException("O arquivo não parece ser um OFX válido.")
        val body = text.substring(start)

        val isCard = Regex("<CCSTMTRS>|<CREDITCARDMSGSRSV1>|<CCACCTFROM>", RegexOption.IGNORE_CASE)
            .containsMatchIn(body)

        val tranListStart = indexOfIgnoreCase(body, "<BANKTRANLIST>")
        val listHeader = if (tranListStart >= 0) {
            val firstTrn = indexOfIgnoreCase(body, "<STMTTRN>", tranListStart)
            body.substring(tranListStart, if (firstTrn > 0) firstTrn else body.length)
        } else ""

        val ledgerIdx = indexOfIgnoreCase(body, "<LEDGERBAL>")
        val balance = if (ledgerIdx >= 0) tag(body.substring(ledgerIdx), "BALAMT")?.let(::parseAmount) else null

        val transactions = TRANSACTION.findAll(body).mapNotNull { m ->
            val block = m.groupValues[1]
            val date = tag(block, "DTPOSTED")?.let(::parseDate) ?: tag(block, "DTUSER")?.let(::parseDate)
            val amount = tag(block, "TRNAMT")?.let(::parseAmount)
            if (date == null || amount == null) return@mapNotNull null
            val memo = tag(block, "MEMO")
            val name = tag(block, "NAME")
            val label = when {
                memo != null && name != null && !memo.contains(name, ignoreCase = true) &&
                    !name.contains(memo, ignoreCase = true) -> "$name $memo"
                memo != null && name != null -> if (memo.length >= name.length) memo else name
                else -> memo ?: name ?: ""
            }
            OfxTransaction(
                fitId = tag(block, "FITID"),
                date = date,
                amount = amount,
                trnType = tag(block, "TRNTYPE")?.uppercase(),
                memo = label.replace(Regex("\\s+"), " ").trim(),
                name = name,
            )
        }.toList()

        return OfxStatement(
            isCreditCard = isCard,
            bankId = tag(body, "BANKID"),
            organization = tag(body, "ORG"),
            accountId = tag(body, "ACCTID"),
            currency = tag(body, "CURDEF"),
            start = tag(listHeader, "DTSTART")?.let(::parseDate),
            end = tag(listHeader, "DTEND")?.let(::parseDate),
            balance = balance,
            transactions = transactions,
        )
    }

    /** Decodifica respeitando UTF-8 quando válido; senão usa Windows-1252 (padrão dos bancos brasileiros). */
    fun decode(bytes: ByteArray): String {
        var data = bytes
        if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) {
            data = data.copyOfRange(3, data.size)
        }
        try {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data))
                .toString()
        } catch (e: CharacterCodingException) {
            // continua abaixo
        }
        val head = String(data, 0, minOf(data.size, 800), Charsets.ISO_8859_1).uppercase()
        val charset = when {
            head.contains("CHARSET:8859") || head.contains("ISO-8859-1") -> Charsets.ISO_8859_1
            else -> runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)
        }
        return String(data, charset)
    }

    internal fun tag(block: String, name: String): String? {
        val m = Regex("<$name>\\s*([^<\\r\\n]*)", RegexOption.IGNORE_CASE).find(block) ?: return null
        return unescape(m.groupValues[1].trim()).ifEmpty { null }
    }

    /** "20261005", "20261005120000[-3:BRT]" → 2026-10-05 */
    internal fun parseDate(value: String): LocalDate? {
        val digits = value.takeWhile { it.isDigit() }
        if (digits.length < 8) return null
        return try {
            LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt())
        } catch (e: Exception) {
            null
        }
    }

    /** "-150.00", "-150,00", "+1234.5" → centavos com sinal. */
    internal fun parseAmount(value: String): Long? {
        var s = value.replace(" ", "").replace("+", "").replace(',', '.')
        val lastDot = s.lastIndexOf('.')
        if (lastDot >= 0) s = s.substring(0, lastDot).replace(".", "") + s.substring(lastDot)
        return try {
            BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    private fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&nbsp;", " ").replace("&amp;", "&")

    private fun indexOfIgnoreCase(text: String, needle: String, from: Int = 0): Int =
        text.indexOf(needle, from, ignoreCase = true)
}
