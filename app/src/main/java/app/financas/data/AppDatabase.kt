package app.financas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        CardEntity::class,
        CategoryEntity::class,
        PersonEntity::class,
        RecurringEntity::class,
        AccountTransactionEntity::class,
        CardPurchaseEntity::class,
        CardInstallmentEntity::class,
        PurchaseSplitEntity::class,
        SettlementEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "financas.db")
                .build()
    }
}
