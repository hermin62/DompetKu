package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class DompetKuSnapshot(
    val ownerEmail: String = "",
    val currencyCode: String = "IDR",
    val overallMonthlyBudget: Double = 10_000_000.0,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val debts: List<DebtLoanEntity> = emptyList(),
    val bills: List<BillReminderEntity> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val SHEET_SETTINGS = "Ringkasan_Pengaturan"
        const val SHEET_ACCOUNTS = "Akun_Saldo"
        const val SHEET_CATEGORIES = "Kategori_Anggaran"
        const val SHEET_TRANSACTIONS = "Transaksi"
        const val SHEET_DEBTS = "Hutang_Piutang"
        const val SHEET_BILLS = "Tagihan_Berkala"

        val ALL_SHEET_NAMES = listOf(
            SHEET_SETTINGS,
            SHEET_ACCOUNTS,
            SHEET_CATEGORIES,
            SHEET_TRANSACTIONS,
            SHEET_DEBTS,
            SHEET_BILLS
        )
    }
}

object SnapshotSerializer {

    private fun safeNumber(value: Double): Double = if (value.isFinite()) value else 0.0

    fun toJsonString(snapshot: DompetKuSnapshot): String {
        val root = JSONObject()
        root.put("ownerEmail", snapshot.ownerEmail)
        root.put("currencyCode", snapshot.currencyCode)
        root.put("overallMonthlyBudget", safeNumber(snapshot.overallMonthlyBudget))
        root.put("updatedAtMillis", snapshot.updatedAtMillis)

        val accountsArray = JSONArray()
        snapshot.accounts.forEach { acc ->
            accountsArray.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("type", acc.type.name)
                    put("nature", acc.nature.name)
                    put("balance", safeNumber(acc.balance))
                    put("currencyCode", acc.currencyCode)
                    put("colorHex", acc.colorHex)
                    put("isDefault", acc.isDefault)
                }
            )
        }
        root.put("accounts", accountsArray)

        val categoriesArray = JSONArray()
        snapshot.categories.forEach { cat ->
            categoriesArray.put(
                JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("type", cat.type.name)
                    put("iconKey", cat.iconKey)
                    put("colorHex", cat.colorHex)
                    put("expenseGroup", cat.expenseGroup.name)
                    put("monthlyBudget", safeNumber(cat.monthlyBudget))
                    put("isDefault", cat.isDefault)
                }
            )
        }
        root.put("categories", categoriesArray)

        val txArray = JSONArray()
        snapshot.transactions.forEach { tx ->
            txArray.put(
                JSONObject().apply {
                    put("id", tx.id)
                    put("title", tx.title)
                    put("amount", safeNumber(tx.amount))
                    put("type", tx.type.name)
                    put("categoryId", tx.categoryId)
                    put("dateMillis", tx.dateMillis)
                    put("notes", tx.notes)
                    put("currencyCode", tx.currencyCode)
                    put("accountId", tx.accountId)
                    put("toAccountId", tx.toAccountId ?: JSONObject.NULL)
                    put("receiptUri", tx.receiptUri ?: JSONObject.NULL)
                }
            )
        }
        root.put("transactions", txArray)

        val debtsArray = JSONArray()
        snapshot.debts.forEach { debt ->
            debtsArray.put(
                JSONObject().apply {
                    put("id", debt.id)
                    put("personName", debt.personName)
                    put("totalAmount", safeNumber(debt.totalAmount))
                    put("remainingAmount", safeNumber(debt.remainingAmount))
                    put("type", debt.type.name)
                    put("dueDateMillis", debt.dueDateMillis)
                    put("notes", debt.notes)
                    put("phone", debt.phone)
                    put("currencyCode", debt.currencyCode)
                    put("isSettled", debt.isSettled)
                }
            )
        }
        root.put("debts", debtsArray)

        val billsArray = JSONArray()
        snapshot.bills.forEach { bill ->
            billsArray.put(
                JSONObject().apply {
                    put("id", bill.id)
                    put("title", bill.title)
                    put("amount", safeNumber(bill.amount))
                    put("categoryId", bill.categoryId)
                    put("dueDateMillis", bill.dueDateMillis)
                    put("repeatIntervalMonths", bill.repeatIntervalMonths)
                    put("notes", bill.notes)
                    put("currencyCode", bill.currencyCode)
                    put("isPaid", bill.isPaid)
                }
            )
        }
        root.put("bills", billsArray)

        return root.toString()
    }

    fun fromJsonString(json: String): DompetKuSnapshot? {
        return try {
            val root = JSONObject(json)
            val ownerEmail = root.optString("ownerEmail", "")
            val currencyCode = root.optString("currencyCode", "IDR")
            val overallBudget = root.optDouble("overallMonthlyBudget", 10_000_000.0)
            val updatedAt = root.optLong("updatedAtMillis", System.currentTimeMillis())

            val accounts = mutableListOf<AccountEntity>()
            val accArr = root.optJSONArray("accounts")
            if (accArr != null) {
                for (i in 0 until accArr.length()) {
                    val o = accArr.getJSONObject(i)
                    accounts.add(
                        AccountEntity(
                            id = o.getLong("id"),
                            name = o.getString("name"),
                            type = runCatching { AccountType.valueOf(o.getString("type")) }.getOrDefault(AccountType.CASH),
                            nature = runCatching { AccountNature.valueOf(o.getString("nature")) }.getOrDefault(AccountNature.DEBIT),
                            balance = o.optDouble("balance", 0.0),
                            currencyCode = o.optString("currencyCode", currencyCode),
                            colorHex = o.optString("colorHex", "#10B981"),
                            isDefault = o.optBoolean("isDefault", false)
                        )
                    )
                }
            }

            val categories = mutableListOf<CategoryEntity>()
            val catArr = root.optJSONArray("categories")
            if (catArr != null) {
                for (i in 0 until catArr.length()) {
                    val o = catArr.getJSONObject(i)
                    categories.add(
                        CategoryEntity(
                            id = o.getLong("id"),
                            name = o.getString("name"),
                            type = runCatching { TransactionType.valueOf(o.getString("type")) }.getOrDefault(TransactionType.EXPENSE),
                            iconKey = o.optString("iconKey", "category"),
                            colorHex = o.optString("colorHex", "#0D9488"),
                            expenseGroup = runCatching { ExpenseGroup.valueOf(o.getString("expenseGroup")) }.getOrDefault(ExpenseGroup.NEEDS),
                            monthlyBudget = o.optDouble("monthlyBudget", 0.0),
                            isDefault = o.optBoolean("isDefault", false)
                        )
                    )
                }
            }

            val transactions = mutableListOf<TransactionEntity>()
            val txArr = root.optJSONArray("transactions")
            if (txArr != null) {
                for (i in 0 until txArr.length()) {
                    val o = txArr.getJSONObject(i)
                    val toAccId = if (o.isNull("toAccountId")) null else o.optLong("toAccountId")
                    val receiptUri = if (o.isNull("receiptUri")) null else o.optString("receiptUri", "").ifBlank { null }
                    transactions.add(
                        TransactionEntity(
                            id = o.getLong("id"),
                            title = o.getString("title"),
                            amount = o.optDouble("amount", 0.0),
                            type = runCatching { TransactionType.valueOf(o.getString("type")) }.getOrDefault(TransactionType.EXPENSE),
                            categoryId = o.optLong("categoryId", 0L),
                            dateMillis = o.optLong("dateMillis", System.currentTimeMillis()),
                            notes = o.optString("notes", ""),
                            currencyCode = o.optString("currencyCode", currencyCode),
                            accountId = o.optLong("accountId", 1L),
                            toAccountId = toAccId,
                            receiptUri = receiptUri
                        )
                    )
                }
            }

            val debts = mutableListOf<DebtLoanEntity>()
            val debtArr = root.optJSONArray("debts")
            if (debtArr != null) {
                for (i in 0 until debtArr.length()) {
                    val o = debtArr.getJSONObject(i)
                    debts.add(
                        DebtLoanEntity(
                            id = o.getLong("id"),
                            personName = o.getString("personName"),
                            totalAmount = o.optDouble("totalAmount", 0.0),
                            remainingAmount = o.optDouble("remainingAmount", 0.0),
                            type = runCatching { DebtType.valueOf(o.getString("type")) }.getOrDefault(DebtType.HUTANG),
                            dueDateMillis = o.optLong("dueDateMillis", System.currentTimeMillis()),
                            notes = o.optString("notes", ""),
                            phone = o.optString("phone", ""),
                            currencyCode = o.optString("currencyCode", currencyCode),
                            isSettled = o.optBoolean("isSettled", false)
                        )
                    )
                }
            }

            val bills = mutableListOf<BillReminderEntity>()
            val billArr = root.optJSONArray("bills")
            if (billArr != null) {
                for (i in 0 until billArr.length()) {
                    val o = billArr.getJSONObject(i)
                    bills.add(
                        BillReminderEntity(
                            id = o.getLong("id"),
                            title = o.getString("title"),
                            amount = o.optDouble("amount", 0.0),
                            categoryId = o.optLong("categoryId", 0L),
                            dueDateMillis = o.optLong("dueDateMillis", System.currentTimeMillis()),
                            repeatIntervalMonths = o.optInt("repeatIntervalMonths", 1),
                            notes = o.optString("notes", ""),
                            currencyCode = o.optString("currencyCode", currencyCode),
                            isPaid = o.optBoolean("isPaid", false)
                        )
                    )
                }
            }

            DompetKuSnapshot(
                ownerEmail = ownerEmail,
                currencyCode = currencyCode,
                overallMonthlyBudget = overallBudget,
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                debts = debts,
                bills = bills,
                updatedAtMillis = updatedAt
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun toSheetTables(snapshot: DompetKuSnapshot): Map<String, List<List<Any>>> {
        val settingsRows = listOf(
            listOf("Kunci", "Nilai"),
            listOf("ownerEmail", snapshot.ownerEmail),
            listOf("currencyCode", snapshot.currencyCode),
            listOf("overallMonthlyBudget", safeNumber(snapshot.overallMonthlyBudget)),
            listOf("updatedAtMillis", snapshot.updatedAtMillis.toString()),
            listOf("schemaVersion", "3")
        )

        val accountRows = mutableListOf<List<Any>>(
            listOf("id", "name", "type", "nature", "balance", "currencyCode", "colorHex", "isDefault")
        )
        snapshot.accounts.forEach { acc ->
            accountRows.add(
                listOf(
                    acc.id.toString(),
                    acc.name,
                    acc.type.name,
                    acc.nature.name,
                    safeNumber(acc.balance),
                    acc.currencyCode,
                    acc.colorHex,
                    acc.isDefault
                )
            )
        }

        val categoryRows = mutableListOf<List<Any>>(
            listOf("id", "name", "type", "iconKey", "colorHex", "expenseGroup", "monthlyBudget", "isDefault")
        )
        snapshot.categories.forEach { cat ->
            categoryRows.add(
                listOf(
                    cat.id.toString(),
                    cat.name,
                    cat.type.name,
                    cat.iconKey,
                    cat.colorHex,
                    cat.expenseGroup.name,
                    safeNumber(cat.monthlyBudget),
                    cat.isDefault
                )
            )
        }

        val txRows = mutableListOf<List<Any>>(
            listOf("id", "title", "amount", "type", "categoryId", "dateMillis", "notes", "currencyCode", "accountId", "toAccountId", "receiptUri")
        )
        snapshot.transactions.forEach { tx ->
            txRows.add(
                listOf(
                    tx.id.toString(),
                    tx.title,
                    safeNumber(tx.amount),
                    tx.type.name,
                    tx.categoryId.toString(),
                    tx.dateMillis.toString(),
                    tx.notes,
                    tx.currencyCode,
                    tx.accountId.toString(),
                    tx.toAccountId?.toString() ?: "",
                    tx.receiptUri ?: ""
                )
            )
        }

        val debtRows = mutableListOf<List<Any>>(
            listOf("id", "personName", "totalAmount", "remainingAmount", "type", "dueDateMillis", "notes", "phone", "currencyCode", "isSettled")
        )
        snapshot.debts.forEach { debt ->
            debtRows.add(
                listOf(
                    debt.id.toString(),
                    debt.personName,
                    safeNumber(debt.totalAmount),
                    safeNumber(debt.remainingAmount),
                    debt.type.name,
                    debt.dueDateMillis.toString(),
                    debt.notes,
                    debt.phone,
                    debt.currencyCode,
                    debt.isSettled
                )
            )
        }

        val billRows = mutableListOf<List<Any>>(
            listOf("id", "title", "amount", "categoryId", "dueDateMillis", "repeatIntervalMonths", "notes", "currencyCode", "isPaid")
        )
        snapshot.bills.forEach { bill ->
            billRows.add(
                listOf(
                    bill.id.toString(),
                    bill.title,
                    safeNumber(bill.amount),
                    bill.categoryId.toString(),
                    bill.dueDateMillis.toString(),
                    bill.repeatIntervalMonths,
                    bill.notes,
                    bill.currencyCode,
                    bill.isPaid
                )
            )
        }

        return mapOf(
            DompetKuSnapshot.SHEET_SETTINGS to settingsRows,
            DompetKuSnapshot.SHEET_ACCOUNTS to accountRows,
            DompetKuSnapshot.SHEET_CATEGORIES to categoryRows,
            DompetKuSnapshot.SHEET_TRANSACTIONS to txRows,
            DompetKuSnapshot.SHEET_DEBTS to debtRows,
            DompetKuSnapshot.SHEET_BILLS to billRows
        )
    }

    fun fromSheetTables(tables: Map<String, List<List<Any?>>>): DompetKuSnapshot? {
        val settingsRows = tables[DompetKuSnapshot.SHEET_SETTINGS].orEmpty()
        val accountRows = tables[DompetKuSnapshot.SHEET_ACCOUNTS].orEmpty()
        val categoryRows = tables[DompetKuSnapshot.SHEET_CATEGORIES].orEmpty()
        val txRows = tables[DompetKuSnapshot.SHEET_TRANSACTIONS].orEmpty()
        val debtRows = tables[DompetKuSnapshot.SHEET_DEBTS].orEmpty()
        val billRows = tables[DompetKuSnapshot.SHEET_BILLS].orEmpty()

        // If spreadsheet has no data rows beyond headers yet, signal empty/uninitialized
        val hasSettings = settingsRows.size > 1
        val hasAccounts = accountRows.size > 1
        val hasCategories = categoryRows.size > 1
        if (!hasSettings && !hasAccounts && !hasCategories) {
            return null
        }

        val settingsMap = mutableMapOf<String, String>()
        settingsRows.drop(1).forEach { row ->
            val key = row.getOrNull(0)?.toString()?.trim().orEmpty()
            val value = row.getOrNull(1)?.toString()?.trim().orEmpty()
            if (key.isNotEmpty()) settingsMap[key] = value
        }

        val ownerEmail = settingsMap["ownerEmail"]?.trim().orEmpty()
        val currencyCode = settingsMap["currencyCode"]?.ifBlank { "IDR" } ?: "IDR"
        val overallBudget = settingsMap["overallMonthlyBudget"]?.toDoubleOrNull() ?: 10_000_000.0
        val updatedAtMillis = settingsMap["updatedAtMillis"]?.toLongOrNull() ?: System.currentTimeMillis()

        val accounts = accountRows.drop(1).mapNotNull { row ->
            val id = parseLong(row.getOrNull(0)) ?: return@mapNotNull null
            val name = row.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AccountEntity(
                id = id,
                name = name,
                type = runCatching { AccountType.valueOf(row.getOrNull(2)?.toString().orEmpty()) }.getOrDefault(AccountType.CASH),
                nature = runCatching { AccountNature.valueOf(row.getOrNull(3)?.toString().orEmpty()) }.getOrDefault(AccountNature.DEBIT),
                balance = parseDouble(row.getOrNull(4)) ?: 0.0,
                currencyCode = row.getOrNull(5)?.toString()?.ifBlank { currencyCode } ?: currencyCode,
                colorHex = row.getOrNull(6)?.toString()?.ifBlank { "#10B981" } ?: "#10B981",
                isDefault = parseBoolean(row.getOrNull(7))
            )
        }

        val categories = categoryRows.drop(1).mapNotNull { row ->
            val id = parseLong(row.getOrNull(0)) ?: return@mapNotNull null
            val name = row.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CategoryEntity(
                id = id,
                name = name,
                type = runCatching { TransactionType.valueOf(row.getOrNull(2)?.toString().orEmpty()) }.getOrDefault(TransactionType.EXPENSE),
                iconKey = row.getOrNull(3)?.toString()?.ifBlank { "category" } ?: "category",
                colorHex = row.getOrNull(4)?.toString()?.ifBlank { "#0D9488" } ?: "#0D9488",
                expenseGroup = runCatching { ExpenseGroup.valueOf(row.getOrNull(5)?.toString().orEmpty()) }.getOrDefault(ExpenseGroup.NEEDS),
                monthlyBudget = parseDouble(row.getOrNull(6)) ?: 0.0,
                isDefault = parseBoolean(row.getOrNull(7))
            )
        }

        val transactions = txRows.drop(1).mapNotNull { row ->
            val id = parseLong(row.getOrNull(0)) ?: return@mapNotNull null
            val title = row.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val toAccRaw = row.getOrNull(9)?.toString()?.trim().orEmpty()
            val receiptRaw = row.getOrNull(10)?.toString()?.trim().orEmpty()
            TransactionEntity(
                id = id,
                title = title,
                amount = parseDouble(row.getOrNull(2)) ?: 0.0,
                type = runCatching { TransactionType.valueOf(row.getOrNull(3)?.toString().orEmpty()) }.getOrDefault(TransactionType.EXPENSE),
                categoryId = parseLong(row.getOrNull(4)) ?: 0L,
                dateMillis = parseLong(row.getOrNull(5)) ?: System.currentTimeMillis(),
                notes = row.getOrNull(6)?.toString().orEmpty(),
                currencyCode = row.getOrNull(7)?.toString()?.ifBlank { currencyCode } ?: currencyCode,
                accountId = parseLong(row.getOrNull(8)) ?: (accounts.firstOrNull()?.id ?: 1L),
                toAccountId = toAccRaw.toLongOrNull(),
                receiptUri = receiptRaw.ifBlank { null }
            )
        }

        val debts = debtRows.drop(1).mapNotNull { row ->
            val id = parseLong(row.getOrNull(0)) ?: return@mapNotNull null
            val personName = row.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DebtLoanEntity(
                id = id,
                personName = personName,
                totalAmount = parseDouble(row.getOrNull(2)) ?: 0.0,
                remainingAmount = parseDouble(row.getOrNull(3)) ?: 0.0,
                type = runCatching { DebtType.valueOf(row.getOrNull(4)?.toString().orEmpty()) }.getOrDefault(DebtType.HUTANG),
                dueDateMillis = parseLong(row.getOrNull(5)) ?: System.currentTimeMillis(),
                notes = row.getOrNull(6)?.toString().orEmpty(),
                phone = row.getOrNull(7)?.toString().orEmpty(),
                currencyCode = row.getOrNull(8)?.toString()?.ifBlank { currencyCode } ?: currencyCode,
                isSettled = parseBoolean(row.getOrNull(9))
            )
        }

        val bills = billRows.drop(1).mapNotNull { row ->
            val id = parseLong(row.getOrNull(0)) ?: return@mapNotNull null
            val title = row.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BillReminderEntity(
                id = id,
                title = title,
                amount = parseDouble(row.getOrNull(2)) ?: 0.0,
                categoryId = parseLong(row.getOrNull(3)) ?: 0L,
                dueDateMillis = parseLong(row.getOrNull(4)) ?: System.currentTimeMillis(),
                repeatIntervalMonths = parseLong(row.getOrNull(5))?.toInt() ?: 1,
                notes = row.getOrNull(6)?.toString().orEmpty(),
                currencyCode = row.getOrNull(7)?.toString()?.ifBlank { currencyCode } ?: currencyCode,
                isPaid = parseBoolean(row.getOrNull(8))
            )
        }

        return DompetKuSnapshot(
            ownerEmail = ownerEmail,
            currencyCode = currencyCode,
            overallMonthlyBudget = overallBudget,
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            debts = debts,
            bills = bills,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun parseLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull() ?: value.trim().toDoubleOrNull()?.toLong()
            else -> null
        }
    }

    private fun parseDouble(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun parseBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.trim().equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> false
        }
    }
}
