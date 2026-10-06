package app.financas.data

import androidx.room.withTransaction
import app.financas.domain.Billing
import app.financas.domain.Money
import app.financas.domain.TxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth

/** Compra vinda da importação de OFX, com as pessoas que vão dividi-la. */
data class ImportedPurchase(val purchase: CardPurchaseEntity, val personIds: Set<Long>)

class FinanceRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    private data class Registry(
        val accounts: List<AccountEntity>,
        val cards: List<CardEntity>,
        val categories: List<CategoryEntity>,
        val people: List<PersonEntity>,
        val recurring: List<RecurringEntity>,
    )

    private data class Ledger(
        val transactions: List<AccountTransactionEntity>,
        val purchases: List<CardPurchaseEntity>,
        val installments: List<CardInstallmentEntity>,
        val splits: List<PurchaseSplitEntity>,
        val settlements: List<SettlementEntity>,
    )

    val snapshot: Flow<Snapshot> = combine(
        combine(dao.accounts(), dao.cards(), dao.categories(), dao.people(), dao.recurring()) { a, c, cat, p, r ->
            Registry(a, c, cat, p, r)
        },
        combine(dao.accountTransactions(), dao.purchases(), dao.installments(), dao.splits(), dao.settlements()) { t, p, i, s, st ->
            Ledger(t, p, i, s, st)
        },
    ) { r, l ->
        Snapshot(
            accounts = r.accounts, cards = r.cards, categories = r.categories, people = r.people, recurring = r.recurring,
            transactions = l.transactions, purchases = l.purchases, installments = l.installments,
            splits = l.splits, settlements = l.settlements,
        )
    }

    /** Cria "Eu" e as categorias padrão na primeira execução. */
    suspend fun ensureSeed() {
        if (dao.peopleCount() == 0) {
            dao.upsertPerson(PersonEntity(name = "Eu", color = 0xFF0E6B4E.toInt(), isMe = true))
        }
        if (dao.categoryCount() == 0) {
            DefaultCategories.all.forEach { dao.upsertCategory(it) }
        }
    }

    // ---- Cadastros ----
    suspend fun saveAccount(item: AccountEntity) = dao.upsertAccount(item)
    suspend fun deleteAccount(item: AccountEntity) = dao.deleteAccount(item)
    suspend fun saveCategory(item: CategoryEntity) = dao.upsertCategory(item)
    suspend fun deleteCategory(item: CategoryEntity) = dao.deleteCategory(item)
    suspend fun savePerson(item: PersonEntity) = dao.upsertPerson(item)
    suspend fun deletePerson(item: PersonEntity) {
        if (!item.isMe) dao.deletePerson(item)
    }
    suspend fun saveRecurring(item: RecurringEntity) = dao.upsertRecurring(item)
    suspend fun deleteRecurring(item: RecurringEntity) = dao.deleteRecurring(item)
    suspend fun recurring(id: Long) = dao.recurringItem(id)
    suspend fun deleteCard(item: CardEntity) = dao.deleteCard(item)

    /** Salva o cartão; se o dia de fechamento mudar, recalcula as faturas das compras lançadas à mão. */
    suspend fun saveCard(item: CardEntity) = db.withTransaction {
        val previous = if (item.id != 0L) dao.card(item.id) else null
        dao.upsertCard(item)
        if (previous != null && previous.closingDay != item.closingDay) {
            dao.purchasesOfCard(item.id).filter { it.firstInstallment == 1 && it.fitId == null }.forEach { p ->
                val updated = p.copy(firstInvoice = Billing.invoiceFor(p.date, item.closingDay))
                dao.upsertPurchase(updated)
                regenerateInstallments(updated)
            }
        }
    }

    // ---- Lançamentos ----
    suspend fun accountTransaction(id: Long) = dao.accountTransaction(id)
    suspend fun purchase(id: Long) = dao.purchase(id)
    suspend fun splitsOf(purchaseId: Long) = dao.splitsOf(purchaseId)

    suspend fun saveAccountTransaction(item: AccountTransactionEntity) {
        dao.upsertAccountTransaction(item)
    }

    suspend fun deleteAccountTransaction(id: Long) = dao.deleteAccountTransaction(id)

    suspend fun deletePurchase(id: Long) = dao.deletePurchase(id)

    /**
     * Salva uma compra no cartão, gerando as parcelas nas faturas corretas.
     * [shares] = pessoa → parte do valor total (vazio = tudo para "Eu").
     */
    suspend fun savePurchase(purchase: CardPurchaseEntity, shares: Map<Long, Long>): Long = db.withTransaction {
        val card = dao.card(purchase.cardId) ?: error("Cartão não encontrado")
        val firstInvoice = if (purchase.firstInstallment == 1) {
            Billing.invoiceFor(purchase.date, card.closingDay)
        } else purchase.firstInvoice
        val toSave = purchase.copy(firstInvoice = firstInvoice)
        val inserted = dao.upsertPurchase(toSave)
        val id = if (purchase.id != 0L) purchase.id else inserted
        val saved = toSave.copy(id = id)
        regenerateInstallments(saved)
        dao.deleteSplitsOf(id)
        dao.insertSplits(shares.filter { it.value > 0 }.map { PurchaseSplitEntity(id, it.key, it.value) })
        id
    }

    private suspend fun regenerateInstallments(p: CardPurchaseEntity) {
        dao.deleteInstallmentsOf(p.id)
        dao.insertInstallments(
            Billing.installments(p.totalAmount, p.installments, p.firstInvoice, p.firstInstallment).map {
                CardInstallmentEntity(purchaseId = p.id, cardId = p.cardId, number = it.number, amount = it.amount, invoice = it.invoice)
            }
        )
    }

    suspend fun setSettled(personId: Long, invoice: YearMonth, settled: Boolean) {
        val item = SettlementEntity(personId, invoice)
        if (settled) dao.insertSettlement(item) else dao.deleteSettlement(item)
    }

    // ---- Importação OFX ----
    suspend fun importIntoAccount(account: AccountEntity, items: List<AccountTransactionEntity>, ofxAccountId: String?) =
        db.withTransaction {
            dao.insertAccountTransactions(items.map { it.copy(id = 0, accountId = account.id) })
            if (ofxAccountId != null && account.ofxAccountId != ofxAccountId) {
                dao.upsertAccount(account.copy(ofxAccountId = ofxAccountId))
            }
        }

    suspend fun importIntoCard(card: CardEntity, items: List<ImportedPurchase>, ofxAccountId: String?) =
        db.withTransaction {
            items.forEach { item ->
                val p = item.purchase.copy(id = 0, cardId = card.id)
                val id = dao.upsertPurchase(p)
                regenerateInstallments(p.copy(id = id))
                if (item.personIds.isNotEmpty()) {
                    val people = item.personIds.toList()
                    val parts = Money.splitEven(p.totalAmount, people.size)
                    dao.insertSplits(people.zip(parts).map { (person, share) -> PurchaseSplitEntity(id, person, share) })
                }
            }
            if (ofxAccountId != null && card.ofxAccountId != ofxAccountId) {
                dao.upsertCard(card.copy(ofxAccountId = ofxAccountId))
            }
        }

    // ---- Backup ----
    suspend fun replaceAll(data: Snapshot) = db.withTransaction {
        dao.clearSettlements(); dao.clearSplits(); dao.clearInstallments(); dao.clearPurchases()
        dao.clearAccountTransactions(); dao.clearRecurring(); dao.clearPeople(); dao.clearCategories()
        dao.clearCards(); dao.clearAccounts()
        dao.restoreAccounts(data.accounts)
        dao.restoreCards(data.cards)
        dao.restoreCategories(data.categories)
        dao.restorePeople(data.people)
        dao.restoreRecurring(data.recurring)
        dao.restoreAccountTransactions(data.transactions)
        dao.restorePurchases(data.purchases)
        dao.restoreInstallments(data.installments)
        dao.restoreSplits(data.splits)
        dao.restoreSettlements(data.settlements)
    }
}

