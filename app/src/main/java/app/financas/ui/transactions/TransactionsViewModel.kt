package app.financas.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.CategoryEntity
import app.financas.data.FinanceRepository
import app.financas.data.LedgerItem
import app.financas.data.LedgerKind
import app.financas.data.PersonEntity
import app.financas.data.Snapshot
import app.financas.domain.Dates
import app.financas.domain.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class TxFilters(
    val month: YearMonth = YearMonth.now(),
    /** 0 = tudo, 1 = contas, 2 = cartões */
    val tab: Int = 0,
    /** "a<id>" para conta, "c<id>" para cartão */
    val source: String? = null,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val query: String = "",
)

data class SourceOption(val key: String, val name: String)

data class DayGroup(val heading: String, val total: Long, val items: List<LedgerItem>)

data class TransactionsState(
    val filters: TxFilters,
    val groups: List<DayGroup>,
    val income: Long,
    val expense: Long,
    val sources: List<SourceOption>,
    val categories: List<CategoryEntity>,
    val people: List<PersonEntity>,
)

class TransactionsViewModel(repository: FinanceRepository) : ViewModel() {
    private val filters = MutableStateFlow(TxFilters())

    val state: StateFlow<TransactionsState?> = combine(repository.snapshot, filters) { s, f -> build(s, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (TxFilters) -> TxFilters) = filters.update(transform)

    private fun build(s: Snapshot, f: TxFilters): TransactionsState {
        val today = LocalDate.now()
        val q = f.query.trim().lowercase()
        val items = s.ledger.filter { item ->
            item.period == f.month &&
                (f.tab == 0 || (f.tab == 1 && item.kind == LedgerKind.ACCOUNT) || (f.tab == 2 && item.kind == LedgerKind.CARD)) &&
                (f.source == null || f.source == sourceKey(item)) &&
                (f.categoryId == null || item.category?.id == f.categoryId) &&
                (f.personId == null || f.personId in item.personIds) &&
                (q.isEmpty() || item.description.lowercase().contains(q))
        }
        val counted = items.filterNot { it.ignored }
        val groups = items.groupBy { it.date }.entries.sortedByDescending { it.key }.map { (date, list) ->
            DayGroup(
                heading = Dates.dayHeading(date, today),
                total = list.filterNot { it.ignored }.sumOf { if (it.type == TxType.IN) it.amount else -it.amount },
                items = list,
            )
        }
        val sources = s.accounts.map { SourceOption("a${it.id}", it.name) } + s.cards.map { SourceOption("c${it.id}", it.name) }
        return TransactionsState(
            filters = f,
            groups = groups,
            income = counted.filter { it.type == TxType.IN }.sumOf { it.amount },
            expense = counted.filter { it.type == TxType.OUT }.sumOf { it.amount },
            sources = when (f.tab) {
                1 -> sources.filter { it.key.startsWith("a") }
                2 -> sources.filter { it.key.startsWith("c") }
                else -> sources
            },
            categories = s.categories,
            people = s.people,
        )
    }

    private fun sourceKey(item: LedgerItem) = (if (item.kind == LedgerKind.ACCOUNT) "a" else "c") + item.sourceId
}
