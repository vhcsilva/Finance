@file:OptIn(ExperimentalLayoutApi::class)

package app.financas.ui.registry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financas.data.AccountEntity
import app.financas.data.CardEntity
import app.financas.data.CategoryEntity
import app.financas.data.PersonEntity
import app.financas.data.RecurringEntity
import app.financas.domain.Billing
import app.financas.domain.Dates
import app.financas.domain.Frequency
import app.financas.domain.Money
import app.financas.domain.Recurrence
import app.financas.domain.TxType
import app.financas.ui.components.AmountText
import app.financas.ui.components.AppTextField
import app.financas.ui.components.Avatar
import app.financas.ui.components.CardSwatch
import app.financas.ui.components.CategoryBadge
import app.financas.ui.components.ColorPicker
import app.financas.ui.components.ConfirmDialog
import app.financas.ui.components.DateField
import app.financas.ui.components.EmptyState
import app.financas.ui.components.FieldLabel
import app.financas.ui.components.FormSheet
import app.financas.ui.components.Panel
import app.financas.ui.components.Pill
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.RoundIconButton
import app.financas.ui.components.RowDivider
import app.financas.ui.components.ScreenHeader
import app.financas.ui.components.SecondaryButton
import app.financas.ui.components.Segmented
import app.financas.ui.components.SwitchRow
import app.financas.ui.components.initials
import app.financas.ui.navigation.Routes
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette
import java.time.LocalDate
import java.time.YearMonth

@Composable
private fun AddButton(description: String, onClick: () -> Unit) {
    RoundIconButton(onClick = onClick, description = description) { Icon(Icons.Outlined.Add, null) }
}

/** Botões Salvar / Excluir no fim dos formulários. */
@Composable
private fun FormButtons(canDelete: Boolean, onDelete: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canDelete) SecondaryButton("Excluir", onDelete, color = MaterialTheme.colorScheme.error)
        PrimaryButton("Salvar", onSave, Modifier.weight(1f))
    }
}

// =============================== CONTAS ===============================

