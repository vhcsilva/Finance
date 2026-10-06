@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.financas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.financas.data.CategoryEntity
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.TxType
import app.financas.ui.theme.NumberStyle
import app.financas.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// ---------- Estrutura ----------

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold),
        color = Palette.Muted,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Cartão branco com borda fina, usado em todas as listas. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, Palette.Line)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = Palette.Surface, border = border) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = Palette.Surface, border = border) {
            Column(content = content)
        }
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = Palette.LineSoft)

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Muted,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    )
}

// ---------- Controles ----------

/** Controle segmentado (ex.: Tudo | Contas | Cartões). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) Palette.Surface else Color.Transparent)
                    .semantics { this.selected = isSel }
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSel) Palette.Ink else Palette.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Botão em formato de pílula para seleção (contas, categorias, filtros). */
@Composable
fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val bg = when {
        selected && filled -> Palette.Accent
        selected -> Palette.AccentSoft
        else -> Palette.Surface
    }
    val fg = when {
        selected && filled -> Color.White
        selected -> Palette.Accent
        else -> Palette.Ink
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, if (selected) Palette.Accent else Palette.Border),
        modifier = modifier.semantics { this.selected = selected },
    ) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            trailing?.invoke()
        }
    }
}

/** Pílula de filtro com menu suspenso. */
@Composable
fun <K> FilterPill(
    label: String,
    selectedKey: K?,
    options: List<Pair<K, String>>,
    onSelect: (K?) -> Unit,
    allLabel: String = "Todos",
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selectedKey }?.second
    Box {
        Pill(
            label = current ?: label,
            selected = current != null,
            onClick = { open = true },
            trailing = { Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(16.dp), tint = if (current != null) Palette.Accent else Palette.Muted) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { open = false; onSelect(null) })
            options.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { open = false; onSelect(key) },
                    trailingIcon = if (key == selectedKey) ({ Icon(Icons.Outlined.Check, null) }) else null,
                )
            }
        }
    }
}

@Composable
fun MonthSwitcher(label: String, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(onClick = onPrevious, description = "Período anterior") { Icon(Icons.Outlined.ChevronLeft, null) }
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
        RoundIconButton(onClick = onNext, description = "Próximo período") { Icon(Icons.Outlined.ChevronRight, null) }
    }
}

@Composable
fun RoundIconButton(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Line),
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
            shape = RoundedCornerShape(12.dp),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "Diminuir" },
        ) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
            modifier = Modifier.width(36.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
            shape = RoundedCornerShape(12.dp),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "Aumentar" },
        ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- Botões ----------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(52.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.Ink) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Palette.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = modifier.height(52.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Barra fixa de ações no rodapé de formulários. */
@Composable
fun BottomActions(content: @Composable RowScope.() -> Unit) {
    Surface(color = Palette.Surface) {
        Column {
            HorizontalDivider(color = Palette.LineSoft)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

// ---------- Elementos visuais ----------

@Composable
fun ColorDot(color: Int, size: Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
    )
}

@Composable
fun CardSwatch(color: Int, width: Dp = 40.dp, height: Dp = 28.dp) {
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(color))
    )
}

@Composable
fun Avatar(name: String, color: Int, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

/** Selo com as iniciais da categoria, nas cores dela. */
@Composable
fun CategoryBadge(category: CategoryEntity?, size: Dp = 36.dp) {
    val color = category?.let { Color(it.color) } ?: Palette.Muted
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            category?.let { initials(it.name) } ?: "?",
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

fun initials(name: String): String {
    val small = setOf("de", "da", "do", "das", "dos", "e", "entre")
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.lowercase() !in small }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit, colors: List<Int> = Palette.choices) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(if (isSel) 3.dp else 0.dp, if (isSel) Palette.Ink else Color.Transparent, CircleShape)
                    .padding(if (isSel) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .semantics { this.selected = isSel; contentDescription = "Cor" }
                    .clickable(role = Role.RadioButton) { onSelect(c) }
            )
        }
    }
}

@Composable
fun AmountText(
    cents: Long,
    type: TxType?,
    modifier: Modifier = Modifier,
    fontSize: Int = 14,
    color: Color? = null,
) {
    Text(
        if (type == null) Money.format(cents) else Money.signed(cents, type),
        modifier = modifier,
        color = color ?: type?.let { Palette.amount(it) } ?: Palette.Ink,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
        style = NumberStyle,
        maxLines = 1,
    )
}

// ---------- Campos ----------

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    error: String? = null,
) {
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = placeholder?.let { { Text(it, color = Palette.Muted) } },
            prefix = prefix?.let { { Text(it, color = Palette.Muted) } },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Palette.Border,
                focusedBorderColor = Palette.Accent,
                unfocusedContainerColor = Palette.Surface,
                focusedContainerColor = Palette.Surface,
            ),
        )
    }
}

@Composable
fun DateField(label: String, date: LocalDate, onDate: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotEmpty()) FieldLabel(label)
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            color = Palette.Surface,
            border = BorderStroke(1.dp, Palette.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .height(56.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Dates.full(date), style = MaterialTheme.typography.bodyLarge.merge(NumberStyle), modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.CalendarMonth, null, tint = Palette.Muted)
            }
        }
    }
    if (open) {
        DatePickerDialogFor(date, onDismiss = { open = false }, onPick = { open = false; onDate(it) })
    }
}

@Composable
fun DatePickerDialogFor(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = state)
    }
}

// ---------- Diálogos ----------

/** Folha inferior com título, usada pelos formulários de cadastro. */
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            content()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Excluir",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Palette.Surface,
    )
}

fun monthLabel(ym: YearMonth): String = Dates.monthTitle(ym)

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