object DefaultCategories {
    private fun out(name: String, color: Long, ignore: Boolean = false) =
        CategoryEntity(name = name, type = TxType.OUT, color = color.toInt(), ignoreInReports = ignore)

    private fun inc(name: String, color: Long, ignore: Boolean = false) =
        CategoryEntity(name = name, type = TxType.IN, color = color.toInt(), ignoreInReports = ignore)

    const val TRANSFER = "Transferência"
    const val INVOICE_PAYMENT = "Pagamento de fatura"
    const val OWN_TRANSFER = "Entre minhas contas"

    val all = listOf(
        out("Moradia", 0xFF0E6B4E),
        out("Alimentação", 0xFFB45309),
        out("Transporte", 0xFF0F766E),
        out("Saúde", 0xFFBE185D),
        out("Lazer", 0xFF6D28D9),
        out("Compras", 0xFF1D4ED8),
        out("Assinaturas", 0xFFA16207),
        out("Educação", 0xFF4D7C0F),
        out(TRANSFER, 0xFF475569),
        out(OWN_TRANSFER, 0xFF64748B, ignore = true),
        out(INVOICE_PAYMENT, 0xFF334155, ignore = true),
        out("Outros", 0xFF57534E),
        inc("Salário", 0xFF1D4ED8),
        inc("Reembolso", 0xFF0E7490),
        inc(TRANSFER, 0xFF475569),
        inc(OWN_TRANSFER, 0xFF64748B, ignore = true),
        inc("Estorno", 0xFF0F766E),
        inc("Outros", 0xFF57534E),
    )
}
