@file:OptIn(ExperimentalLayoutApi::class)

package app.financas.ui.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.financas.domain.Billing
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.TxType
import app.financas.domain.ofx.ImportCandidate
import app.financas.ui.appViewModelFactory
import app.financas.ui.components.AmountText
import app.financas.ui.components.AppTextField
import app.financas.ui.components.Avatar
import app.financas.ui.components.BottomActions
import app.financas.ui.components.Caption
import app.financas.ui.components.CardSwatch
import app.financas.ui.components.ColorDot
import app.financas.ui.components.FieldLabel
import app.financas.ui.components.FormSheet
import app.financas.ui.components.MonthSwitcher
import app.financas.ui.components.Panel
import app.financas.ui.components.Pill
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.RowDivider
import app.financas.ui.components.ScreenHeader
import app.financas.ui.components.SecondaryButton
import app.financas.ui.components.SwitchRow
import app.financas.ui.navigation.Routes
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm: ImportViewModel = viewModel(factory = appViewModelFactory { repo, _ -> ImportViewModel(repo) })
    val uiState by vm.ui.collectAsStateWithLifecycle()
    val ui = uiState ?: return
    val step = ui.form.step

    BackHandler(enabled = step == ImportStep.DESTINATION || step == ImportStep.REVIEW) { vm.back() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (step == ImportStep.REVIEW) "Revisar importação" else "Importar OFX",
            subtitle = if (step == ImportStep.REVIEW) ui.destinationName + (ui.form.invoice?.takeIf { ui.isCardDestination }
                ?.let { " · fatura ${Dates.monthShortYear(it)}" } ?: "") else null,
            onBack = { if (step == ImportStep.DESTINATION || step == ImportStep.REVIEW) vm.back() else onBack() },
        )
        if (step == ImportStep.DESTINATION || step == ImportStep.REVIEW) StepIndicator(step)
        Box(Modifier.weight(1f)) {
            when (step) {
                ImportStep.PICK -> PickStep(ui, vm)
                ImportStep.DESTINATION -> DestinationStep(ui, vm)
                ImportStep.REVIEW -> ReviewStep(ui, vm)
                ImportStep.DONE -> DoneStep(ui, vm, navigate)
            }
        }
        when (step) {
            ImportStep.DESTINATION -> BottomActions {
                val n = ui.form.statement?.transactions?.size ?: 0
                PrimaryButton("Revisar $n transações", vm::goToReview, Modifier.weight(1f), enabled = ui.form.destination != null)
            }
            ImportStep.REVIEW -> Column(Modifier.background(Palette.Surface)) {
                RowDivider()
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                    Text("Total selecionado", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                    Text(Money.format(ui.selectedTotal), style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
                }
                Row(Modifier.padding(16.dp)) {
                    PrimaryButton(
                        if (ui.form.busy) "Importando…" else "Importar ${ui.selected.size} transações",
                        vm::commit,
                        Modifier.weight(1f),
                        enabled = !ui.form.busy && ui.selected.isNotEmpty(),
                    )
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun StepIndicator(step: ImportStep) {
    val current = if (step == ImportStep.DESTINATION) 2 else 3
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("1 · Arquivo", "2 · Destino", "3 · Revisão").forEachIndexed { i, label ->
            val done = i < current
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (done) Palette.Accent else Palette.Border)
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (done) Palette.Accent else Palette.Muted)
            }
        }
    }
}

// ---------- Passo 1 ----------

