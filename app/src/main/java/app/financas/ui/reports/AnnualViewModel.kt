package app.financas.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.FinanceRepository
import app.financas.data.Snapshot
import app.financas.domain.AnnualSummary
import app.financas.domain.MonthSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

enum class MonthStatus { PAST, CURRENT, FUTURE }

data class AnnualState(
    val year: Int,
    val months: List<MonthSummary>,
    val statuses: List<MonthStatus>,
    /** Índice (0..11) do mês aberto, ou -1. */
    val open: Int,
    /** Meses que entram no total do ano (até o mês atual, no ano corrente). */
    val realizedUntil: Int,
    val data: Snapshot,
) {
    private val realized get() = months.take(realizedUntil + 1)
    val yearIncome: Long get() = realized.sumOf { it.income }
    val yearExpense: Long get() = realized.sumOf { it.expense }
    val yearNet: Long get() = yearIncome - yearExpense
}

class AnnualViewModel(repository: FinanceRepository) : ViewModel() {
    private val now = YearMonth.now()
    private val year = MutableStateFlow(now.year)
    private val open = MutableStateFlow(now.monthValue - 1)

    val state: StateFlow<AnnualState?> = combine(repository.snapshot, year, open) { s, y, o ->
        val months = AnnualSummary.build(y, s.described, s.recurringDefs, s.me?.id)
        val statuses = months.map {
            when {
                it.month < now -> MonthStatus.PAST
                it.month == now -> MonthStatus.CURRENT
                else -> MonthStatus.FUTURE
            }
        }
        val realizedUntil = when {
            y < now.year -> 11
            y > now.year -> -1
            else -> now.monthValue - 1
        }
        AnnualState(y, months, statuses, o, realizedUntil, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftYear(delta: Int) {
        year.update { it + delta }
        open.value = if (year.value == now.year) now.monthValue - 1 else -1
    }

    fun toggle(index: Int) = open.update { if (it == index) -1 else index }
}
