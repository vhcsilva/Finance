package app.financas.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.AccountEntity
import app.financas.data.CardEntity
import app.financas.data.FinanceRepository
import app.financas.data.LedgerItem
import app.financas.data.RecurringEntity
import app.financas.data.Snapshot
import app.financas.domain.Billing
import app.financas.domain.Recurrence
import app.financas.domain.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class AccountSummary(val account: AccountEntity, val balance: Long)
data class CardSummary(val card: CardEntity, val invoice: YearMonth, val total: Long, val closing: LocalDate, val due: LocalDate)
data class UpcomingItem(val recurring: RecurringEntity, val date: LocalDate)

data class HomeState(
    val month: YearMonth,
    val totalBalance: Long,
    val monthIncome: Long,
    val monthExpense: Long,
    val invoicesTotal: Long,
    val accounts: List<AccountSummary>,
    val cards: List<CardSummary>,
    val upcoming: List<UpcomingItem>,
    val recent: List<LedgerItem>,
    val isEmpty: Boolean,
)

class HomeViewModel(repository: FinanceRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HomeState?> = combine(repository.snapshot, month) { s, m -> build(s, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftMonth(delta: Long) = month.update { it.plusMonths(delta) }

    private fun build(s: Snapshot, m: YearMonth): HomeState {
        val today = LocalDate.now()
        val isCurrent = m == YearMonth.from(today)
        val monthTx = s.transactions.filter { YearMonth.from(it.date) == m && !s.isIgnored(it.categoryId) }

        val cards = s.cards.map { card ->
            // No mês atual mostra a fatura em aberto; nos demais, a fatura que fecha naquele mês.
            val invoice = if (isCurrent) Billing.invoiceFor(today, card.closingDay) else m
            CardSummary(
                card = card,
                invoice = invoice,
                total = s.invoiceTotal(card.id, invoice),
                closing = Billing.closingDate(invoice, card.closingDay),
                due = Billing.dueDate(invoice, card.closingDay, card.dueDay),
            )
        }

        val upcoming = s.recurring.map { r -> UpcomingItem(r, Recurrence.next(r.anchorDate, r.frequency, today)) }
            .filter { !it.date.isAfter(today.plusDays(45)) }
            .sortedBy { it.date }
            .take(4)

        val recent = s.ledger.filter { !it.date.isAfter(today) }.take(5)

        return HomeState(
            month = m,
            totalBalance = s.totalBalance,
            monthIncome = monthTx.filter { it.type == TxType.IN }.sumOf { it.amount },
            monthExpense = monthTx.filter { it.type == TxType.OUT }.sumOf { it.amount },
            invoicesTotal = cards.sumOf { it.total },
            accounts = s.accounts.map { AccountSummary(it, s.accountBalance(it.id)) },
            cards = cards,
            upcoming = upcoming,
            recent = recent,
            isEmpty = s.accounts.isEmpty() && s.cards.isEmpty(),
        )
    }
}
