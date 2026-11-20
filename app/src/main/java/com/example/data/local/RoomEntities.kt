package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.*

@Entity(
    tableName = "cached_transactions",
    primaryKeys = ["accountKey", "id"],
    indices = [
        Index(value = ["accountKey", "dateMillis"]),
        Index(value = ["accountKey", "type"]),
        Index(value = ["accountKey", "categoryId"])
    ]
)
data class CachedTransactionEntity(
    val accountKey: String,
    val id: Long,
    val title: String,
    val amount: Double,
    val type: String,
    val categoryId: Long,
    val dateMillis: Long,
    val notes: String,
    val currencyCode: String,
    val accountId: Long,
    val toAccountId: Long?,
    val receiptUri: String?
)

@Entity(
    tableName = "cached_accounts",
    primaryKeys = ["accountKey", "id"]
)
data class CachedAccountEntity(
    val accountKey: String,
    val id: Long,
    val name: String,
    val type: String,
    val nature: String,
    val balance: Double,
    val currencyCode: String,
    val colorHex: String,
    val isDefault: Boolean
)

@Entity(
    tableName = "cached_categories",
    primaryKeys = ["accountKey", "id"]
)
data class CachedCategoryEntity(
    val accountKey: String,
    val id: Long,
    val name: String,
    val type: String,
    val iconKey: String,
    val colorHex: String,
    val expenseGroup: String,
    val monthlyBudget: Double,
    val isDefault: Boolean
)

@Entity(
    tableName = "cached_debts",
    primaryKeys = ["accountKey", "id"]
)
data class CachedDebtEntity(
    val accountKey: String,
    val id: Long,
    val personName: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    val type: String,
    val dueDateMillis: Long,
    val notes: String,
    val phone: String,
    val currencyCode: String,
    val isSettled: Boolean
)

@Entity(
    tableName = "cached_bills",
    primaryKeys = ["accountKey", "id"]
)
data class CachedBillEntity(
    val accountKey: String,
    val id: Long,
    val title: String,
    val amount: Double,
    val categoryId: Long,
    val dueDateMillis: Long,
    val repeatIntervalMonths: Int,
    val notes: String,
    val currencyCode: String,
    val isPaid: Boolean
)

@Entity(tableName = "cached_workspace_meta")
data class CachedWorkspaceMetaEntity(
    @PrimaryKey val accountKey: String,
    val ownerEmail: String,
    val currencyCode: String,
    val overallMonthlyBudget: Double,
    val updatedAtMillis: Long,
    val lastSyncedAtMillis: Long = 0L,
    val pendingCloudSync: Boolean = false
)

fun TransactionEntity.toRoom(accountKey: String): CachedTransactionEntity = CachedTransactionEntity(
    accountKey = accountKey,
    id = id,
    title = title,
    amount = amount,
    type = type.name,
    categoryId = categoryId,
    dateMillis = dateMillis,
    notes = notes,
    currencyCode = currencyCode,
    accountId = accountId,
    toAccountId = toAccountId,
    receiptUri = receiptUri
)

fun CachedTransactionEntity.toDomain(): TransactionEntity = TransactionEntity(
    id = id,
    title = title,
    amount = amount,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    categoryId = categoryId,
    dateMillis = dateMillis,
    notes = notes,
    currencyCode = currencyCode,
    accountId = accountId,
    toAccountId = toAccountId,
    receiptUri = receiptUri
)

fun AccountEntity.toRoom(accountKey: String): CachedAccountEntity = CachedAccountEntity(
    accountKey = accountKey,
    id = id,
    name = name,
    type = type.name,
    nature = nature.name,
    balance = balance,
    currencyCode = currencyCode,
    colorHex = colorHex,
    isDefault = isDefault
)

fun CachedAccountEntity.toDomain(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.CASH),
    nature = runCatching { AccountNature.valueOf(nature) }.getOrDefault(AccountNature.DEBIT),
    balance = balance,
    currencyCode = currencyCode,
    colorHex = colorHex,
    isDefault = isDefault
)

fun CategoryEntity.toRoom(accountKey: String): CachedCategoryEntity = CachedCategoryEntity(
    accountKey = accountKey,
    id = id,
    name = name,
    type = type.name,
    iconKey = iconKey,
    colorHex = colorHex,
    expenseGroup = expenseGroup.name,
    monthlyBudget = monthlyBudget,
    isDefault = isDefault
)

fun CachedCategoryEntity.toDomain(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    iconKey = iconKey,
    colorHex = colorHex,
    expenseGroup = runCatching { ExpenseGroup.valueOf(expenseGroup) }.getOrDefault(ExpenseGroup.NEEDS),
    monthlyBudget = monthlyBudget,
    isDefault = isDefault
)

fun DebtLoanEntity.toRoom(accountKey: String): CachedDebtEntity = CachedDebtEntity(
    accountKey = accountKey,
    id = id,
    personName = personName,
    totalAmount = totalAmount,
    remainingAmount = remainingAmount,
    type = type.name,
    dueDateMillis = dueDateMillis,
    notes = notes,
    phone = phone,
    currencyCode = currencyCode,
    isSettled = isSettled
)

fun CachedDebtEntity.toDomain(): DebtLoanEntity = DebtLoanEntity(
    id = id,
    personName = personName,
    totalAmount = totalAmount,
    remainingAmount = remainingAmount,
    type = runCatching { DebtType.valueOf(type) }.getOrDefault(DebtType.HUTANG),
    dueDateMillis = dueDateMillis,
    notes = notes,
    phone = phone,
    currencyCode = currencyCode,
    isSettled = isSettled
)

fun BillReminderEntity.toRoom(accountKey: String): CachedBillEntity = CachedBillEntity(
    accountKey = accountKey,
    id = id,
    title = title,
    amount = amount,
    categoryId = categoryId,
    dueDateMillis = dueDateMillis,
    repeatIntervalMonths = repeatIntervalMonths,
    notes = notes,
    currencyCode = currencyCode,
    isPaid = isPaid
)

fun CachedBillEntity.toDomain(): BillReminderEntity = BillReminderEntity(
    id = id,
    title = title,
    amount = amount,
    categoryId = categoryId,
    dueDateMillis = dueDateMillis,
    repeatIntervalMonths = repeatIntervalMonths,
    notes = notes,
    currencyCode = currencyCode,
    isPaid = isPaid
)
