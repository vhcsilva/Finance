package app.financas.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.financas.data.LedgerKind
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.Recurrence
import app.financas.ui.appViewModelFactory
import app.financas.ui.components.AmountText
import app.financas.ui.components.CardSwatch
import app.financas.ui.components.ColorDot
import app.financas.ui.components.LedgerRow
import app.financas.ui.components.MonthSwitcher
import app.financas.ui.components.Panel
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.RowDivider
import app.financas.ui.components.SecondaryButton
import app.financas.ui.components.SectionHeader
import app.financas.ui.navigation.Routes
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette

@Composable
fun HomeScreen(navigate: (String) -> Unit) {
    val vm: HomeViewModel = viewModel(factory = appViewModelFactory { repo, _ -> HomeViewModel(repo) })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Visão geral", style = MaterialTheme.typography.bodySmall)
                        Text(Dates.monthTitle(s.month), style = MaterialTheme.typography.headlineSmall)
                    }
                    MonthSwitcher(label = "", onPrevious = { vm.shiftMonth(-1) }, onNext = { vm.shiftMonth(1) })
                }
            }
            item { BalanceCard(s) }

            if (s.isEmpty) {
                item { Onboarding(navigate) }
            }

            if (s.accounts.isNotEmpty()) {
                item { SectionHeader("Contas", "Ver todas") { navigate(Routes.ACCOUNTS) } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.accounts, key = { it.account.id }) { a ->
                            Panel(Modifier.width(160.dp), onClick = { navigate(Routes.ACCOUNTS) }) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ColorDot(a.account.color)
                                        Text(a.account.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    AmountText(a.balance, null, fontSize = 17)
                                }
                            }
                        }
                    }
                }
            }

            if (s.cards.isNotEmpty()) {
                item { SectionHeader(if (s.month == java.time.YearMonth.now()) "Faturas abertas" else "Faturas do mês", "Cartões") { navigate(Routes.CARDS) } }
                items(s.cards, key = { "card${it.card.id}" }) { c ->
                    Panel(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        onClick = { navigate(Routes.TRANSACTIONS) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardSwatch(c.card.color)
                            Column(Modifier.weight(1f)) {
                                Text(c.card.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Fatura ${Dates.monthShortYear(c.invoice)} · fecha ${Dates.dayMonth(c.closing)} · vence ${Dates.dayMonth(c.due)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            AmountText(c.total, null, fontSize = 15)
                        }
                    }
                }
            }

            if (s.upcoming.isNotEmpty()) {
                item { SectionHeader("Próximas recorrentes", "Ver todas") { navigate(Routes.RECURRING) } }
                item {
                    Panel(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        s.upcoming.forEachIndexed { i, u ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { navigate(Routes.edit(recurringId = u.recurring.id)) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Dates.MONTHS_SHORT[u.date.monthValue - 1].uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Palette.Muted)
                                    Text(u.date.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, style = NumberStyle)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(u.recurring.name, style = MaterialTheme.typography.titleSmall)
                                    Text(Recurrence.label(u.recurring.frequency) + " · toque para lançar", style = MaterialTheme.typography.bodySmall)
                                }
                                AmountText(u.recurring.amount, null, color = Palette.Out)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Últimos lançamentos", "Ver todos") { navigate(Routes.TRANSACTIONS) } }
            item {
                Panel(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (s.recent.isEmpty()) {
                        Text(
                            "Nenhum lançamento ainda. Toque em + para registrar o primeiro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    s.recent.forEachIndexed { i, item ->
                        if (i > 0) RowDivider()
                        LedgerRow(item) {
                            val mode = if (item.kind == LedgerKind.CARD) Routes.MODE_CARD else Routes.MODE_ACCOUNT
                            navigate(Routes.edit(mode, item.refId))
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { navigate(Routes.edit()) },
            containerColor = Palette.Accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = "Novo lançamento") }
    }
}

@Composable
private fun BalanceCard(s: HomeState) {
    Surface(
        color = Palette.Deep,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Saldo em contas", color = Palette.DeepMuted, fontSize = 13.sp)
                Text(
                    Money.format(s.totalBalance),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = NumberStyle,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Entradas", Money.format(s.monthIncome), Palette.InOnDark, Modifier.weight(1f))
                Metric("Saídas", Money.format(s.monthExpense), Palette.OutOnDark, Modifier.weight(1f))
                Metric("Faturas", Money.format(s.invoicesTotal), Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Palette.DeepMuted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = NumberStyle, maxLines = 1)
    }
}

@Composable
private fun Onboarding(navigate: (String) -> Unit) {
    Panel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comece por aqui", style = MaterialTheme.typography.titleMedium)
            Text(
                "Cadastre suas contas e cartões. Depois é só lançar os gastos ou importar o extrato em OFX ou CSV.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Nova conta", { navigate(Routes.ACCOUNTS) }, Modifier.weight(1f))
                SecondaryButton("Novo cartão", { navigate(Routes.CARDS) }, Modifier.weight(1f))
            }
        }
    }
}
