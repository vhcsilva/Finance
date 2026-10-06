package app.financas.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.financas.domain.CardMonth
import app.financas.domain.Dates
import app.financas.domain.MonthSummary
import app.financas.domain.Money
import app.financas.domain.RecurringLine
import app.financas.domain.RecurringSource
import app.financas.ui.appViewModelFactory
import app.financas.ui.components.Caption
import app.financas.ui.components.MonthSwitcher
import app.financas.ui.components.ScreenHeader
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette
import kotlin.math.abs

private fun signed(cents: Long): String = if (cents > 0) "+ " + Money.format(cents) else Money.format(cents)
private fun netColor(cents: Long): Color = if (cents >= 0) Palette.Accent else Palette.Out

@Composable
fun AnnualScreen(onBack: () -> Unit) {
    val vm: AnnualViewModel = viewModel(factory = appViewModelFactory { repo, _ -> AnnualViewModel(repo) })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ScreenHeader("Resumo anual", onBack = onBack) {
                MonthSwitcher(s.year.toString(), onPrevious = { vm.shiftYear(-1) }, onNext = { vm.shiftYear(1) })
            }
        }
        item { YearCard(s) }
        item { Caption("Mês a mês · toque para abrir") }
        itemsIndexed(s.months, key = { _, m -> m.month.toString() }) { i, m ->
            MonthCard(
                s = s,
                m = m,
                status = s.statuses[i],
                open = s.open == i,
                onToggle = { vm.toggle(i) },
            )
        }
        item {
            Text(
                "Cartões contam no mês da fatura. Recorrentes já lançadas na conta saem de “outras saídas” " +
                    "para não contar duas vezes; as pagas no cartão aparecem só como referência.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun YearCard(s: AnnualState) {
    val rangeLabel = when {
        s.realizedUntil < 0 -> "nenhum mês realizado ainda"
        s.realizedUntil == 11 -> "jan–dez"
        else -> "jan–" + Dates.MONTHS_SHORT[s.realizedUntil]
    }
    Surface(
        color = Palette.Deep,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Líquido no ano ($rangeLabel)", color = Palette.DeepMuted, fontSize = 12.sp)
                    Text(signed(s.yearNet), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, style = NumberStyle)
                }
                if (s.realizedUntil in 0..10) {
                    Text("meses seguintes\nprevistos", color = Palette.DeepMuted, fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Entradas", color = Palette.DeepMuted, fontSize = 11.sp)
                    Text(Money.format(s.yearIncome), color = Palette.InOnDark, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = NumberStyle)
                }
                Column(Modifier.weight(1f)) {
                    Text("Saídas", color = Palette.DeepMuted, fontSize = 11.sp)
                    Text(Money.format(s.yearExpense), color = Palette.OutOnDark, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = NumberStyle)
                }
            }
            NetChart(s)
        }
    }
}

