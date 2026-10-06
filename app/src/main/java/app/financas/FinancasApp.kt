package app.financas

import android.app.Application
import android.content.Context
import app.financas.data.AppDatabase
import app.financas.data.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Injeção de dependências manual: um único banco e repositório para o app inteiro. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase = AppDatabase.build(context)
    val repository = FinanceRepository(database)
}

class FinancasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.repository.ensureSeed() }
    }
}
