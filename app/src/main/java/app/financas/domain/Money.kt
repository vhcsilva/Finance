package app.financas.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Valores monetários são sempre guardados em centavos (Long) para evitar erros de arredondamento.
 */
object Money {
    const val MINUS = '−'

    /** "R$ 1.234,56" (negativos recebem "− " na frente). */
    fun format(cents: Long): String {
        val body = "R$ " + plain(abs(cents))
        return if (cents < 0) "$MINUS $body" else body
    }

    /** "+ R$ 6.200,00" para entradas e "− R$ 312,48" para saídas. */
    fun signed(cents: Long, type: TxType): String =
        (if (type == TxType.IN) "+ " else "$MINUS ") + format(abs(cents))

    /** "1.234,56" — sem símbolo, usado em campos de texto. */
    fun plain(cents: Long): String {
        val a = abs(cents)
        val units = groupThousands(a / 100)
        val frac = (a % 100).toString().padStart(2, '0')
        return (if (cents < 0) "-" else "") + units + "," + frac
    }

    /** "R$ 62.400" — arredondado para reais, usado em resumos compactos. */
    fun compact(cents: Long): String {
        val reais = BigDecimal(cents).movePointLeft(2).setScale(0, RoundingMode.HALF_UP).toLong()
        val body = "R$ " + groupThousands(abs(reais))
        return if (reais < 0) "$MINUS $body" else body
    }

    private fun groupThousands(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Converte texto digitado pelo usuário em centavos.
     * Aceita "1.234,56", "1234,5", "1234.56", "R$ 12", "-30,00". Retorna null se inválido.
     */
    fun parse(input: String): Long? {
        var s = input.trim()
            .replace("R$", "")
            .replace(" ", "")
            .replace(" ", "")
        if (s.isEmpty()) return null
        var negative = false
        when {
            s.startsWith("-") || s.startsWith(MINUS) -> { negative = true; s = s.substring(1) }
            s.startsWith("+") -> s = s.substring(1)
        }
        if (s.isEmpty() || s.any { !(it.isDigit() || it == '.' || it == ',') }) return null
        if (s.none { it.isDigit() }) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> {
                if (s.count { it == ',' } > 1) return null
                s.replace(',', '.')
            }
            lastDot >= 0 -> {
                val decimals = s.length - lastDot - 1
                if (s.count { it == '.' } > 1 || decimals == 3) s.replace(".", "") else s
            }
            else -> s
        }
        return try {
            val v = BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
            if (negative) -v else v
        } catch (e: Exception) {
            null
        }
    }

    /** Divide um total em partes iguais; os centavos que sobram vão para as primeiras partes. */
    fun splitEven(total: Long, parts: Int): List<Long> {
        require(parts > 0) { "parts deve ser > 0" }
        val base = total / parts
        val rem = (total % parts).toInt()
        return List(parts) { i -> base + if (i < rem) 1 else 0 }
    }

    /** Distribui um total proporcionalmente aos pesos (método do maior resto). A soma é sempre exata. */
    fun proportional(total: Long, weights: List<Long>): List<Long> {
        if (weights.isEmpty()) return emptyList()
        val sum = weights.sum()
        if (sum == 0L) return splitEven(total, weights.size)
        val t = BigDecimal(total)
        val s = BigDecimal(sum)
        val exact = weights.map { t.multiply(BigDecimal(it)).divide(s, 10, RoundingMode.HALF_UP) }
        val floors = exact.map { it.setScale(0, RoundingMode.FLOOR).toLong() }
        var remaining = total - floors.sum()
        val result = floors.toMutableList()
        val order = exact.indices.sortedByDescending { exact[it].subtract(BigDecimal(floors[it])) }
        var k = 0
        while (remaining > 0 && order.isNotEmpty()) {
            result[order[k % order.size]] += 1
            remaining--
            k++
        }
        return result
    }
}
