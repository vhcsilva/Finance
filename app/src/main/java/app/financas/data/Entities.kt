package app.financas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.financas.domain.Frequency
import app.financas.domain.TxType
import java.time.LocalDate
import java.time.YearMonth

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val initialBalance: Long = 0,
    /** Identificador da conta no OFX (para sugerir o destino em próximas importações). */
    val ofxAccountId: String? = null,
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val closingDay: Int,
    val dueDay: Int,
    val ofxAccountId: String? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxType,
    val color: Int,
    /** Não entra em relatórios nem totais (ex.: pagamento de fatura, transferência entre contas próprias). */
    val ignoreInReports: Boolean = false,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val isMe: Boolean = false,
)

@Entity(
    tableName = "recurring",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("categoryId")],
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val frequency: Frequency,
    /** Primeira ocorrência; define o dia da semana/mês/ano em que a conta se repete. */
    val anchorDate: LocalDate,
    val categoryId: Long? = null,
)

@Entity(
    tableName = "account_transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("date")],
)
data class AccountTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val description: String,
    /** Sempre positivo; a direção vem de [type]. */
    val amount: Long,
    val type: TxType,
    val date: LocalDate,
    val categoryId: Long? = null,
    val notes: String? = null,
    val fitId: String? = null,
    val importedMemo: String? = null,
)

@Entity(
    tableName = "card_purchases",
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("cardId"), Index("categoryId")],
)
data class CardPurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val description: String,
    /** Valor total da compra (todas as parcelas), sempre positivo. */
    val totalAmount: Long,
    /** OUT = compra; IN = estorno/crédito na fatura. */
    val type: TxType = TxType.OUT,
    val date: LocalDate,
    val installments: Int = 1,
    /** Primeira parcela registrada (> 1 quando importada no meio do parcelamento). */
    val firstInstallment: Int = 1,
    /** Fatura da primeira parcela registrada. */
    val firstInvoice: YearMonth,
    val categoryId: Long? = null,
    val fitId: String? = null,
    val importedMemo: String? = null,
)

@Entity(
    tableName = "card_installments",
    foreignKeys = [ForeignKey(entity = CardPurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("purchaseId"), Index("invoice")],
)
data class CardInstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val cardId: Long,
    val number: Int,
    val amount: Long,
    val invoice: YearMonth,
)

/** Quanto do total da compra cabe a cada pessoa. Sem linhas = tudo é "Eu". */
@Entity(
    tableName = "purchase_splits",
    primaryKeys = ["purchaseId", "personId"],
    foreignKeys = [
        ForeignKey(entity = CardPurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId")],
)
data class PurchaseSplitEntity(
    val purchaseId: Long,
    val personId: Long,
    val share: Long,
)

/** Marca que uma pessoa já acertou a parte dela nas faturas de um mês. */
@Entity(
    tableName = "settlements",
    primaryKeys = ["personId", "invoice"],
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
)
data class SettlementEntity(
    val personId: Long,
    val invoice: YearMonth,
)
