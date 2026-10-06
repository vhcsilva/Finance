@file:OptIn(ExperimentalLayoutApi::class)

package app.financas.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.financas.domain.Dates
import app.financas.domain.Grouping
import app.financas.domain.Money
import app.financas.domain.TxType
import app.financas.ui.appViewModelFactory
import app.financas.ui.components.AmountText
import app.financas.ui.components.Avatar
import app.financas.ui.components.Caption
import app.financas.ui.components.CardSwatch
import app.financas.ui.components.EmptyState
import app.financas.ui.components.MonthSwitcher
import app.financas.ui.components.Panel
import app.financas.ui.components.Pill
import app.financas.ui.components.RowDivider
import app.financas.ui.components.ScreenHeader
import app.financas.ui.components.SectionHeader
import app.financas.ui.components.Segmented
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette

@Composable
fun ReportsScreen(onOpenAnnual: () -> Unit = {}) {
    val vm: ReportsViewModel = viewModel(factory = appViewModelFactory { repo, _ -> ReportsViewModel(repo) })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val f = s.filters

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader("Relatórios") {
                MonthSwitcher(s.periodLabel, onPrevious = { vm.shiftPeriod(-1) }, onNext = { vm.shiftPeriod(1) })
            }
        }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                onClick = onOpenAnnual,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Deep),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.CalendarMonth, null, tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text("Resumo anual mês a mês", style = MaterialTheme.typography.titleSmall)
                        Text("Entradas, cartões por pessoa, recorrentes e líquido", style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.ChevronRight, null, tint = Palette.Muted,
                    )
                }
            }
        }
        item {
            Segmented(
                options = listOf("Anual", "Mensal"),
                selected = if (f.annual) 0 else 1,
                onSelect = { i -> vm.update { it.copy(annual = i == 0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        item { Caption("Agrupar por") }
        item {
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                s.groupings.forEach { g ->
                    Pill(g.label, f.grouping == g, { vm.update { it.copy(grouping = g) } })
                }
            }
        }

        item { SummaryPanel(s) }

        if (f.grouping == Grouping.PERSON) {
            item { SectionHeader("Cartões por pessoa") }
            if (s.people.isEmpty()) item { EmptyState("Nenhuma compra no cartão neste período.") }
            item {
                if (s.people.isNotEmpty()) PeopleShareBar(s)
            }
            items(s.people, key = { it.person.id }) { p ->
                Panel(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(p.person.name, p.person.color)
                            Column(Modifier.weight(1f)) {
                                Text(p.person.name, style = MaterialTheme.typography.titleMedium)
                                val status = when {
                                    p.person.isMe -> null
                                    f.annual -> null
                                    p.settled -> "pago"
                                    else -> "a receber"
                                }
                                Text(
                                    "${p.count} lançamento${if (p.count == 1) "" else "s"}" + (status?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            AmountText(p.total, null, fontSize = 16)
                        }
                        Column(Modifier.padding(start = 52.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.cards.forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CardSwatch(c.color, 14.dp, 10.dp)
                                    Text(c.name, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft, modifier = Modifier
                                        .padding(start = 6.dp)
                                        .weight(1f))
                                    Text(Money.format(c.amount), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle))
                                }
                            }
                        }
                        if (!p.person.isMe && !f.annual) {
                            Row(Modifier.padding(start = 40.dp)) {
                                TextButton(onClick = { vm.setSettled(p.person.id, !p.settled) }) {
                                    Text(if (p.settled) "Desfazer pagamento" else "Marcar como pago")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeader(s.rowsTitle) }
                    if (f.grouping == Grouping.CATEGORY) {
                        val other = if (f.categoryType == TxType.OUT) TxType.IN else TxType.OUT
                        TextButton(onClick = { vm.update { it.copy(categoryType = other) } }) {
                            Text(if (other == TxType.IN) "Ver entradas" else "Ver saídas")
                        }
                    }
                }
            }
            item {
                Panel(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (s.rows.isEmpty()) EmptyState("Nenhum lançamento neste período.")
                    s.rows.forEachIndexed { i, r ->
                        if (i > 0) RowDivider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(Money.format(r.value), style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
                                Text(" · ${r.percent}%", style = MaterialTheme.typography.bodySmall)
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Palette.LineSoft)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(r.bar.coerceIn(0f, 1f))
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(r.color?.let { Color(it) } ?: Palette.Accent)
                                )
                            }
                            r.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Compras no cartão contam no mês da fatura. Categorias marcadas como “ignorar nos relatórios” ficam de fora.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun SummaryPanel(s: ReportsState) {
    Panel(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.filters.annual) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Entradas × saídas por mês", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Legend(Palette.In, "Entradas")
                    Legend(Palette.ChartOut, "Saídas")
                }
                MonthlyChart(s)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(if (s.filters.annual) "Entradas no ano" else "Entradas", Money.compact(s.totals.income), Modifier.weight(1f))
                Metric(if (s.filters.annual) "Saídas no ano" else "Saídas", Money.compact(s.totals.expense), Modifier.weight(1f))
                if (s.filters.annual) {
                    Metric("Média mensal", Money.compact(s.monthlyAverage), Modifier.weight(1f))
                } else {
                    Metric("Resultado", Money.compact(s.totals.net), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, fontSize = 11.sp, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun MonthlyChart(s: ReportsState) {
    val max = s.chart.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > 0 } ?: 1L
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            s.chart.forEach { m ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Bar(m.income.toFloat() / max, Palette.In)
                    Bar(m.expense.toFloat() / max, Palette.ChartOut)
                }
            }
        }
        RowDivider()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Dates.MONTH_INITIALS.forEach {
                Text(it, fontSize = 10.sp, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .width(8.dp)
            .fillMaxHeight(fraction.coerceIn(0.005f, 1f))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(if (fraction <= 0f) Color.Transparent else color)
    )
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(NumberStyle), maxLines = 1)
    }
}

/** Barra horizontal com a fatia de cada pessoa. */
@Composable
private fun PeopleShareBar(s: ReportsState) {
    val total = s.peopleTotal.takeIf { it > 0 } ?: return
    Panel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Total das faturas", style = MaterialTheme.typography.bodySmall)
            Text(Money.format(total), style = MaterialTheme.typography.headlineSmall.merge(NumberStyle))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                s.people.filter { it.total > 0 }.forEach { p ->
                    Box(
                        Modifier
                            .weight(p.total.toFloat())
                            .fillMaxHeight()
                            .background(Color(p.person.color))
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                s.people.filter { it.total > 0 }.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(p.person.color))
                        )
                        Text(
                            "${p.person.name} ${app.financas.domain.Reports.percent(p.total, total)}%",
                            fontSize = 12.sp,
                            color = Palette.InkSoft,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