@Composable
private fun NetChart(s: AnnualState) {
    val max = s.months.maxOfOrNull { abs(it.net) }?.takeIf { it > 0 } ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            s.months.forEachIndexed { i, m ->
                val fraction = (abs(m.net).toFloat() / max).coerceIn(0f, 1f)
                val alpha = if (s.statuses[i] == MonthStatus.FUTURE) 0.4f else 1f
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (m.net > 0) Bar(fraction, Color(0xFF7FD1AE).copy(alpha = alpha), top = true)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (m.net < 0) Bar(fraction, Palette.OutOnDark.copy(alpha = alpha), top = false)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Dates.MONTH_INITIALS.forEachIndexed { i, l ->
                Text(
                    l,
                    fontSize = 10.sp,
                    color = Palette.DeepMuted,
                    fontWeight = if (s.statuses[i] == MonthStatus.CURRENT) FontWeight.ExtraBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, top: Boolean) {
    val shape = if (top) RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp) else RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
    Box(
        Modifier
            .width(12.dp)
            .fillMaxHeight(fraction.coerceAtLeast(0.06f))
            .clip(shape)
            .background(color)
    )
}

@Composable
private fun MonthCard(s: AnnualState, m: MonthSummary, status: MonthStatus, open: Boolean, onToggle: () -> Unit) {
    val name = Dates.MONTHS[m.month.monthValue - 1].replaceFirstChar { it.uppercase() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Palette.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (open) Palette.Accent else Palette.Line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = if (open) "aberto" else "fechado" }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        when (status) {
                            MonthStatus.CURRENT -> Badge("Atual", Palette.AccentSoft, Palette.Accent)
                            MonthStatus.FUTURE -> Badge("Previsto", Palette.LineSoft, Palette.Muted)
                            MonthStatus.PAST -> Unit
                        }
                    }
                    Text(
                        "Entradas ${Money.compact(m.income).removePrefix("R$ ")} · Saídas ${Money.compact(m.expense).removePrefix("R$ ")}",
                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                    )
                }
                Text(signed(m.net), color = netColor(m.net), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, style = NumberStyle)
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Palette.Muted)
            }
            if (open) {
                HorizontalDivider(color = Palette.LineSoft)
                MonthDetail(s, m)
            }
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun MonthDetail(s: AnnualState, m: MonthSummary) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
        if (m.income == 0L && m.expense == 0L && m.cards.isEmpty()) {
            Text("Nenhum lançamento neste mês.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(vertical = 12.dp))
            return@Column
        }

        // Entradas
        SectionTitle(Icons.Outlined.ArrowUpward, "ENTRADAS", Palette.In, Money.format(m.income), Palette.In)
        if (m.incomes.isEmpty()) DetailLine("Nenhuma entrada lançada", null)
        m.incomes.forEach { line ->
            val name = line.categoryId?.let { s.data.categoryById[it]?.name } ?: "Sem categoria"
            DetailLine(name, Money.format(line.amount))
        }

        SectionDivider()
        SectionTitle(Icons.Outlined.CreditCard, "CARTÕES", Palette.Out, Money.format(m.cardsTotal), Palette.Ink)
        if (m.cards.isEmpty()) DetailLine("Nenhuma compra nas faturas deste mês", null)
        m.cards.forEach { CardBlock(s, it) }

        SectionDivider()
        SectionTitle(Icons.Outlined.Repeat, "RECORRENTES", Palette.Out, Money.format(m.recurringTotal), Palette.Ink)
        if (m.recurring.isEmpty()) DetailLine("Nenhuma conta recorrente neste mês", null)
        m.recurring.forEach { RecurringRow(s, it) }

        SectionDivider()
        SectionTitle(Icons.Outlined.AccountBalance, "OUTRAS SAÍDAS DA CONTA", Palette.Out, Money.format(m.otherExpense), Palette.Ink)

        Surface(
            color = if (m.net >= 0) Palette.AccentSoft else Palette.OutSoft,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Entradas − saídas", fontSize = 12.sp, color = Palette.InkSoft, modifier = Modifier.weight(1f))
                    Text("${Money.format(m.income)} − ${Money.format(m.expense)}", fontSize = 12.sp, color = Palette.InkSoft, style = NumberStyle)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Líquido do mês", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text(signed(m.net), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = netColor(m.net), style = NumberStyle)
                }
                if (m.receivable > 0) {
                    HorizontalDivider(color = Color(0xFFC9D3CE))
                    Row {
                        Text("+ a receber de outras pessoas", fontSize = 12.sp, color = Palette.In, modifier = Modifier.weight(1f))
                        Text(Money.format(m.receivable), fontSize = 12.sp, color = Palette.In, style = NumberStyle)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = Palette.LineSoft, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SectionTitle(icon: ImageVector, label: String, color: Color, value: String, valueColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, letterSpacing = 0.3.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, style = NumberStyle)
    }
}

@Composable
private fun DetailLine(label: String, value: String?, note: String? = null, muted: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = if (muted || value == null) Palette.Muted else Palette.InkSoft)
            if (note != null) Text(note, fontSize = 11.sp, color = Palette.Muted)
        }
        if (value != null) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (muted) Palette.Muted else Palette.Ink, style = NumberStyle)
        }
    }
}

@Composable
private fun CardBlock(s: AnnualState, c: CardMonth) {
    val card = s.data.cardById[c.cardId]
    Surface(
        color = Color(0xFFF6F8F7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 4.dp, bottom = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp, 15.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(card?.color ?: 0xFF1F2937.toInt()))
                )
                Text(card?.name ?: "Cartão removido", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f))
                Text(Money.format(c.total), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = NumberStyle)
            }
            c.people.forEach { p ->
                val person = s.data.personById[p.personId]
                val color = Color(person?.color ?: 0xFF5B6661.toInt())
                val fraction = if (c.total > 0) (p.amount.toFloat() / c.total).coerceIn(0f, 1f) else 0f
                Column(Modifier.padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text((person?.name ?: "?").take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(person?.name ?: "Pessoa removida", fontSize = 13.sp, color = Palette.InkSoft, modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f))
                        Text(Money.format(p.amount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = NumberStyle)
                    }
                    Box(
                        Modifier
                            .padding(start = 26.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Palette.Track)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(s: AnnualState, r: RecurringLine) {
    val note = when (r.source) {
        RecurringSource.LAUNCHED -> if (r.occurrences > 1) "lançada · ${r.occurrences}×" else "lançada"
        RecurringSource.PROJECTED ->
            if (r.occurrences > 1) "${r.occurrences}× ${Money.format(r.unitAmount)} · prevista" else "prevista"
        RecurringSource.ON_CARD -> {
            val card = r.cardId?.let { s.data.cardById[it]?.name } ?: "cartão"
            "no $card · já somada na fatura"
        }
    }
    DetailLine(r.name, Money.format(r.amount), note, muted = r.source == RecurringSource.ON_CARD)
}
