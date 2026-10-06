package app.financas.data

import app.financas.domain.Frequency
import app.financas.domain.TxType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Exporta e lê o backup completo em JSON (arquivo escolhido pelo usuário). */
object Backup {
    private const val VERSION = 1

    fun toJson(s: Snapshot): String {
        val root = JSONObject()
        root.put("app", "financas")
        root.put("version", VERSION)
        root.put("exportedAt", LocalDate.now().toString())
        root.put("accounts", arr(s.accounts) {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)
                .put("initialBalance", it.initialBalance).putOpt("ofxAccountId", it.ofxAccountId)
        })
        root.put("cards", arr(s.cards) {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)
                .put("closingDay", it.closingDay).put("dueDay", it.dueDay).putOpt("ofxAccountId", it.ofxAccountId)
        })
        root.put("categories", arr(s.categories) {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name).put("color", it.color)
                .put("ignoreInReports", it.ignoreInReports)
        })
        root.put("people", arr(s.people) {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("isMe", it.isMe)
        })
        root.put("recurring", arr(s.recurring) {
            JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)
                .put("frequency", it.frequency.name).put("anchorDate", it.anchorDate.toString())
                .putOpt("categoryId", it.categoryId)
        })
        root.put("transactions", arr(s.transactions) {
            JSONObject().put("id", it.id).put("accountId", it.accountId).put("description", it.description)
                .put("amount", it.amount).put("type", it.type.name).put("date", it.date.toString())
                .putOpt("categoryId", it.categoryId).putOpt("notes", it.notes).putOpt("fitId", it.fitId)
                .putOpt("importedMemo", it.importedMemo)
        })
        root.put("purchases", arr(s.purchases) {
            JSONObject().put("id", it.id).put("cardId", it.cardId).put("description", it.description)
                .put("totalAmount", it.totalAmount).put("type", it.type.name).put("date", it.date.toString())
                .put("installments", it.installments).put("firstInstallment", it.firstInstallment)
                .put("firstInvoice", it.firstInvoice.toString()).putOpt("categoryId", it.categoryId)
                .putOpt("fitId", it.fitId).putOpt("importedMemo", it.importedMemo)
        })
        root.put("installments", arr(s.installments) {
            JSONObject().put("id", it.id).put("purchaseId", it.purchaseId).put("cardId", it.cardId)
                .put("number", it.number).put("amount", it.amount).put("invoice", it.invoice.toString())
        })
        root.put("splits", arr(s.splits) {
            JSONObject().put("purchaseId", it.purchaseId).put("personId", it.personId).put("share", it.share)
        })
        root.put("settlements", arr(s.settlements) {
            JSONObject().put("personId", it.personId).put("invoice", it.invoice.toString())
        })
        return root.toString(2)
    }

    /** Lê um backup; lança exceção com mensagem amigável se o arquivo não for válido. */
    fun fromJson(text: String): Snapshot {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("O arquivo não é um backup válido.")
        }
        require(root.optString("app") == "financas") { "O arquivo não é um backup deste app." }
        return Snapshot(
            accounts = list(root, "accounts") {
                AccountEntity(it.getLong("id"), it.getString("name"), it.getInt("color"),
                    it.optLong("initialBalance", 0), it.optStr("ofxAccountId"))
            },
            cards = list(root, "cards") {
                CardEntity(it.getLong("id"), it.getString("name"), it.getInt("color"),
                    it.getInt("closingDay"), it.getInt("dueDay"), it.optStr("ofxAccountId"))
            },
            categories = list(root, "categories") {
                CategoryEntity(it.getLong("id"), it.getString("name"), TxType.valueOf(it.getString("type")),
                    it.getInt("color"), it.optBoolean("ignoreInReports", false))
            },
            people = list(root, "people") {
                PersonEntity(it.getLong("id"), it.getString("name"), it.getInt("color"), it.optBoolean("isMe", false))
            },
            recurring = list(root, "recurring") {
                RecurringEntity(it.getLong("id"), it.getString("name"), it.getLong("amount"),
                    Frequency.valueOf(it.getString("frequency")), LocalDate.parse(it.getString("anchorDate")),
                    it.optLongOrNull("categoryId"))
            },
            transactions = list(root, "transactions") {
                AccountTransactionEntity(it.getLong("id"), it.getLong("accountId"), it.getString("description"),
                    it.getLong("amount"), TxType.valueOf(it.getString("type")), LocalDate.parse(it.getString("date")),
                    it.optLongOrNull("categoryId"), it.optStr("notes"), it.optStr("fitId"), it.optStr("importedMemo"))
            },
            purchases = list(root, "purchases") {
                CardPurchaseEntity(it.getLong("id"), it.getLong("cardId"), it.getString("description"),
                    it.getLong("totalAmount"), TxType.valueOf(it.getString("type")), LocalDate.parse(it.getString("date")),
                    it.getInt("installments"), it.getInt("firstInstallment"), YearMonth.parse(it.getString("firstInvoice")),
                    it.optLongOrNull("categoryId"), it.optStr("fitId"), it.optStr("importedMemo"))
            },
            installments = list(root, "installments") {
                CardInstallmentEntity(it.getLong("id"), it.getLong("purchaseId"), it.getLong("cardId"),
                    it.getInt("number"), it.getLong("amount"), YearMonth.parse(it.getString("invoice")))
            },
            splits = list(root, "splits") {
                PurchaseSplitEntity(it.getLong("purchaseId"), it.getLong("personId"), it.getLong("share"))
            },
            settlements = list(root, "settlements") {
                SettlementEntity(it.getLong("personId"), YearMonth.parse(it.getString("invoice")))
            },
        )
    }

    private fun <T> arr(items: List<T>, map: (T) -> JSONObject): JSONArray =
        JSONArray().apply { items.forEach { put(map(it)) } }

    private fun <T> list(root: JSONObject, key: String, map: (JSONObject) -> T): List<T> {
        val a = root.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { map(a.getJSONObject(it)) }
    }

    private fun JSONObject.optStr(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}
