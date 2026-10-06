package app.financas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // ---- Leitura reativa ----
    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE")
    fun accounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM cards ORDER BY name COLLATE NOCASE")
    fun cards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM people ORDER BY isMe DESC, name COLLATE NOCASE")
    fun people(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM recurring ORDER BY name COLLATE NOCASE")
    fun recurring(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM account_transactions ORDER BY date DESC, id DESC")
    fun accountTransactions(): Flow<List<AccountTransactionEntity>>

    @Query("SELECT * FROM card_purchases ORDER BY date DESC, id DESC")
    fun purchases(): Flow<List<CardPurchaseEntity>>

    @Query("SELECT * FROM card_installments")
    fun installments(): Flow<List<CardInstallmentEntity>>

    @Query("SELECT * FROM purchase_splits")
    fun splits(): Flow<List<PurchaseSplitEntity>>

    @Query("SELECT * FROM settlements")
    fun settlements(): Flow<List<SettlementEntity>>

    // ---- Leitura pontual ----
    @Query("SELECT * FROM account_transactions WHERE id = :id")
    suspend fun accountTransaction(id: Long): AccountTransactionEntity?

    @Query("SELECT * FROM card_purchases WHERE id = :id")
    suspend fun purchase(id: Long): CardPurchaseEntity?

    @Query("SELECT * FROM purchase_splits WHERE purchaseId = :purchaseId")
    suspend fun splitsOf(purchaseId: Long): List<PurchaseSplitEntity>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun recurringItem(id: Long): RecurringEntity?

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun card(id: Long): CardEntity?

    @Query("SELECT * FROM card_installments WHERE purchaseId = :purchaseId ORDER BY number")
    suspend fun installmentsOf(purchaseId: Long): List<CardInstallmentEntity>

    @Query("SELECT * FROM card_purchases WHERE cardId = :cardId")
    suspend fun purchasesOfCard(cardId: Long): List<CardPurchaseEntity>

    @Query("SELECT COUNT(*) FROM people")
    suspend fun peopleCount(): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    // ---- Escrita ----
    @Upsert suspend fun upsertAccount(item: AccountEntity): Long
    @Upsert suspend fun upsertCard(item: CardEntity): Long
    @Upsert suspend fun upsertCategory(item: CategoryEntity): Long
    @Upsert suspend fun upsertPerson(item: PersonEntity): Long
    @Upsert suspend fun upsertRecurring(item: RecurringEntity): Long
    @Upsert suspend fun upsertAccountTransaction(item: AccountTransactionEntity): Long
    @Upsert suspend fun upsertPurchase(item: CardPurchaseEntity): Long

    @Insert suspend fun insertAccountTransactions(items: List<AccountTransactionEntity>)
    @Insert suspend fun insertInstallments(items: List<CardInstallmentEntity>)
    @Insert suspend fun insertSplits(items: List<PurchaseSplitEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettlement(item: SettlementEntity)

    @Delete suspend fun deleteAccount(item: AccountEntity)
    @Delete suspend fun deleteCard(item: CardEntity)
    @Delete suspend fun deleteCategory(item: CategoryEntity)
    @Delete suspend fun deletePerson(item: PersonEntity)
    @Delete suspend fun deleteRecurring(item: RecurringEntity)
    @Delete suspend fun deleteSettlement(item: SettlementEntity)

    @Query("DELETE FROM account_transactions WHERE id = :id")
    suspend fun deleteAccountTransaction(id: Long)

    @Query("DELETE FROM card_purchases WHERE id = :id")
    suspend fun deletePurchase(id: Long)

    @Query("DELETE FROM card_installments WHERE purchaseId = :purchaseId")
    suspend fun deleteInstallmentsOf(purchaseId: Long)

    @Query("DELETE FROM purchase_splits WHERE purchaseId = :purchaseId")
    suspend fun deleteSplitsOf(purchaseId: Long)

    @Query("UPDATE card_installments SET cardId = :cardId WHERE purchaseId = :purchaseId")
    suspend fun moveInstallments(purchaseId: Long, cardId: Long)

    // ---- Backup / restauração ----
    @Query("DELETE FROM settlements") suspend fun clearSettlements()
    @Query("DELETE FROM purchase_splits") suspend fun clearSplits()
    @Query("DELETE FROM card_installments") suspend fun clearInstallments()
    @Query("DELETE FROM card_purchases") suspend fun clearPurchases()
    @Query("DELETE FROM account_transactions") suspend fun clearAccountTransactions()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM cards") suspend fun clearCards()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAccounts(items: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreCards(items: List<CardEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restorePeople(items: List<PersonEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreRecurring(items: List<RecurringEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAccountTransactions(items: List<AccountTransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restorePurchases(items: List<CardPurchaseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreInstallments(items: List<CardInstallmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSplits(items: List<PurchaseSplitEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSettlements(items: List<SettlementEntity>)
}
