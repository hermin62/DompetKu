package com.example.data

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class ExpenseGroup {
    NEEDS,
    WANTS
}

enum class AccountType {
    CASH,
    BANK,
    EWALLET,
    SAVINGS,
    CREDIT_CARD,
    PAYLATER,
    OTHER
}

enum class AccountNature {
    DEBIT,
    CREDIT
}

enum class DebtType {
    HUTANG,  // Saya meminjam (Kewajiban)
    PIUTANG  // Orang meminjam ke saya (Hak)
}

data class CategoryEntity(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val iconKey: String = "category",
    val colorHex: String = "#0D9488",
    val expenseGroup: ExpenseGroup = ExpenseGroup.NEEDS,
    val monthlyBudget: Double = 0.0,
    val isDefault: Boolean = false
)

data class TransactionEntity(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val dateMillis: Long = System.currentTimeMillis(),
    val notes: String = "",
    val currencyCode: String = "IDR",
    val accountId: Long = 1L,
    val toAccountId: Long? = null,
    val receiptUri: String? = null
)

data class AccountEntity(
    val id: Long,
    val name: String,
    val type: AccountType,
    val nature: AccountNature = AccountNature.DEBIT,
    val balance: Double = 0.0,
    val currencyCode: String = "IDR",
    val colorHex: String = "#0D9488",
    val isDefault: Boolean = false
)

data class AccountWithBalance(
    val account: AccountEntity,
    val currentBalance: Double,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalTransferIn: Double = 0.0,
    val totalTransferOut: Double = 0.0,
    val transactionCount: Int = 0
)

data class DebtLoanEntity(
    val id: Long = System.currentTimeMillis(),
    val personName: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    val type: DebtType,
    val dueDateMillis: Long,
    val notes: String = "",
    val phone: String = "",
    val currencyCode: String = "IDR",
    val isSettled: Boolean = false
)

data class BillReminderEntity(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val amount: Double,
    val categoryId: Long,
    val dueDateMillis: Long,
    val repeatIntervalMonths: Int = 1,
    val notes: String = "",
    val currencyCode: String = "IDR",
    val isPaid: Boolean = false
)
