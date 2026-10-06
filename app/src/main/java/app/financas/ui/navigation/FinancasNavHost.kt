@file:OptIn(ExperimentalLayoutApi::class)

package app.financas.ui.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.financas.ui.LocalSnackbar
import app.financas.ui.edit.TransactionEditScreen
import app.financas.ui.home.HomeScreen
import app.financas.ui.importer.ImportScreen
import app.financas.ui.registry.AccountsScreen
import app.financas.ui.registry.CardsScreen
import app.financas.ui.registry.CategoriesScreen
import app.financas.ui.registry.PeopleScreen
import app.financas.ui.registry.RecurringScreen
import app.financas.ui.registry.RegistryScreen
import app.financas.ui.reports.AnnualScreen
import app.financas.ui.reports.ReportsScreen
import app.financas.ui.theme.Palette
import app.financas.ui.transactions.TransactionsScreen

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val REPORTS = "reports"
    const val REGISTRY = "registry"
    const val ACCOUNTS = "accounts"
    const val CARDS = "cards"
    const val RECURRING = "recurring"
    const val CATEGORIES = "categories"
    const val PEOPLE = "people"
    const val IMPORT = "import"
    const val ANNUAL = "annual"
    const val EDIT = "edit?mode={mode}&id={id}&recurringId={recurringId}"

    const val MODE_ACCOUNT = "account"
    const val MODE_CARD = "card"

    fun edit(mode: String = MODE_ACCOUNT, id: Long = -1L, recurringId: Long = -1L) =
        "edit?mode=$mode&id=$id&recurringId=$recurringId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Outlined.Home),
    Tab(Routes.TRANSACTIONS, "Lançamentos", Icons.Outlined.SwapVert),
    Tab(Routes.REPORTS, "Relatórios", Icons.Outlined.BarChart),
    Tab(Routes.REGISTRY, "Cadastros", Icons.Outlined.GridView),
)

@Composable
fun FinancasNavHost() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = Palette.Ground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = Palette.Surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Palette.Accent,
                                    selectedTextColor = Palette.Accent,
                                    indicatorColor = Palette.AccentSoft,
                                    unselectedIconColor = Palette.Muted,
                                    unselectedTextColor = Palette.Muted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val go: (String) -> Unit = { nav.navigate(it) }
            val back: () -> Unit = { nav.popBackStack() }
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                composable(Routes.HOME) { HomeScreen(navigate = go) }
                composable(Routes.TRANSACTIONS) { TransactionsScreen(navigate = go) }
                composable(Routes.REPORTS) { ReportsScreen(onOpenAnnual = { go(Routes.ANNUAL) }) }
                composable(Routes.ANNUAL) { AnnualScreen(onBack = back) }
                composable(Routes.REGISTRY) { RegistryScreen(navigate = go) }
                composable(Routes.ACCOUNTS) { AccountsScreen(onBack = back) }
                composable(Routes.CARDS) { CardsScreen(onBack = back) }
                composable(Routes.RECURRING) { RecurringScreen(onBack = back, navigate = go) }
                composable(Routes.CATEGORIES) { CategoriesScreen(onBack = back) }
                composable(Routes.PEOPLE) { PeopleScreen(onBack = back) }
                composable(Routes.IMPORT) { ImportScreen(onBack = back, navigate = go) }
                composable(
                    Routes.EDIT,
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType; defaultValue = Routes.MODE_ACCOUNT },
                        navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("recurringId") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { TransactionEditScreen(onDone = back) }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
