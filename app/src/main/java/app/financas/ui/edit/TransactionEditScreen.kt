@file:OptIn(ExperimentalLayoutApi::class)

package app.financas.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.financas.domain.Money
import app.financas.domain.TxType
import app.financas.ui.LocalSnackbar
import app.financas.ui.appViewModelFactory
import app.financas.ui.components.AppTextField
import app.financas.ui.components.Avatar
import app.financas.ui.components.BottomActions
import app.financas.ui.components.CardSwatch
import app.financas.ui.components.ColorDot
import app.financas.ui.components.ConfirmDialog
import app.financas.ui.components.DateField
import app.financas.ui.components.FieldLabel
import app.financas.ui.components.Pill
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.SecondaryButton
import app.financas.ui.components.Segmented
import app.financas.ui.components.Stepper
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette
import java.time.LocalDate

@Composable
fun TransactionEditScreen(onDone: () -> Unit) {
    val vm: TransactionEditViewModel = viewModel(factory = appViewModelFactory { repo, handle -> TransactionEditViewModel(repo, handle) })
    val uiState by vm.ui.collectAsStateWithLifecycle()
    val ui = uiState ?: return
    val f = ui.form
    if (!f.loaded) return

    val snackbar = LocalSnackbar.current
    LaunchedEffect(f.finished) { if (f.finished) onDone() }
    LaunchedEffect(f.message) {
        f.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Fechar") }
            Text(
                if (ui.isEditing) "Editar lançamento" else "Novo lançamento",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (ui.isEditing) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!ui.isEditing) {
                Segmented(
                    options = listOf("Conta", "Cartão de crédito"),
                    selected = if (f.mode == EditMode.ACCOUNT) 0 else 1,
                    onSelect = { vm.setMode(if (it == 0) EditMode.ACCOUNT else EditMode.CARD) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Tipo + valor
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val outLabel = if (f.mode == EditMode.CARD) "Compra" else "Saída"
                    val inLabel = if (f.mode == EditMode.CARD) "Estorno" else "Entrada"
                    Pill(outLabel, f.type == TxType.OUT, { vm.setType(TxType.OUT) },
                        leading = { Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(16.dp), tint = Palette.Out) })
                    Pill(inLabel, f.type == TxType.IN, { vm.setType(TxType.IN) },
                        leading = { Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(16.dp), tint = Palette.In) })
                }
                Text(if (f.mode == EditMode.CARD) "Valor total" else "Valor", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                BasicTextField(
                    value = f.amountText,
                    onValueChange = vm::setAmount,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(Palette.Accent),
                    textStyle = TextStyle(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.amount(f.type),
                        textAlign = TextAlign.Center,
                        fontFeatureSettings = "tnum",
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text("R$ ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Muted)
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                if (f.amountText.isEmpty()) {
                                    Text("0,00", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Border)
                                }
                                inner()
                            }
                        }
                    },
                )
            }

            AppTextField(f.description, vm::setDescription, "Descrição", placeholder = "Ex.: Supermercado")

            Column {
                FieldLabel(if (f.mode == EditMode.CARD) "Data da compra" else "Data")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val today = LocalDate.now()
                    Pill("Hoje", f.date == today, { vm.setDate(today) })
                    Pill("Ontem", f.date == today.minusDays(1), { vm.setDate(today.minusDays(1)) })
                    DateField("", f.date, vm::setDate, Modifier.weight(1f))
                }
            }

            if (f.mode == EditMode.ACCOUNT) {
                Column {
                    FieldLabel("Conta")
                    if (ui.data.accounts.isEmpty()) {
                        Text("Nenhuma conta cadastrada. Cadastre em Cadastros › Contas.", color = Palette.Out, style = MaterialTheme.typography.bodySmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.data.accounts.forEach { a ->
                            Pill(a.name, f.accountId == a.id, { vm.setAccount(a.id) }, leading = { ColorDot(a.color) })
                        }
                    }
                }
            } else {
                Column {
                    FieldLabel("Cartão")
                    if (ui.data.cards.isEmpty()) {
                        Text("Nenhum cartão cadastrado. Cadastre em Cadastros › Cartões.", color = Palette.Out, style = MaterialTheme.typography.bodySmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.data.cards.forEach { c ->
                            Pill(c.name, f.cardId == c.id, { vm.setCard(c.id) }, leading = { CardSwatch(c.color, 16.dp, 11.dp) })
                        }
                    }
                }
                Surface(color = Palette.Ground, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Parcelamento", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            Text(ui.installmentSummary ?: "${f.installments}×", style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
                            ui.invoiceHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Stepper(f.installments, vm::setInstallments, min = maxOf(1, f.firstInstallment), max = 72)
                    }
                }
            }

            Column {
                FieldLabel("Categoria")
                if (ui.categories.isEmpty()) {
                    Text("Nenhuma categoria deste tipo. Cadastre em Cadastros › Categorias.", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.categories.forEach { c ->
                        Pill(c.name, f.categoryId == c.id, { vm.setCategory(c.id) }, filled = true)
                    }
                }
            }

            if (f.mode == EditMode.CARD) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quem vai pagar", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::splitEvenly) { Text("Dividir igualmente") }
                    }
                    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Palette.Line), color = Palette.Surface) {
                        Column {
                            f.splits.forEachIndexed { i, row ->
                                val person = ui.data.personById[row.personId] ?: return@forEachIndexed
                                if (i > 0) androidx.compose.material3.HorizontalDivider(color = Palette.LineSoft)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.togglePerson(row.personId) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Checkbox(checked = row.selected, onCheckedChange = { vm.togglePerson(row.personId) })
                                    Avatar(person.name, person.color, 30.dp)
                                    Text(person.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    if (row.selected) {
                                        OutlinedTextField(
                                            value = row.shareText,
                                            onValueChange = { vm.setShare(row.personId, it) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.merge(NumberStyle).copy(textAlign = TextAlign.End),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Palette.Border),
                                            modifier = Modifier.width(120.dp),
                                        )
                                    } else {
                                        Text("—", color = Palette.Muted, modifier = Modifier.padding(end = 12.dp))
                                    }
                                }
                            }
                        }
                    }
                    val total = ui.amount ?: 0L
                    if (f.customShares && total > 0 && ui.sharesSum != total) {
                        Text(
                            "Soma das partes: ${Money.format(ui.sharesSum)} de ${Money.format(total)}",
                            color = Palette.Out,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            } else {
                AppTextField(f.notes, vm::setNotes, "Observação (opcional)", placeholder = "Ex.: compra do mês")
            }

            f.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }

        BottomActions {
            if (!ui.isEditing) {
                SecondaryButton("Salvar e novo", { vm.save(andNew = true) })
            }
            val label = if (f.mode == EditMode.CARD && f.installments > 1 && !ui.isEditing) "Salvar ${f.installments} parcelas" else "Salvar"
            PrimaryButton(label, { vm.save(andNew = false) }, Modifier.weight(1f))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir lançamento?",
            text = if (f.mode == EditMode.CARD && f.installments > 1) "Todas as parcelas desta compra serão excluídas." else "Esta ação não pode ser desfeita.",
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
