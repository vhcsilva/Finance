package app.financas.ui.registry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.AccountEntity
import app.financas.data.Backup
import app.financas.data.CardEntity
import app.financas.data.CategoryEntity
import app.financas.data.FinanceRepository
import app.financas.data.PersonEntity
import app.financas.data.RecurringEntity
import app.financas.data.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Um único ViewModel para todos os cadastros (são telas simples sobre os mesmos dados). */
class RegistryViewModel(private val repository: FinanceRepository) : ViewModel() {

    val data: StateFlow<Snapshot?> = repository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    private fun launchSafe(done: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (done != null) _message.value = done
            } catch (e: Exception) {
                _message.value = e.message ?: "Algo deu errado."
            }
        }
    }

    fun saveAccount(item: AccountEntity) = launchSafe { repository.saveAccount(item) }
    fun deleteAccount(item: AccountEntity) = launchSafe("Conta excluída") { repository.deleteAccount(item) }
    fun saveCard(item: CardEntity) = launchSafe { repository.saveCard(item) }
    fun deleteCard(item: CardEntity) = launchSafe("Cartão excluído") { repository.deleteCard(item) }
    fun saveCategory(item: CategoryEntity) = launchSafe { repository.saveCategory(item) }
    fun deleteCategory(item: CategoryEntity) = launchSafe("Categoria excluída") { repository.deleteCategory(item) }
    fun savePerson(item: PersonEntity) = launchSafe { repository.savePerson(item) }
    fun deletePerson(item: PersonEntity) = launchSafe("Pessoa excluída") { repository.deletePerson(item) }
    fun saveRecurring(item: RecurringEntity) = launchSafe { repository.saveRecurring(item) }
    fun deleteRecurring(item: RecurringEntity) = launchSafe("Conta recorrente excluída") { repository.deleteRecurring(item) }

    /** Gera o JSON do backup a partir dos dados atuais. */
    fun backupJson(): String? = data.value?.let(Backup::toJson)

    fun restore(json: String?) = launchSafe("Backup restaurado") {
        requireNotNull(json) { "Não foi possível ler o arquivo." }
        val snapshot = Backup.fromJson(json)
        repository.replaceAll(snapshot)
        repository.ensureSeed()
    }

    fun notify(text: String) {
        _message.value = text
    }
}