@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val vm = registryViewModel()
    val dataState by vm.data.collectAsStateWithLifecycle()
    val data = dataState ?: return
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    val newAccount = { AccountEntity(name = "", color = Palette.choices[data.accounts.size % Palette.choices.size]) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader("Contas", onBack = onBack) { AddButton("Nova conta") { editing = newAccount() } }
        }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (data.accounts.isEmpty()) {
                    EmptyState("Nenhuma conta ainda. Cadastre sua conta corrente, poupança ou carteira.")
                    PrimaryButton("Nova conta", { editing = newAccount() }, Modifier
                        .fillMaxWidth()
                        .padding(16.dp))
                }
                data.accounts.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    val count = data.transactions.count { it.accountId == a.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = a }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LetterTile(a.name, a.color)
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall)
                            Text(plural(count, "lançamento", "lançamentos"), style = MaterialTheme.typography.bodySmall)
                        }
                        AmountText(data.accountBalance(a.id), null, fontSize = 15)
                    }
                }
            }
        }
    }

    editing?.let { item ->
        AccountForm(
            item = item,
            transactionCount = data.transactions.count { it.accountId == item.id },
            onSave = { vm.saveAccount(it); editing = null },
            onDelete = { vm.deleteAccount(item); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun LetterTile(name: String, color: Int) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(color)),
        contentAlignment = Alignment.Center,
    ) { Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun AccountForm(
    item: AccountEntity,
    transactionCount: Int,
    onSave: (AccountEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var balance by remember(item.id) { mutableStateOf(if (item.initialBalance != 0L) Money.plain(item.initialBalance) else "") }
    var color by remember(item.id) { mutableIntStateOf(item.color) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    FormSheet(if (item.id == 0L) "Nova conta" else "Editar conta", onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Ground)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LetterTile(name.ifBlank { "?" }, color)
            Column {
                Text(name.ifBlank { "Nome da conta" }, style = MaterialTheme.typography.titleSmall)
                Text("Pré-visualização", style = MaterialTheme.typography.bodySmall)
            }
        }
        AppTextField(name, { name = it; error = null }, "Nome", placeholder = "Ex.: Conta corrente", error = error)
        AppTextField(balance, { balance = it }, "Saldo inicial", placeholder = "0,00", keyboardType = KeyboardType.Decimal, prefix = "R$ ")
        Column {
            FieldLabel("Cor de exibição")
            ColorPicker(color, { color = it })
        }
        FormButtons(canDelete = item.id != 0L, onDelete = { confirm = true }) {
            val parsed = if (balance.isBlank()) 0L else Money.parse(balance)
            when {
                name.isBlank() -> { error = "Informe o nome." }
                parsed == null -> { error = "Saldo inicial inválido." }
                else -> onSave(item.copy(name = name.trim(), initialBalance = parsed, color = color))
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Excluir conta?",
            text = if (transactionCount > 0) "Os $transactionCount lançamentos desta conta também serão excluídos." else "Esta ação não pode ser desfeita.",
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
        )
    }
}

// =============================== CARTÕES ===============================

@Composable
fun CardsScreen(onBack: () -> Unit) {
    val vm = registryViewModel()
    val dataState by vm.data.collectAsStateWithLifecycle()
    val data = dataState ?: return
    var editing by remember { mutableStateOf<CardEntity?>(null) }
    val newCard = {
        CardEntity(name = "", color = Palette.choices[(data.cards.size + 1) % Palette.choices.size], closingDay = 25, dueDay = 5)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Cartões de crédito", onBack = onBack) { AddButton("Novo cartão") { editing = newCard() } } }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (data.cards.isEmpty()) {
                    EmptyState("Nenhum cartão ainda. Informe o dia de fechamento e de vencimento para o app calcular as faturas.")
                    PrimaryButton("Novo cartão", { editing = newCard() }, Modifier
                        .fillMaxWidth()
                        .padding(16.dp))
                }
                data.cards.forEachIndexed { i, c ->
                    if (i > 0) RowDivider()
                    val invoice = Billing.invoiceFor(LocalDate.now(), c.closingDay)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = c }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CardSwatch(c.color)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            Text("Fecha dia ${c.closingDay} · vence dia ${c.dueDay}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            AmountText(data.invoiceTotal(c.id, invoice), null, fontSize = 15)
                            Text("fatura ${Dates.monthShortYear(invoice)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        CardForm(
            item = item,
            onSave = { vm.saveCard(it); editing = null },
            onDelete = { vm.deleteCard(item); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CardForm(item: CardEntity, onSave: (CardEntity) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var color by remember(item.id) { mutableIntStateOf(item.color) }
    var closing by remember(item.id) { mutableStateOf(item.closingDay.toString()) }
    var due by remember(item.id) { mutableStateOf(item.dueDay.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val closingDay = closing.toIntOrNull()?.takeIf { it in 1..31 }
    val dueDay = due.toIntOrNull()?.takeIf { it in 1..31 }

    FormSheet(if (item.id == 0L) "Novo cartão" else "Editar cartão", onDismiss) {
        Surface(color = Color(color), shape = RoundedCornerShape(20.dp), modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(name.ifBlank { "Nome do cartão" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("Fecha dia", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        Text(closingDay?.toString() ?: "—", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, style = NumberStyle)
                    }
                    Column {
                        Text("Vence dia", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        Text(dueDay?.toString() ?: "—", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, style = NumberStyle)
                    }
                }
            }
        }
        AppTextField(name, { name = it; error = null }, "Nome", placeholder = "Ex.: Cartão Roxo")
        Column {
            FieldLabel("Cor de exibição")
            ColorPicker(color, { color = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(closing, { closing = it.filter(Char::isDigit).take(2) }, "Dia de fechamento", Modifier.weight(1f), keyboardType = KeyboardType.Number)
            AppTextField(due, { due = it.filter(Char::isDigit).take(2) }, "Dia de vencimento", Modifier.weight(1f), keyboardType = KeyboardType.Number)
        }
        if (closingDay != null && dueDay != null) {
            val invoice = Billing.invoiceFor(LocalDate.now(), closingDay)
            Surface(color = Palette.InSoft, shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Compras feitas a partir do dia $closingDay entram na fatura do mês seguinte. " +
                        "A fatura atual (${Dates.monthShortYear(invoice)}) vence em ${Dates.full(Billing.dueDate(invoice, closingDay, dueDay))}.",
                    color = Color(0xFF1A3F9E),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FormButtons(canDelete = item.id != 0L, onDelete = { confirm = true }) {
            when {
                name.isBlank() -> { error = "Informe o nome." }
                closingDay == null || dueDay == null -> { error = "Os dias devem estar entre 1 e 31." }
                else -> onSave(item.copy(name = name.trim(), color = color, closingDay = closingDay, dueDay = dueDay))
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Excluir cartão?",
            text = "Todas as compras e parcelas deste cartão também serão excluídas.",
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
        )
    }
}

// =============================== RECORRENTES ===============================

@Composable
fun RecurringScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = registryViewModel()
    val dataState by vm.data.collectAsStateWithLifecycle()
    val data = dataState ?: return
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }
    val newItem = { RecurringEntity(name = "", amount = 0, frequency = Frequency.MONTHLY, anchorDate = LocalDate.now()) }
    val monthly = data.recurring.sumOf { Recurrence.monthlyEstimate(it.amount, it.frequency) }
    val today = LocalDate.now()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Contas recorrentes", onBack = onBack) { AddButton("Nova conta recorrente") { editing = newItem() } } }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Compromisso mensal estimado", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                    AmountText(monthly, null, fontSize = 17)
                }
            }
        }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (data.recurring.isEmpty()) {
                    EmptyState("Cadastre aluguel, assinaturas, diarista… O app mostra as próximas na tela inicial.")
                }
                data.recurring.sortedBy { Recurrence.next(it.anchorDate, it.frequency, today) }.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    val next = Recurrence.next(r.anchorDate, r.frequency, today)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = r }
                            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${Recurrence.describe(r.anchorDate, r.frequency)} · próxima ${Dates.dayMonth(next)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            AmountText(r.amount, null)
                            Text(Recurrence.label(r.frequency), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        }
                        TextButton(onClick = { navigate(Routes.edit(recurringId = r.id)) }) { Text("Lançar") }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        RecurringForm(
            item = item,
            categories = data.categories.filter { it.type == TxType.OUT },
            onSave = { vm.saveRecurring(it); editing = null },
            onDelete = { vm.deleteRecurring(item); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RecurringForm(
    item: RecurringEntity,
    categories: List<CategoryEntity>,
    onSave: (RecurringEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var amount by remember(item.id) { mutableStateOf(if (item.amount > 0) Money.plain(item.amount) else "") }
    var frequency by remember(item.id) { mutableStateOf(item.frequency) }
    var anchor by remember(item.id) { mutableStateOf(item.anchorDate) }
    var categoryId by remember(item.id) { mutableStateOf(item.categoryId) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val frequencies = listOf(Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY)

    FormSheet(if (item.id == 0L) "Nova conta recorrente" else "Editar conta recorrente", onDismiss) {
        AppTextField(name, { name = it; error = null }, "Nome", placeholder = "Ex.: Internet")
        AppTextField(amount, { amount = it; error = null }, "Valor", placeholder = "0,00", keyboardType = KeyboardType.Decimal, prefix = "R$ ")
        Column {
            FieldLabel("Recorrência")
            Segmented(
                options = frequencies.map(Recurrence::label),
                selected = frequencies.indexOf(frequency),
                onSelect = { frequency = frequencies[it] },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DateField("Primeira ocorrência", anchor, { anchor = it })
        Text(
            "${Recurrence.describe(anchor, frequency)} · próxima em ${Dates.full(Recurrence.next(anchor, frequency, LocalDate.now()))}",
            style = MaterialTheme.typography.bodySmall,
        )
        Column {
            FieldLabel("Categoria (opcional)")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { c ->
                    Pill(c.name, categoryId == c.id, { categoryId = if (categoryId == c.id) null else c.id }, filled = true)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FormButtons(canDelete = item.id != 0L, onDelete = { confirm = true }) {
            val cents = Money.parse(amount)
            when {
                name.isBlank() -> { error = "Informe o nome." }
                cents == null || cents <= 0 -> { error = "Informe um valor maior que zero." }
                else -> onSave(item.copy(name = name.trim(), amount = cents, frequency = frequency, anchorDate = anchor, categoryId = categoryId))
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Excluir conta recorrente?",
            text = "Os lançamentos já feitos não serão alterados.",
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
        )
    }
}

// =============================== CATEGORIAS ===============================

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = registryViewModel()
    val dataState by vm.data.collectAsStateWithLifecycle()
    val data = dataState ?: return
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val type = if (tab == 0) TxType.OUT else TxType.IN
    val list = data.categories.filter { it.type == type }
    val outCount = data.categories.count { it.type == TxType.OUT }
    val inCount = data.categories.size - outCount

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(horizontal = 0.dp)) { ScreenHeader("Categorias", onBack = onBack) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Segmented(
                options = listOf("Saídas · $outCount", "Entradas · $inCount"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        items(list, key = { it.id }) { c ->
            Panel(Modifier.height(108.dp), onClick = { editing = c }) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    CategoryBadge(c, 40.dp)
                    Text(c.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                    if (c.ignoreInReports) Text("fora dos relatórios", fontSize = 10.sp, color = Palette.Muted, textAlign = TextAlign.Center)
                }
            }
        }
        item {
            Surface(
                onClick = { editing = CategoryEntity(name = "", type = type, color = Palette.choices[list.size % Palette.choices.size]) },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, Color(0xFF9AA6A1)),
                modifier = Modifier.height(108.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Add, null, tint = Palette.Accent)
                    Text("Nova", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                }
            }
        }
    }

    editing?.let { item ->
        CategoryForm(
            item = item,
            onSave = { vm.saveCategory(it); editing = null },
            onDelete = { vm.deleteCategory(item); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CategoryForm(item: CategoryEntity, onSave: (CategoryEntity) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var color by remember(item.id) { mutableIntStateOf(item.color) }
    var ignore by remember(item.id) { mutableStateOf(item.ignoreInReports) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val kind = if (item.type == TxType.OUT) "saída" else "entrada"

    FormSheet(if (item.id == 0L) "Nova categoria de $kind" else "Editar categoria de $kind", onDismiss) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(color).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text(if (name.isBlank()) "?" else initials(name), color = Color(color), fontWeight = FontWeight.ExtraBold) }
            AppTextField(name, { name = it; error = null }, "Nome", Modifier.weight(1f), placeholder = "Ex.: Pets", error = error)
        }
        Column {
            FieldLabel("Cor")
            ColorPicker(color, { color = it })
        }
        Panel(Modifier.fillMaxWidth()) {
            SwitchRow(
                "Ignorar nos relatórios",
                "Use para pagamento de fatura e transferências entre suas próprias contas",
                ignore,
            ) { ignore = it }
        }
        FormButtons(canDelete = item.id != 0L, onDelete = { confirm = true }) {
            if (name.isBlank()) {
                error = "Informe o nome."
            } else onSave(item.copy(name = name.trim(), color = color, ignoreInReports = ignore))
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Excluir categoria?",
            text = "Os lançamentos desta categoria ficarão sem categoria.",
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
        )
    }
}

// =============================== PESSOAS ===============================

@Composable
fun PeopleScreen(onBack: () -> Unit) {
    val vm = registryViewModel()
    val dataState by vm.data.collectAsStateWithLifecycle()
    val data = dataState ?: return
    var editing by remember { mutableStateOf<PersonEntity?>(null) }
    val current = YearMonth.now()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader("Pessoas", onBack = onBack) {
                AddButton("Nova pessoa") {
                    editing = PersonEntity(name = "", color = Palette.choices[(data.people.size * 3) % Palette.choices.size])
                }
            }
        }
        item {
            Text(
                "Quem pode dividir as compras do cartão com você. “Eu” é fixo e não pode ser removido.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
        }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                data.people.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    val owed = data.entries.filter { it.period == current && it.cardId != null }.sumOf { e ->
                        val share = e.shares[p.id] ?: 0L
                        if (e.type == TxType.IN) -share else share
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = p }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(p.name, p.color)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            val info = when {
                                p.isMe -> "Padrão em novos lançamentos"
                                owed > 0 && data.isSettled(p.id, current) -> "Pago em ${Dates.monthShortYear(current)}"
                                owed > 0 -> "A receber em ${Dates.monthShortYear(current)}"
                                else -> "Nada pendente neste mês"
                            }
                            Text(info, style = MaterialTheme.typography.bodySmall)
                        }
                        if (owed > 0) AmountText(owed, null, color = if (p.isMe) Palette.Ink else Palette.In)
                    }
                }
            }
        }
    }

    editing?.let { item ->
        PersonForm(
            item = item,
            onSave = { vm.savePerson(it); editing = null },
            onDelete = { vm.deletePerson(item); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun PersonForm(item: PersonEntity, onSave: (PersonEntity) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var color by remember(item.id) { mutableIntStateOf(item.color) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    FormSheet(if (item.id == 0L) "Adicionar pessoa" else "Editar pessoa", onDismiss) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(name.ifBlank { "?" }, color, 56.dp)
            AppTextField(name, { name = it; error = null }, "Nome", Modifier.weight(1f), placeholder = "Ex.: Mariana", error = error)
        }
        Column {
            FieldLabel("Cor")
            ColorPicker(color, { color = it })
        }
        FormButtons(canDelete = item.id != 0L && !item.isMe, onDelete = { confirm = true }) {
            if (name.isBlank()) {
                error = "Informe o nome."
            } else onSave(item.copy(name = name.trim(), color = color))
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Excluir ${item.name}?",
            text = "As divisões de compras com esta pessoa serão removidas (a parte dela volta para você).",
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
        )
    }
}
