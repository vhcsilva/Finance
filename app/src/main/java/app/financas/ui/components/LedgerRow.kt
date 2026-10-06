package app.financas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.financas.data.LedgerItem
import app.financas.ui.theme.Palette

/** Linha de lançamento: categoria, descrição, origem e valor. */
@Composable
fun LedgerRow(item: LedgerItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(item.category)
        Column(Modifier.weight(1f)) {
            val title = if (item.installmentLabel != null) "${item.description} ${item.installmentLabel}" else item.description
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorDot(item.sourceColor, 8.dp)
                val meta = listOfNotNull(item.sourceName, item.category?.name, item.peopleLabel).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AmountText(item.amount, item.type, color = if (item.ignored) Palette.Muted else null)
    }
}
