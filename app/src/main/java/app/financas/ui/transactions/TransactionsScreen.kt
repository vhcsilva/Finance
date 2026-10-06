package app.financas.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.financas.data.LedgerKind
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.TxType
import app.financas.ui.appViewModelFactory
import app.financas.ui.components.AmountText
import app.financas.ui.components.EmptyState
import app.financas.ui.components.FilterPill
import app.financas.ui.components.LedgerRow
import app.financas.ui.components.MonthSwitcher
import app.financas.ui.components.Panel
import app.financas.ui.components.RoundIconButton
import app.financas.ui.components.RowDivider
import app.financas.ui.components.ScreenHeader
import app.financas.ui.components.Segmented
import app.financas.ui.navigation.Routes
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette

@Composable
fun TransactionsScreen(navigate: (String) -> Unit) {
    val vm: TransactionsViewModel = viewModel(factory = appViewModelFactory { repo, _ -> TransactionsViewModel(repo) })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val f = s.filters
    var searching by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                ScreenHeader("Lançamentos") {
                    Surface(
                        onClick = { navigate(Routes.IMPORT) },
                        shape = RoundedCornerShape(50),
                        color = Palette.Surface,
                        border = BorderStroke(1.dp, Palette.Line),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp))
                            Text("Importar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    RoundIconButton(onClick = {
                        searching = !searching
                        if (!searching) vm.update { it.copy(query = "") }
                    }, description = "Buscar") {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, null)
                    }
                }
            }
            if (searching) {
                item {
                    OutlinedTextField(
                        value = f.query,
                        onValueChange = { q -> vm.update { it.copy(query = q) } },
                        placeholder = { Text("Buscar pela descrição") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Palette.Surface,
                            focusedContainerColor = Palette.Surface,
                            unfocusedBorderColor = Palette.Border,
                        ),
                        trailingIcon = if (f.query.isNotEmpty()) ({
                            IconButton(onClick = { vm.update { it.copy(query = "") } }) { Icon(Icons.Outlined.Close, "Limpar") }
                        }) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Segmented(
                    options = listOf("Tudo", "Contas", "Cartões"),
                    selected = f.tab,
                    onSelect = { tab -> vm.update { it.copy(tab = tab, source = null) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthSwitcher(
                        label = Dates.monthChip(f.month),
                        onPrevious = { vm.update { it.copy(month = it.month.minusMonths(1)) } },
                        onNext = { vm.update { it.copy(month = it.month.plusMonths(1)) } },
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterPill(
                        label = if (f.tab == 2) "Cartão" else if (f.tab == 1) "Conta" else "Conta / cartão",
                        selectedKey = f.source,
                        options = s.sources.map { it.key to it.name },
                        onSelect = { key -> vm.update { it.copy(source = key) } },
                    )
                    FilterPill(
                        label = "Categoria",
                        selectedKey = f.categoryId,
                        options = s.categories.map {
                            it.id to (it.name + if (it.type == TxType.IN) " (entrada)" else "")
                        },
                        onSelect = { key -> vm.update { it.copy(categoryId = key) } },
                        allLabel = "Todas",
                    )
                    FilterPill(
                        label = "Pessoa",
                        selectedKey = f.personId,
                        options = s.people.map { it.id to it.name },
                        onSelect = { key -> vm.update { it.copy(personId = key) } },
                        allLabel = "Todas",
                    )
                }
            }
            item {
                Panel(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Summary("Entradas", Money.format(s.income), Palette.In, Modifier.weight(1f))
                        Summary("Saídas", Money.format(s.expense), Palette.Out, Modifier.weight(1f))
                        Summary("Resultado", Money.format(s.income - s.expense), Palette.Ink, Modifier.weight(1f))
                    }
                }
            }
            if (f.tab != 1) {
                item {
                    Text(
                        "Compras no cartão aparecem no mês da fatura.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (s.groups.isEmpty()) {
                item { EmptyState("Nenhum lançamento neste período com os filtros escolhidos.") }
            }
            items(s.groups, key = { it.heading }) { g ->
                Column {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)) {
                        Text(g.heading, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                        AmountText(
                            kotlin.math.abs(g.total),
                            if (g.total >= 0) TxType.IN else TxType.OUT,
                            fontSize = 12,
                            color = Palette.Muted,
                        )
                    }
                    Panel(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        g.items.forEachIndexed { i, item ->
                            if (i > 0) RowDivider()
                            LedgerRow(item) {
                                val mode = if (item.kind == LedgerKind.CARD) Routes.MODE_CARD else Routes.MODE_ACCOUNT
                                navigate(Routes.edit(mode, item.refId))
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { navigate(Routes.edit(if (f.tab == 2) Routes.MODE_CARD else Routes.MODE_ACCOUNT)) },
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
private fun Summary(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(NumberStyle), color = color, maxLines = 1)
    }
}