@Composable
private fun PickStep(ui: ImportUi, vm: ImportViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val (bytes, name) = withContext(Dispatchers.IO) { readUri(context, uri) }
                vm.load(bytes, name)
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Description, null, tint = Palette.Accent, modifier = Modifier.size(32.dp))
                Text("Importe o extrato ou a fatura", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Baixe o arquivo .ofx no app ou site do seu banco. O importador identifica se é conta ou cartão, " +
                        "sugere categorias com base no seu histórico, reconhece parcelas e evita duplicadas. " +
                        "Nada é salvo antes da sua revisão.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.InkSoft,
                )
                if (ui.form.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Lendo arquivo…")
                    }
                } else {
                    PrimaryButton("Escolher arquivo OFX", { launcher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
                }
                ui.form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (ui.data.accounts.isEmpty() && ui.data.cards.isEmpty()) {
            Text(
                "Dica: cadastre antes a conta ou o cartão que vai receber as transações.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun readUri(context: Context, uri: Uri): Pair<ByteArray?, String?> {
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
    return bytes to name
}

// ---------- Passo 2 ----------

@Composable
private fun DestinationStep(ui: ImportUi, vm: ImportViewModel) {
    val st = ui.form.statement ?: return
    val f = ui.form
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.AccentSoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Description, null, tint = Palette.Accent) }
                    Column(Modifier.weight(1f)) {
                        Text(f.fileName ?: "arquivo.ofx", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Lido com sucesso", style = MaterialTheme.typography.bodySmall)
                    }
                    SecondaryButton("Trocar", vm::restart)
                }
            }
        }
        item { Caption("Detectado no arquivo") }
        item {
            val debits = st.transactions.count { it.amount < 0 }
            val credits = st.transactions.size - debits
            val rows = listOfNotNull(
                "Tipo" to if (st.isCreditCard) "Cartão de crédito" else "Conta bancária",
                st.accountSuffix?.let { "Conta no arquivo" to "final $it" },
                st.start?.let { start -> "Período" to "${Dates.full(start)} a ${st.end?.let(Dates::full) ?: "—"}" },
                "Transações" to "${st.transactions.size} ($debits débitos, $credits créditos)",
                "Soma dos débitos" to Money.format(-st.transactions.filter { it.amount < 0 }.sumOf { it.amount }),
            )
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                rows.forEachIndexed { i, (k, v) ->
                    if (i > 0) RowDivider()
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text(k, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
                    }
                }
            }
        }
        item { Caption("Importar para") }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (ui.data.accounts.isEmpty() && ui.data.cards.isEmpty()) {
                    Text(
                        "Nenhuma conta ou cartão cadastrado. Volte e cadastre em Cadastros.",
                        color = Palette.Out,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                val options = ui.data.cards.map { DestinationKey(true, it.id) to Triple(it.name, it.color, it.ofxAccountId) } +
                    ui.data.accounts.map { DestinationKey(false, it.id) to Triple(it.name, it.color, it.ofxAccountId) }
                val ordered = if (st.isCreditCard) options else options.sortedBy { it.first.isCard }
                ordered.forEachIndexed { i, (key, info) ->
                    if (i > 0) RowDivider()
                    val selected = f.destination == key
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setDestination(key) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected, onClick = { vm.setDestination(key) })
                        if (key.isCard) CardSwatch(info.second, 18.dp, 12.dp) else ColorDot(info.second, 12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(info.first, style = MaterialTheme.typography.titleSmall)
                            Text(if (key.isCard) "Cartão" else "Conta", style = MaterialTheme.typography.bodySmall)
                        }
                        if (info.third != null && info.third == st.accountId) {
                            Text(
                                "Sugerido",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.Accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Palette.AccentSoft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                if (!st.isCreditCard && f.destination?.isCard == true || st.isCreditCard && f.destination?.isCard == false) {
                    Text(
                        "Atenção: o arquivo parece ser de ${if (st.isCreditCard) "cartão" else "conta"}, mas o destino escolhido é ${if (f.destination?.isCard == true) "um cartão" else "uma conta"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Out,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
        if (f.destination?.isCard == true && f.invoice != null) {
            item { Caption("Fatura") }
            item {
                val card = ui.data.cardById[f.destination.id]
                Panel(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fatura de ${Dates.monthShortYear(f.invoice)}", style = MaterialTheme.typography.titleSmall)
                            if (card != null) {
                                Text(
                                    "Fecha ${Dates.dayMonth(Billing.closingDate(f.invoice, card.closingDay))} · vence ${Dates.dayMonth(Billing.dueDate(f.invoice, card.closingDay, card.dueDay))}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        MonthSwitcher("", onPrevious = { vm.shiftInvoice(-1) }, onNext = { vm.shiftInvoice(1) })
                    }
                }
            }
        }
        item { Caption("Ao importar") }
        item {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SwitchRow("Ignorar duplicadas", "Pula transações já importadas ou lançadas à mão", f.options.skipDuplicates) {
                    vm.setOptions(f.options.copy(skipDuplicates = it))
                }
                RowDivider()
                SwitchRow("Sugerir categorias", "Usa descrições parecidas já cadastradas", f.options.suggestCategories) {
                    vm.setOptions(f.options.copy(suggestCategories = it))
                }
                RowDivider()
                SwitchRow("Detectar parcelas", "Reconhece “PARC 02/06” e projeta as próximas", f.options.detectInstallments) {
                    vm.setOptions(f.options.copy(detectInstallments = it))
                }
            }
        }
        f.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
    }
}

// ---------- Passo 3 ----------

@Composable
private fun ReviewStep(ui: ImportUi, vm: ImportViewModel) {
    var applyAll by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(ui.selected.size.toString(), "Selecionadas", Palette.Ink, Modifier.weight(1f))
                Stat(ui.withoutCategory.toString(), "Sem categoria", Palette.Warn, Modifier.weight(1f))
                Stat(ui.duplicates.toString(), "Duplicadas", Palette.Muted, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Todas", !ui.form.onlyPending, { vm.setOnlyPending(false) })
                Pill("Pendentes", ui.form.onlyPending, { vm.setOnlyPending(true) })
                Pill("Aplicar a todas…", false, { applyAll = true })
            }
        }
        items(ui.visible, key = { it.id }) { c ->
            CandidateCard(ui, c, onToggle = { vm.toggle(c.id) }, onOpen = { vm.openEditor(c.id) })
        }
        ui.form.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
    }

    val editing = ui.form.editingId?.let { id -> ui.form.candidates.firstOrNull { it.id == id } }
    if (editing != null) CandidateEditor(ui, editing, vm)
    if (applyAll) ApplyAllSheet(ui, vm) { applyAll = false }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Panel(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge.merge(NumberStyle), color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

@Composable
private fun CandidateCard(ui: ImportUi, c: ImportCandidate, onToggle: () -> Unit, onOpen: () -> Unit) {
    val category = c.categoryId?.let { ui.data.categoryById[it] }
    val borderColor = if (c.selected && category == null) Color(0xFFF2C7A8) else Palette.Line
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(end = 12.dp, top = 4.dp, bottom = 10.dp)) {
            Checkbox(checked = c.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.description,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (c.selected) Palette.Ink else Palette.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${c.rawMemo} · ${Dates.dayMonth(c.date)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    AmountText(c.amount, c.type, modifier = Modifier.padding(start = 8.dp), color = if (c.selected) null else Palette.Muted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (category != null) Tag(category.name, Palette.AccentSoft, Palette.Accent)
                    else Tag("Definir categoria", Palette.OutSoft, Palette.Warn)
                    c.installment?.takeIf { ui.form.options.detectInstallments }?.let {
                        Tag("Parcela ${it.label}", Palette.InSoft, Palette.In)
                    }
                    if (ui.isCardDestination && c.personIds.isNotEmpty()) {
                        Tag(c.personIds.mapNotNull { ui.data.personById[it]?.name }.joinToString(" + "), Palette.LineSoft, Palette.InkSoft)
                    }
                    if (c.duplicate) Tag("Já importada", Palette.Track, Palette.InkSoft)
                    c.ignoredReason?.let { Tag(it, Palette.Track, Palette.InkSoft) }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .heightIn(min = 26.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun CandidateEditor(ui: ImportUi, c: ImportCandidate, vm: ImportViewModel) {
    FormSheet(title = "Revisar transação", onDismiss = { vm.openEditor(null) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.rawMemo, style = MaterialTheme.typography.bodySmall)
                Text(Dates.full(c.date), style = MaterialTheme.typography.bodySmall)
            }
            AmountText(c.amount, c.type, fontSize = 18)
        }
        AppTextField(c.description, { text -> vm.updateCandidate(c.id) { it.copy(description = text) } }, "Descrição")
        Column {
            FieldLabel("Categoria")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.data.categories.filter { it.type == c.type }.forEach { cat ->
                    Pill(cat.name, c.categoryId == cat.id, {
                        vm.updateCandidate(c.id) { it.copy(categoryId = if (it.categoryId == cat.id) null else cat.id) }
                    }, filled = true)
                }
            }
        }
        if (ui.isCardDestination) {
            Column {
                FieldLabel("Quem vai pagar (divide igualmente)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.data.people.forEach { p ->
                        val sel = p.id in c.personIds
                        Pill(p.name, sel, {
                            vm.updateCandidate(c.id) {
                                val next = if (sel) it.personIds - p.id else it.personIds + p.id
                                it.copy(personIds = next.ifEmpty { it.personIds })
                            }
                        }, leading = { Avatar(p.name, p.color, 22.dp) })
                    }
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            SwitchRow(
                "Importar esta transação",
                if (c.duplicate) "Parece já ter sido importada" else c.ignoredReason,
                c.selected,
            ) { checked -> vm.updateCandidate(c.id) { it.copy(selected = checked) } }
        }
        PrimaryButton("Concluir", { vm.openEditor(null) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ApplyAllSheet(ui: ImportUi, vm: ImportViewModel, onClose: () -> Unit) {
    var people by remember { mutableStateOf(emptySet<Long>()) }
    var catOut by remember { mutableStateOf<Long?>(null) }
    var catIn by remember { mutableStateOf<Long?>(null) }
    FormSheet(title = "Aplicar às selecionadas", onDismiss = onClose) {
        Text(
            "A categoria é aplicada só às transações sem categoria. As pessoas substituem as atuais.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (ui.isCardDestination) {
            Column {
                FieldLabel("Quem vai pagar")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.data.people.forEach { p ->
                        val sel = p.id in people
                        Pill(p.name, sel, { people = if (sel) people - p.id else people + p.id }, leading = { Avatar(p.name, p.color, 22.dp) })
                    }
                }
            }
        }
        Column {
            FieldLabel("Categoria para saídas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.data.categories.filter { it.type == TxType.OUT }.forEach { cat ->
                    Pill(cat.name, catOut == cat.id, { catOut = if (catOut == cat.id) null else cat.id }, filled = true)
                }
            }
        }
        Column {
            FieldLabel("Categoria para entradas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.data.categories.filter { it.type == TxType.IN }.forEach { cat ->
                    Pill(cat.name, catIn == cat.id, { catIn = if (catIn == cat.id) null else cat.id }, filled = true)
                }
            }
        }
        PrimaryButton("Aplicar", {
            vm.applyToAll(people.takeIf { it.isNotEmpty() }, catOut, catIn)
            onClose()
        }, Modifier.fillMaxWidth())
    }
}

// ---------- Concluído ----------

@Composable
private fun DoneStep(ui: ImportUi, vm: ImportViewModel, navigate: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = Palette.Accent, modifier = Modifier.size(56.dp))
        Text("${ui.form.importedCount} transações importadas", style = MaterialTheme.typography.titleLarge)
        Text("Elas já aparecem em ${ui.destinationName}.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        PrimaryButton("Ver lançamentos", { navigate(Routes.TRANSACTIONS) }, Modifier.fillMaxWidth())
        SecondaryButton("Importar outro arquivo", vm::restart, Modifier.fillMaxWidth())
    }
}
