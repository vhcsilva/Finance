package app.financas.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.FinanceRepository
import app.financas.data.PersonEntity
import app.financas.data.Snapshot
import app.financas.domain.Dates
import app.financas.domain.GroupRow
import app.financas.domain.Grouping
import app.financas.domain.Reports
import app.financas.domain.Totals
import app.financas.domain.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs

data class ReportFilters(
    val annual: Boolean = true,
    val period: YearMonth = YearMonth.now(),
    val grouping: Grouping = Grouping.CATEGORY,
    /** Para o agrupamento por categoria: mostrar saídas ou entradas. */
    val categoryType: TxType = TxType.OUT,
)

data class ReportRow(
    val label: String,
    val color: Int?,
    val value: Long,
    val percent: Int,
    val bar: Float,
    val detail: String? = null,
)

data class PersonCardShare(val name: String, val color: Int, val amount: Long)

data class PersonReport(
    val person: PersonEntity,
    val total: Long,
    val count: Int,
    val cards: List<PersonCardShare>,
    val settled: Boolean,
)

data class ReportsState(
    val filters: ReportFilters,
    val periodLabel: String,
    val totals: Totals,
    val monthlyAverage: Long,
    val chart: List<GroupRow>,
    val rows: List<ReportRow>,
    val rowsTitle: String,
    val people: List<PersonReport>,
    val peopleTotal: Long,
    val groupings: List<Grouping>,
)

class ReportsViewModel(private val repository: FinanceRepository) : ViewModel() {
    private val filters = MutableStateFlow(ReportFilters())

    val state: StateFlow<ReportsState?> = combine(repository.snapshot, filters) { s, f -> build(s, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (ReportFilters) -> ReportFilters) = filters.update { f ->
        val next = transform(f)
        // "Mês" só faz sentido na visão anual.
        if (!next.annual && next.grouping == Grouping.MONTH) next.copy(grouping = Grouping.CATEGORY) else next
    }

    fun shiftPeriod(delta: Long) = update {
        it.copy(period = if (it.annual) it.period.plusYears(delta) else it.period.plusMonths(delta))
    }

    fun setSettled(personId: Long, settled: Boolean) {
        val period = filters.value.period
        viewModelScope.launch { repository.setSettled(personId, period, settled) }
    }

    private fun build(s: Snapshot, f: ReportFilters): ReportsState {
        val year = f.period.year
        val entries = Reports.filter(s.entries, year, if (f.annual) null else f.period.monthValue)
        val totals = Reports.totals(entries)
        val monthsWithData = if (f.annual) {
            Reports.byMonth(s.entries, year).count { it.income > 0 || it.expense > 0 }.coerceAtLeast(1)
        } else 1

        val (title, rows) = when (f.grouping) {
            Grouping.MONTH -> "Saídas por mês" to monthRows(entries, year)
            Grouping.CATEGORY -> categoryRows(s, entries, f.categoryType)
            Grouping.ACCOUNT -> "Movimento por conta" to accountRows(s, entries)
            Grouping.CARD -> "Faturas por cartão" to cardRows(s, entries)
            Grouping.PERSON -> "Cartões por pessoa" to emptyList()
        }
        val people = if (f.grouping == Grouping.PERSON) personReports(s, entries, f) else emptyList()

        return ReportsState(
            filters = f,
            periodLabel = if (f.annual) year.toString() else Dates.monthChip(f.period),
            totals = totals,
            monthlyAverage = totals.expense / monthsWithData,
            chart = if (f.annual) Reports.byMonth(s.entries, year) else emptyList(),
            rows = rows,
            rowsTitle = title,
            people = people,
            peopleTotal = people.sumOf { it.total },
            groupings = if (f.annual) Grouping.entries.toList() else Grouping.entries.filter { it != Grouping.MONTH },
        )
    }

    private fun rowsFrom(items: List<Triple<String, Int?, Long>>, detail: (Int) -> String? = { null }): List<ReportRow> {
        val total = items.sumOf { abs(it.third) }
        val max = items.maxOfOrNull { abs(it.third) } ?: 0L
        return items.mapIndexed { i, (label, color, value) ->
            ReportRow(
                label = label, color = color, value = value,
                percent = Reports.percent(abs(value), total),
                bar = if (max == 0L) 0f else abs(value).toFloat() / max,
                detail = detail(i),
            )
        }
    }

    private fun monthRows(entries: List<app.financas.domain.Entry>, year: Int): List<ReportRow> {
        val rows = Reports.byMonth(entries, year).filter { it.income > 0 || it.expense > 0 }
        return rowsFrom(rows.map { Triple(Dates.MONTHS[(it.key ?: 1L).toInt() - 1].replaceFirstChar { c -> c.uppercase() }, null, it.expense) }) { i ->
            "Entradas ${app.financas.domain.Money.format(rows[i].income)}"
        }
    }

    private fun categoryRows(s: Snapshot, entries: List<app.financas.domain.Entry>, type: TxType): Pair<String, List<ReportRow>> {
        val rows = Reports.group(entries.filter { it.type == type }, Grouping.CATEGORY)
        val items = rows.map { r ->
            val c = r.key?.let { s.categoryById[it] }
            Triple(c?.name ?: "Sem categoria", c?.color, if (type == TxType.OUT) r.expense else r.income)
        }.sortedByDescending { it.third }
        return (if (type == TxType.OUT) "Saídas por categoria" else "Entradas por categoria") to rowsFrom(items)
    }

    private fun accountRows(s: Snapshot, entries: List<app.financas.domain.Entry>): List<ReportRow> {
        val rows = Reports.group(entries, Grouping.ACCOUNT)
        val items = rows.map { r ->
            val a = r.key?.let { s.accountById[it] }
            Triple(a?.name ?: "Conta removida", a?.color, r.expense)
        }
        return rowsFrom(items) { i ->
            "Entradas ${app.financas.domain.Money.format(rows[i].income)} · resultado ${app.financas.domain.Money.format(rows[i].net)}"
        }
    }

    private fun cardRows(s: Snapshot, entries: List<app.financas.domain.Entry>): List<ReportRow> {
        val rows = Reports.group(entries, Grouping.CARD)
        val items = rows.map { r ->
            val c = r.key?.let { s.cardById[it] }
            Triple(c?.name ?: "Cartão removido", c?.color, r.expense - r.income)
        }
        return rowsFrom(items) { i ->
            if (rows[i].income > 0) "Créditos/estornos ${app.financas.domain.Money.format(rows[i].income)}" else null
        }
    }

    private fun personReports(s: Snapshot, entries: List<app.financas.domain.Entry>, f: ReportFilters): List<PersonReport> {
        val cardEntries = entries.filter { it.cardId != null }
        return Reports.byPerson(cardEntries).mapNotNull { row ->
            val personId = row.key ?: return@mapNotNull null
            val person = s.personById[personId] ?: return@mapNotNull null
            val mine = cardEntries.filter { personId in it.shares }
            val byCard = mine.groupBy { it.cardId }.mapNotNull { (cardId, list) ->
                val card = cardId?.let { s.cardById[it] } ?: return@mapNotNull null
                val amount = list.sumOf { e ->
                    val share = e.shares[personId] ?: 0L
                    if (e.type == TxType.IN) -share else share
                }
                PersonCardShare(card.name, card.color, amount)
            }.sortedByDescending { it.amount }
            PersonReport(
                person = person,
                total = row.expense - row.income,
                count = mine.size,
                cards = byCard,
                settled = !f.annual && s.isSettled(personId, f.period),
            )
        }
    }
}
