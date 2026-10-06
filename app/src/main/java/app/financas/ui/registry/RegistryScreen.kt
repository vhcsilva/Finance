package app.financas.ui.registry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.financas.domain.Money
import app.financas.domain.Recurrence
import app.financas.ui.LocalSnackbar
import app.financas.ui.appViewModelFactory
import app.financas.ui.components.Caption
import app.financas.ui.components.ConfirmDialog
import app.financas.ui.components.Panel
import app.financas.ui.components.RowDivider
import app.financas.ui.components.ScreenHeader
import app.financas.ui.navigation.Routes
import app.financas.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** ViewModel dos cadastros + exibição das mensagens no snackbar. */
@Composable
internal fun registryViewModel(): RegistryViewModel {
    val vm: RegistryViewModel = viewModel(factory = appViewModelFactory { repo, _ -> RegistryViewModel(repo) })
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    return vm
}

@Composable
fun RegistryScreen(navigate: (String) -> Unit) {
    val vm = registryViewModel()
    val dataState by vm.data.collectAsStateWithLifecycle()
    val data = dataState ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = vm.backupJson()
        if (uri != null && json != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }.isSuccess
                }
                vm.notify(if (ok) "Backup salvo" else "Não foi possível salvar o backup")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                }
                if (text == null) vm.notify("Não foi possível ler o arquivo") else pendingRestore = text
            }
        }
    }

    val monthly = data.recurring.sumOf { Recurrence.monthlyEstimate(it.amount, it.frequency) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Cadastros")
        Panel(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            HubRow(Icons.Outlined.AccountBalance, Color(0xFFEEE4FB), Color(0xFF5B21B6), "Contas", plural(data.accounts.size, "conta", "contas")) { navigate(Routes.ACCOUNTS) }
            RowDivider()
            HubRow(Icons.Outlined.CreditCard, Palette.Track, Color(0xFF1F2937), "Cartões de crédito", plural(data.cards.size, "cartão", "cartões")) { navigate(Routes.CARDS) }
            RowDivider()
            HubRow(
                Icons.Outlined.Repeat, Palette.OutSoft, Palette.Warn, "Contas recorrentes",
                plural(data.recurring.size, "ativa", "ativas") + if (monthly > 0) " · ${Money.compact(monthly)}/mês" else "",
            ) { navigate(Routes.RECURRING) }
            RowDivider()
            HubRow(Icons.Outlined.Label, Palette.AccentSoft, Palette.Accent, "Categorias", plural(data.categories.size, "categoria", "categorias")) { navigate(Routes.CATEGORIES) }
            RowDivider()
            HubRow(Icons.Outlined.Group, Palette.InSoft, Palette.In, "Pessoas", data.people.joinToString(", ") { it.name }) { navigate(Routes.PEOPLE) }
        }

        Caption("Dados")
        Panel(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            HubRow(Icons.Outlined.FileUpload, Palette.LineSoft, Palette.InkSoft, "Importar OFX", "Extratos de conta e fatura") { navigate(Routes.IMPORT) }
            RowDivider()
            HubRow(Icons.Outlined.FileDownload, Palette.LineSoft, Palette.InkSoft, "Exportar backup", "Salva todos os dados em um arquivo") {
                exportLauncher.launch("financas-backup-${LocalDate.now()}.json")
            }
            RowDivider()
            HubRow(Icons.Outlined.Restore, Palette.LineSoft, Palette.InkSoft, "Restaurar backup", "Substitui os dados atuais") {
                restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
        Text(
            "Seus dados ficam somente neste aparelho. Faça backups regulares para não perdê-los ao trocar de celular.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }

    pendingRestore?.let { text ->
        ConfirmDialog(
            title = "Restaurar backup?",
            text = "Todos os dados atuais serão substituídos pelos do arquivo. Essa ação não pode ser desfeita.",
            confirmLabel = "Restaurar",
            onConfirm = {
                pendingRestore = null
                vm.restore(text)
            },
            onDismiss = { pendingRestore = null },
        )
    }
}

@Composable
private fun HubRow(icon: ImageVector, bg: Color, fg: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Palette.Muted)
    }
}

internal fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
