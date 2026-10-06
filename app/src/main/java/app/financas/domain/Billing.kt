package app.financas.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/** Uma parcela de uma compra no cartão, já associada à fatura em que será cobrada. */
data class InstallmentPlan(val number: Int, val amount: Long, val invoice: YearMonth)

/**
 * Regras de fatura do cartão de crédito.
 *
 * A fatura é identificada pelo mês em que FECHA. Compras feitas antes do dia de fechamento
 * entram na fatura daquele mês; compras no dia do fechamento ou depois entram na do mês seguinte.
 */
object Billing {

    /** Data de fechamento da fatura de [invoice] (dias como 31 são ajustados para o fim do mês). */
    fun closingDate(invoice: YearMonth, closingDay: Int): LocalDate =
        invoice.atDay(min(closingDay, invoice.lengthOfMonth()))

    /** Em qual fatura uma compra feita em [purchaseDate] é cobrada. */
    fun invoiceFor(purchaseDate: LocalDate, closingDay: Int): YearMonth {
        val ym = YearMonth.from(purchaseDate)
        return if (purchaseDate.isBefore(closingDate(ym, closingDay))) ym else ym.plusMonths(1)
    }

    /** Vencimento da fatura: no mesmo mês se o dia de vencimento for depois do fechamento, senão no mês seguinte. */
    fun dueDate(invoice: YearMonth, closingDay: Int, dueDay: Int): LocalDate {
        val m = if (dueDay > closingDay) invoice else invoice.plusMonths(1)
        return m.atDay(min(dueDay, m.lengthOfMonth()))
    }

    /**
     * Gera as parcelas de uma compra de [total] centavos em [count] vezes.
     * Quando a compra foi importada a partir da parcela [firstNumber] (ex.: 3/6),
     * só são geradas as parcelas de [firstNumber] até [count]; a primeira gerada cai em [firstInvoice].
     */
    fun installments(total: Long, count: Int, firstInvoice: YearMonth, firstNumber: Int = 1): List<InstallmentPlan> {
        require(count >= 1) { "count deve ser >= 1" }
        require(firstNumber in 1..count) { "firstNumber fora do intervalo" }
        val amounts = Money.splitEven(total, count)
        return (firstNumber..count).map { n ->
            InstallmentPlan(n, amounts[n - 1], firstInvoice.plusMonths((n - firstNumber).toLong()))
        }
    }
}
