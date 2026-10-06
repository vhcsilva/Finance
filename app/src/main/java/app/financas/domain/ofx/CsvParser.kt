package app.financas.domain.ofx

import app.financas.domain.Money
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate

/**
 * Leitor de extratos/faturas em CSV, convertido para o mesmo [OfxStatement] usado pelo importador.
 *
 * Reconhece as colunas pelo cabeçalho (ex.: "Data;Estabelecimento;Portador;Valor;Parcela"),
 * aceita separador ";", "," ou tab, valores como "R$ 1.234,56" e parcelas como "2 de 3" ou "2/3".
 */
object CsvParser {

    private val DATE_KEYS = listOf("DATA", "DATE", "DT ")
    private val DESC_KEYS = listOf("ESTABELECIMENTO", "DESCRICAO", "HISTORICO", "LANCAMENTO", "DESCRIPTION", "TITLE", "MEMO", "TITULO", "LOJA")
    private val AMOUNT_KEYS = listOf("VALOR", "AMOUNT", "QUANTIA", "MONTANTE")
    private val INSTALLMENT_KEYS = listOf("PARCELA", "INSTALLMENT")
    private val HOLDER_KEYS = listOf("PORTADOR", "TITULAR", "NOME NO CARTAO")

    private data class Columns(val date: Int, val desc: Int, val amount: Int, val installment: Int?, val holder: Int?)

    fun parse(bytes: ByteArray): OfxStatement = parse(OfxParser.decode(bytes))

    fun parse(text: String): OfxStatement {
        val lines = text.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) throw OfxException("O arquivo CSV está vazio.")
        val delimiter = detectDelimiter(lines.take(5))

        // Procura a linha de cabeçalho nas primeiras linhas (alguns bancos colocam um título antes).
        var headerIndex = -1
        var columns: Columns? = null
        for ((i, line) in lines.take(10).withIndex()) {
            columns = mapColumns(split(line, delimiter))
            if (columns != null) {
                headerIndex = i
                break
            }
        }
        val cols = columns ?: throw OfxException(
            "Não reconheci as colunas do CSV. O arquivo precisa ter colunas de data, descrição e valor."
        )

        data class Row(val date: LocalDate, val desc: String, val value: Long, val installment: InstallmentInfo?)

        val rows = lines.drop(headerIndex + 1).mapNotNull { line ->
            val f = split(line, delimiter)
            val date = f.getOrNull(cols.date)?.let(::parseDate) ?: return@mapNotNull null
            val value = f.getOrNull(cols.amount)?.let(::parseValue) ?: return@mapNotNull null
            val desc = f.getOrNull(cols.desc)?.trim().orEmpty().replace(Regex("\\s+"), " ")
            val inst = cols.installment?.let { f.getOrNull(it) }?.let(::parseInstallment)
            Row(date, desc, value, inst)
        }
        if (rows.isEmpty()) throw OfxException("Não encontrei lançamentos no CSV.")

        // Fatura de cartão: tem coluna de parcela ou de portador. Nela as compras costumam vir positivas.
        val looksLikeCard = cols.installment != null || cols.holder != null
        val positives = rows.count { it.value > 0 }
        val negate = looksLikeCard && positives >= rows.size - positives

        val seen = mutableMapOf<String, Int>()
        val transactions = rows.map { r ->
            val memo = if (r.installment != null) "${r.desc} PARCELA ${r.installment.number} de ${r.installment.total}" else r.desc
            val baseKey = "${r.date}|$memo|${r.value}"
            val occurrence = (seen[baseKey] ?: 0) + 1
            seen[baseKey] = occurrence
            OfxTransaction(
                fitId = "csv-" + sha1("$baseKey|$occurrence").take(20),
                date = r.date,
                amount = if (negate) -r.value else r.value,
                trnType = null,
                memo = memo,
                name = null,
            )
        }

        return OfxStatement(
            isCreditCard = looksLikeCard,
            bankId = null,
            organization = null,
            accountId = null,
            currency = "BRL",
            start = transactions.minOf { it.date },
            end = transactions.maxOf { it.date },
            balance = null,
            transactions = transactions,
        )
    }

    private fun mapColumns(header: List<String>): Columns? {
        val names = header.map(::normalize)
        fun find(keys: List<String>, exclude: Set<Int> = emptySet()): Int? =
            keys.firstNotNullOfOrNull { k ->
                names.indices.firstOrNull { it !in exclude && (" " + names[it] + " ").contains(if (k.endsWith(" ")) " $k" else k) }
            }
        val date = find(DATE_KEYS) ?: return null
        val amount = find(AMOUNT_KEYS, setOf(date)) ?: return null
        val desc = find(DESC_KEYS, setOf(date, amount)) ?: return null
        val installment = find(INSTALLMENT_KEYS, setOf(date, amount, desc))
        val holder = find(HOLDER_KEYS, setOfNotNull(date, amount, desc, installment))
        return Columns(date, desc, amount, installment, holder)
    }

    private fun detectDelimiter(sample: List<String>): Char =
        listOf(';', '\t', ',').maxBy { d -> sample.sumOf { line -> split(line, d).size } }

    /** Divide uma linha respeitando aspas ("a;b" não é separado). */
    internal fun split(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out.map { it.trim() }
    }

    internal fun parseDate(text: String): LocalDate? {
        val s = text.trim().take(10)
        val dmy = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})$").find(s)
        val ymd = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(s)
        return try {
            when {
                ymd != null -> LocalDate.of(ymd.groupValues[1].toInt(), ymd.groupValues[2].toInt(), ymd.groupValues[3].toInt())
                dmy != null -> {
                    var y = dmy.groupValues[3].toInt()
                    if (y < 100) y += 2000
                    LocalDate.of(y, dmy.groupValues[2].toInt(), dmy.groupValues[1].toInt())
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** "R$ 1.234,56", "-15.137,98", "R$ -10,00", "10,00 D" → centavos com sinal. */
    internal fun parseValue(text: String): Long? {
        var s = text.trim().uppercase().replace("R$", "").replace(" ", "").replace(" ", "")
        var sign = 1
        if (s.endsWith("D")) { sign = -1; s = s.dropLast(1) } else if (s.endsWith("C")) s = s.dropLast(1)
        if (s.startsWith("(") && s.endsWith(")")) { sign = -sign; s = s.substring(1, s.length - 1) }
        val v = Money.parse(s) ?: return null
        return v * sign
    }

    internal fun parseInstallment(text: String): InstallmentInfo? {
        val m = Regex("(\\d{1,2})\\s*(?:/|de|DE|of)\\s*(\\d{1,2})").find(text) ?: return null
        val n = m.groupValues[1].toInt()
        val total = m.groupValues[2].toInt()
        return if (total in 2..72 && n in 1..total) InstallmentInfo(n, total) else null
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .uppercase().replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Escolhe o leitor certo (OFX ou CSV) pelo conteúdo do arquivo. */
object StatementReader {
    fun read(bytes: ByteArray): OfxStatement {
        val text = OfxParser.decode(bytes)
        return if (text.contains("<OFX>", ignoreCase = true)) OfxParser.parse(text) else CsvParser.parse(text)
    }
}
