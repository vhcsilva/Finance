package app.financas.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.financas.FinancasApp
import app.financas.data.FinanceRepository

/** Cria ViewModels recebendo o repositório do app e os argumentos de navegação. */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (FinanceRepository, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancasApp
        create(app.container.repository, createSavedStateHandle())
    }
}

/** Snackbar compartilhado por todas as telas. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState não fornecido") }
