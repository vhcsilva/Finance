package app.financas.domain.ofx

import java.text.Normalizer
import java.time.LocalDate

/** Parcela detectada na descrição, ex.: "PARC 02/06" → 2 de 6. */
data class InstallmentInfo(val number: Int, val total: Int) {
    val label: String get() = "$number/$total"
}

/** Um lançamento já existente, usado para achar o "nome bonito" e a categoria de descrições parecidas. */
data class HistoryItem(
    val normalized: String,
    val description: String,
    val categoryId: Long?,
    val date: LocalDate,
)

data class Suggestion(val description: String?, val categoryId: Long?)

/** Regras de inferência usadas pelo importador de OFX. */
object ImportHeuristics {

    private val PARC = Regex("(?i)\\bparc(?:ela)?\\.?\\s*(\\d{1,2})\\s*(?:/|de)\\s*(\\d{1,2})\\b")
    private val TRAILING = Regex("\\s(\\d{1,2})/(\\d{1,2})\\s*$")
    private val SMALL_WORDS = setOf("de", "da", "do", "das", "dos", "e", "em", "com", "para", "a", "o", "na", "no")

    /**
     * Procura indicação de parcela. "PARC 02/06" e "Parcela 2 de 6" sempre contam;
     * o formato "LOJA 02/06" no final só é aceito em faturas de cartão ([lenient]).
     */
    fun detectInstallment(text: String, lenient: Boolean): InstallmentInfo? {
        val m = PARC.find(text) ?: (if (lenient) TRAILING.find(text) else null) ?: return null
        val n = m.groupValues[1].toInt()
        val total = m.groupValues[2].toInt()
        return if (total in 2..72 && n in 1..total) InstallmentInfo(n, total) else null
    }

    fun stripInstallment(text: String, lenient: Boolean): String {
        var s = PARC.replace(text, " ")
        if (lenient && detectInstallment(text, true) != null && PARC.find(text) == null) s = TRAILING.replace(s, " ")
        return s.replace(Regex("[\\s\\-–—:]+$"), "").replace(Regex("\\s+"), " ").trim()
    }

    /** Chave de comparação: maiúsculas, sem acentos, sem parcela, sem números e pontuação. */
    fun normalize(text: String): String {
        val noInstallment = stripInstallment(text, lenient = true)
        val noAccents = Normalizer.normalize(noInstallment, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.uppercase()
            .replace(Regex("[^A-Z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** "SUPERMERC BOM PRECO PARC 02/06" → "Supermerc Bom Preco". */
    fun prettify(text: String): String {
        val clean = stripInstallment(text, lenient = true)
            .replace(Regex("\\s*[*|]\\s*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return text.trim()
        return clean.split(" ").mapIndexed { i, w ->
            when {
                w.any { it.isDigit() } -> w
                i > 0 && w.lowercase() in SMALL_WORDS -> w.lowercase()
                else -> w.lowercase().replaceFirstChar { it.titlecase() }
            }
        }.joinToString(" ")
    }

    /** Pagamento da fatura lançado como crédito dentro do próprio OFX do cartão. */
    fun isCardPayment(text: String): Boolean {
        val n = normalize(text)
        if (n.contains("ESTORNO")) return false
        return n.contains("PAGAMENTO") || n.startsWith("PGTO") || n.contains("PAGTO") || n.contains("PAG FATURA")
    }

    /** Pagamento de fatura de cartão saindo da conta corrente. */
    fun isInvoicePayment(text: String): Boolean {
        val n = normalize(text)
        return n.contains("FATURA") || (n.contains("CARTAO") && (n.contains("PAG") || n.contains("PGTO")))
    }

    fun isTransfer(text: String, trnType: String?): Boolean {
        if (trnType == "XFER") return true
        val n = " " + normalize(text) + " "
        return listOf(" PIX ", " TED ", " DOC ", "TRANSF").any { n.contains(it) }
    }

    /**
     * Sugere descrição e categoria a partir do histórico (mais recente primeiro).
     * Igualdade exata reaproveita a descrição já editada; semelhança pelas duas primeiras palavras só sugere a categoria.
     */
    fun suggest(normalized: String, history: List<HistoryItem>): Suggestion? {
        if (normalized.isBlank()) return null
        val sorted = history.sortedByDescending { it.date }
        sorted.firstOrNull { it.normalized == normalized }?.let {
            return Suggestion(it.description, it.categoryId)
        }
        val key = prefixKey(normalized) ?: return null
        val match = sorted.firstOrNull { it.categoryId != null && prefixKey(it.normalized) == key } ?: return null
        return Suggestion(null, match.categoryId)
    }

    private fun prefixKey(normalized: String): String? {
        val tokens = normalized.split(" ").filter { it.length >= 3 }
        val key = tokens.take(2).joinToString(" ")
        return if (key.length >= 5) key else null
    }
}
