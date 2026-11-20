package com.example.data

import com.example.util.CurrencyHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FinanceRepository {

    private val _categories = MutableStateFlow<List<CategoryEntity>>(createInitialCategories())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountEntity>>(createInitialAccounts())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(createInitialTransactions())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    private val _debts = MutableStateFlow<List<DebtLoanEntity>>(createInitialDebts())
    val debts: StateFlow<List<DebtLoanEntity>> = _debts.asStateFlow()

    private val _bills = MutableStateFlow<List<BillReminderEntity>>(emptyList())
    val bills: StateFlow<List<BillReminderEntity>> = _bills.asStateFlow()

    private val _activeCurrency = MutableStateFlow("IDR")
    val activeCurrency: StateFlow<String> = _activeCurrency.asStateFlow()

    private val _overallMonthlyBudget = MutableStateFlow(10_000_000.0)
    val overallMonthlyBudget: StateFlow<Double> = _overallMonthlyBudget.asStateFlow()

    private val _dataRevision = MutableStateFlow(0L)
    val dataRevision: StateFlow<Long> = _dataRevision.asStateFlow()

    init {
        // Initial sync of category budgets to match the overall budget (10.000.000)
        syncCategoryBudgetsToTotal(_overallMonthlyBudget.value)
    }

    private fun bumpRevision() {
        _dataRevision.value = _dataRevision.value + 1L
    }

    fun exportSnapshot(ownerEmail: String = ""): DompetKuSnapshot {
        return DompetKuSnapshot(
            ownerEmail = ownerEmail,
            currencyCode = _activeCurrency.value,
            overallMonthlyBudget = _overallMonthlyBudget.value,
            accounts = _accounts.value,
            categories = _categories.value,
            transactions = _transactions.value,
            debts = _debts.value,
            bills = _bills.value,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun restoreSnapshot(snapshot: DompetKuSnapshot) {
        _activeCurrency.value = snapshot.currencyCode.ifBlank { "IDR" }
        _overallMonthlyBudget.value = snapshot.overallMonthlyBudget
        _accounts.value = snapshot.accounts.ifEmpty { createInitialAccounts() }
        _categories.value = snapshot.categories.ifEmpty { createInitialCategories() }
        _transactions.value = snapshot.transactions
        _debts.value = snapshot.debts
        _bills.value = snapshot.bills
    }

    fun resetToStarterData() {
        _activeCurrency.value = "IDR"
        _overallMonthlyBudget.value = 10_000_000.0
        _categories.value = createInitialCategories()
        syncCategoryBudgetsToTotal(10_000_000.0)
        _accounts.value = createInitialAccounts()
        _transactions.value = createInitialTransactions()
        _debts.value = createInitialDebts()
        _bills.value = emptyList()
    }

    fun setOverallMonthlyBudget(total: Double, strategy: String = "SMART_50_30") {
        _overallMonthlyBudget.value = total
        syncCategoryBudgetsToTotal(total, strategy)
        bumpRevision()
    }

    private fun syncCategoryBudgetsToTotal(total: Double, strategy: String = "SMART_50_30") {
        if (total <= 0.0) {
            _categories.update { list ->
                list.map { if (it.type == TransactionType.EXPENSE) it.copy(monthlyBudget = 0.0) else it }
            }
            return
        }

        _categories.update { list ->
            val expenseCats = list.filter { it.type == TransactionType.EXPENSE }
            val needsCats = expenseCats.filter { it.expenseGroup == ExpenseGroup.NEEDS }
            val wantsCats = expenseCats.filter { it.expenseGroup == ExpenseGroup.WANTS }

            when (strategy) {
                "EQUAL" -> {
                    val count = expenseCats.size.coerceAtLeast(1)
                    val perCat = (total / count).toLong().toDouble()
                    list.map {
                        if (it.type == TransactionType.EXPENSE) it.copy(monthlyBudget = perCat) else it
                    }
                }
                else -> {
                    // Smart 65% Kebutuhan (Needs) & 35% Keinginan (Wants)
                    val needsPool = total * 0.65
                    val wantsPool = total * 0.35

                    val needsWeightMap = mapOf(
                        "makanan & minuman" to 0.30,
                        "tempat tinggal" to 0.25,
                        "transportasi" to 0.15,
                        "belanja bulanan" to 0.15,
                        "tagihan & utilitas" to 0.10,
                        "kesehatan" to 0.05
                    )

                    val wantsWeightMap = mapOf(
                        "restoran & cafe" to 0.30,
                        "hiburan" to 0.25,
                        "belanja pribadi" to 0.25,
                        "wisata & liburan" to 0.20
                    )

                    list.map { cat ->
                        if (cat.type != TransactionType.EXPENSE) {
                            cat
                        } else {
                            val lower = cat.name.lowercase().trim()
                            val allocated = if (cat.expenseGroup == ExpenseGroup.NEEDS) {
                                val weight = needsWeightMap[lower] ?: (1.0 / needsCats.size.coerceAtLeast(1))
                                (needsPool * weight)
                            } else {
                                val weight = wantsWeightMap[lower] ?: (1.0 / wantsCats.size.coerceAtLeast(1))
                                (wantsPool * weight)
                            }
                            // Round to nearest 50.000 for clean numbers
                            val rounded = (Math.round(allocated / 50_000.0) * 50_000.0).coerceAtLeast(50_000.0)
                            cat.copy(monthlyBudget = rounded)
                        }
                    }
                }
            }
        }
    }

    fun updateCategoryBudget(categoryId: Long, newBudget: Double) {
        _categories.update { list ->
            list.map { if (it.id == categoryId) it.copy(monthlyBudget = newBudget) else it }
        }
        // Recalculate overall budget as the sum of category budgets
        val sumExpenseBudget = _categories.value
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.monthlyBudget }
        if (sumExpenseBudget > 0) {
            _overallMonthlyBudget.value = sumExpenseBudget
        }
        bumpRevision()
    }

    fun addTransaction(tx: TransactionEntity) {
        _transactions.update { listOf(tx) + it }
        // Update account balances
        when (tx.type) {
            TransactionType.EXPENSE -> {
                adjustAccountBalanceInternal(tx.accountId, -tx.amount)
            }
            TransactionType.INCOME -> {
                adjustAccountBalanceInternal(tx.accountId, tx.amount)
            }
            TransactionType.TRANSFER -> {
                adjustAccountBalanceInternal(tx.accountId, -tx.amount)
                tx.toAccountId?.let { adjustAccountBalanceInternal(it, tx.amount) }
            }
        }
        bumpRevision()
    }

    fun updateTransaction(updated: TransactionEntity) {
        val old = _transactions.value.find { it.id == updated.id }
        if (old != null) {
            // Revert old transaction impact
            when (old.type) {
                TransactionType.EXPENSE -> adjustAccountBalanceInternal(old.accountId, old.amount)
                TransactionType.INCOME -> adjustAccountBalanceInternal(old.accountId, -old.amount)
                TransactionType.TRANSFER -> {
                    adjustAccountBalanceInternal(old.accountId, old.amount)
                    old.toAccountId?.let { adjustAccountBalanceInternal(it, -old.amount) }
                }
            }
        }
        // Apply updated
        when (updated.type) {
            TransactionType.EXPENSE -> adjustAccountBalanceInternal(updated.accountId, -updated.amount)
            TransactionType.INCOME -> adjustAccountBalanceInternal(updated.accountId, updated.amount)
            TransactionType.TRANSFER -> {
                adjustAccountBalanceInternal(updated.accountId, -updated.amount)
                updated.toAccountId?.let { adjustAccountBalanceInternal(it, updated.amount) }
            }
        }
        _transactions.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        bumpRevision()
    }

    fun deleteTransaction(tx: TransactionEntity) {
        // Revert balance impact
        when (tx.type) {
            TransactionType.EXPENSE -> adjustAccountBalanceInternal(tx.accountId, tx.amount)
            TransactionType.INCOME -> adjustAccountBalanceInternal(tx.accountId, -tx.amount)
            TransactionType.TRANSFER -> {
                adjustAccountBalanceInternal(tx.accountId, tx.amount)
                tx.toAccountId?.let { adjustAccountBalanceInternal(it, -tx.amount) }
            }
        }
        _transactions.update { list -> list.filter { it.id != tx.id } }
        bumpRevision()
    }

    fun clearAllTransactions() {
        _transactions.value = emptyList()
        _debts.value = emptyList()
        _bills.value = emptyList()
        // Reset account balances to 0 as in a clean install
        _accounts.update { list ->
            list.map { it.copy(balance = 0.0) }
        }
        bumpRevision()
    }

    fun addCategory(category: CategoryEntity) {
        _categories.update { it + category }
        bumpRevision()
    }

    fun updateCategory(updated: CategoryEntity) {
        _categories.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        bumpRevision()
    }

    fun deleteCategory(categoryId: Long) {
        _categories.update { list -> list.filter { it.id != categoryId } }
        bumpRevision()
    }

    fun switchCategoryGroup(categoryId: Long) {
        _categories.update { list ->
            list.map {
                if (it.id == categoryId && it.type == TransactionType.EXPENSE) {
                    val newGroup = if (it.expenseGroup == ExpenseGroup.NEEDS) ExpenseGroup.WANTS else ExpenseGroup.NEEDS
                    it.copy(expenseGroup = newGroup)
                } else it
            }
        }
        bumpRevision()
    }

    fun setActiveCurrency(currencyCode: String) {
        _activeCurrency.value = currencyCode
        bumpRevision()
    }

    fun addAccount(account: AccountEntity) {
        _accounts.update { it + account }
        bumpRevision()
    }

    fun updateAccount(updated: AccountEntity) {
        _accounts.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        bumpRevision()
    }

    fun deleteAccount(accountId: Long) {
        if (_accounts.value.size > 1) {
            _accounts.update { list -> list.filter { it.id != accountId } }
            bumpRevision()
        }
    }

    private fun adjustAccountBalanceInternal(accountId: Long, delta: Double) {
        _accounts.update { list ->
            list.map {
                if (it.id == accountId) it.copy(balance = it.balance + delta) else it
            }
        }
    }

    fun adjustAccountBalance(accountId: Long, delta: Double) {
        adjustAccountBalanceInternal(accountId, delta)
        bumpRevision()
    }

    fun setAccountBalance(accountId: Long, newBalance: Double) {
        _accounts.update { list ->
            list.map {
                if (it.id == accountId) it.copy(balance = newBalance) else it
            }
        }
        bumpRevision()
    }

    fun transferFunds(fromAccountId: Long, toAccountId: Long, amount: Double, notes: String = "") {
        val fromAcc = _accounts.value.find { it.id == fromAccountId }?.name ?: "Akun Asal"
        val toAcc = _accounts.value.find { it.id == toAccountId }?.name ?: "Akun Tujuan"
        val title = "Transfer: $fromAcc ➔ $toAcc"
        val tx = TransactionEntity(
            id = System.currentTimeMillis(),
            title = title,
            amount = amount,
            type = TransactionType.TRANSFER,
            categoryId = 0L,
            dateMillis = System.currentTimeMillis(),
            notes = notes,
            currencyCode = _activeCurrency.value,
            accountId = fromAccountId,
            toAccountId = toAccountId
        )
        addTransaction(tx)
    }

    fun addDebt(debt: DebtLoanEntity) {
        _debts.update { listOf(debt) + it }
        bumpRevision()
    }

    fun deleteDebt(debtId: Long) {
        _debts.update { list -> list.filter { it.id != debtId } }
        bumpRevision()
    }

    fun repayDebt(debtId: Long, paymentAmount: Double, accountId: Long? = null) {
        val debt = _debts.value.find { it.id == debtId } ?: return
        val remaining = (debt.remainingAmount - paymentAmount).coerceAtLeast(0.0)
        val isSettled = remaining <= 0.0

        _debts.update { list ->
            list.map {
                if (it.id == debtId) {
                    it.copy(remainingAmount = remaining, isSettled = isSettled)
                } else it
            }
        }

        // Auto-record transaction if linked to an account
        if (accountId != null && paymentAmount > 0.0) {
            val isPiutang = debt.type == DebtType.PIUTANG
            val title = if (isPiutang) "Pelunasan Piutang: ${debt.personName}" else "Pembayaran Hutang: ${debt.personName}"
            val txType = if (isPiutang) TransactionType.INCOME else TransactionType.EXPENSE
            val categoryId = if (isPiutang) 15L else 5L
            val tx = TransactionEntity(
                id = System.currentTimeMillis(),
                title = title,
                amount = paymentAmount,
                type = txType,
                categoryId = categoryId,
                dateMillis = System.currentTimeMillis(),
                notes = if (isSettled) "Lunas" else "Cicilan (Sisa ${CurrencyHelper.format(remaining, debt.currencyCode)})",
                currencyCode = debt.currencyCode,
                accountId = accountId
            )
            addTransaction(tx)
        } else {
            bumpRevision()
        }
    }

    fun addBill(bill: BillReminderEntity) {
        _bills.update { listOf(bill) + it }
        bumpRevision()
    }

    fun updateBill(updated: BillReminderEntity) {
        _bills.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        bumpRevision()
    }

    fun deleteBill(billId: Long) {
        _bills.update { list -> list.filter { it.id != billId } }
        bumpRevision()
    }

    fun payBill(billId: Long, accountId: Long? = null) {
        val target = _bills.value.find { it.id == billId } ?: return
        if (target.isPaid) return

        val nextCycleBill = if (target.repeatIntervalMonths > 0) {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = target.dueDateMillis
                add(java.util.Calendar.MONTH, target.repeatIntervalMonths)
            }
            target.copy(
                id = System.currentTimeMillis() + 1L,
                dueDateMillis = cal.timeInMillis,
                isPaid = false
            )
        } else {
            null
        }

        _bills.update { list ->
            val updatedList = list.map { if (it.id == billId) it.copy(isPaid = true) else it }
            if (nextCycleBill != null) listOf(nextCycleBill) + updatedList else updatedList
        }

        if (accountId != null && target.amount > 0.0) {
            val tx = TransactionEntity(
                id = System.currentTimeMillis(),
                title = "Bayar Tagihan: ${target.title}",
                amount = target.amount,
                type = TransactionType.EXPENSE,
                categoryId = target.categoryId.takeIf { it > 0L } ?: 5L,
                dateMillis = System.currentTimeMillis(),
                notes = target.notes.ifBlank { "Pembayaran tagihan berkala" },
                currencyCode = target.currencyCode,
                accountId = accountId
            )
            addTransaction(tx)
        } else {
            bumpRevision()
        }
    }

    private fun createInitialCategories(): List<CategoryEntity> {
        return listOf(
            // Kebutuhan (Needs)
            CategoryEntity(1L, "Makanan & Minuman", TransactionType.EXPENSE, "food", "#0D9488", ExpenseGroup.NEEDS, 2_000_000.0, true),
            CategoryEntity(2L, "Tempat Tinggal", TransactionType.EXPENSE, "housing", "#0284C7", ExpenseGroup.NEEDS, 1_500_000.0, true),
            CategoryEntity(3L, "Transportasi", TransactionType.EXPENSE, "transport", "#F59E0B", ExpenseGroup.NEEDS, 1_000_000.0, true),
            CategoryEntity(4L, "Belanja Bulanan", TransactionType.EXPENSE, "groceries", "#10B981", ExpenseGroup.NEEDS, 1_000_000.0, true),
            CategoryEntity(5L, "Tagihan & Utilitas", TransactionType.EXPENSE, "bills", "#6366F1", ExpenseGroup.NEEDS, 600_000.0, true),
            CategoryEntity(6L, "Kesehatan", TransactionType.EXPENSE, "health", "#EC4899", ExpenseGroup.NEEDS, 400_000.0, true),
            // Keinginan (Wants)
            CategoryEntity(7L, "Restoran & Cafe", TransactionType.EXPENSE, "restaurant", "#8B5CF6", ExpenseGroup.WANTS, 1_200_000.0, true),
            CategoryEntity(8L, "Hiburan", TransactionType.EXPENSE, "entertainment", "#D946EF", ExpenseGroup.WANTS, 800_000.0, true),
            CategoryEntity(9L, "Belanja Pribadi", TransactionType.EXPENSE, "shopping", "#F43F5E", ExpenseGroup.WANTS, 900_000.0, true),
            CategoryEntity(10L, "Wisata & Liburan", TransactionType.EXPENSE, "travel", "#06B6D4", ExpenseGroup.WANTS, 600_000.0, true),
            // Pemasukan (Income)
            CategoryEntity(11L, "Gaji Bulanan", TransactionType.INCOME, "salary", "#10B981", ExpenseGroup.NEEDS, 0.0, true),
            CategoryEntity(12L, "Bisnis & Usaha", TransactionType.INCOME, "business", "#0D9488", ExpenseGroup.NEEDS, 0.0, true),
            CategoryEntity(13L, "Bonus & Hadiah", TransactionType.INCOME, "bonus", "#F59E0B", ExpenseGroup.NEEDS, 0.0, true),
            CategoryEntity(14L, "Investasi", TransactionType.INCOME, "investment", "#6366F1", ExpenseGroup.NEEDS, 0.0, true),
            CategoryEntity(15L, "Pemasukan Lain", TransactionType.INCOME, "other", "#64748B", ExpenseGroup.NEEDS, 0.0, true)
        )
    }

    private fun createInitialAccounts(): List<AccountEntity> {
        return listOf(
            AccountEntity(1L, "Kas Utama", AccountType.CASH, AccountNature.DEBIT, 5_000_000.0, "IDR", "#10B981", true)
        )
    }

    private fun createInitialTransactions(): List<TransactionEntity> {
        val now = System.currentTimeMillis()
        val oneDay = 86400_000L
        return listOf(
            TransactionEntity(1L, "Gaji Masuk", 12_500_000.0, TransactionType.INCOME, 11L, now - 2 * oneDay, "Gaji Pokok", "IDR", 1L),
            TransactionEntity(2L, "Makan Siang Soto", 35_000.0, TransactionType.EXPENSE, 1L, now - 1 * oneDay, "Makan siang kantor", "IDR", 1L),
            TransactionEntity(3L, "Bensin Pertamax", 100_000.0, TransactionType.EXPENSE, 3L, now - 1 * oneDay, "Isi bensin motor", "IDR", 1L),
            TransactionEntity(4L, "Belanja Supermarket", 450_000.0, TransactionType.EXPENSE, 4L, now - 3 * oneDay, "Bahan masakan mingguan", "IDR", 1L),
            TransactionEntity(5L, "Kopi Senja", 28_000.0, TransactionType.EXPENSE, 7L, now, "Nongkrong sore", "IDR", 1L)
        )
    }

    private fun createInitialDebts(): List<DebtLoanEntity> {
        val now = System.currentTimeMillis()
        val oneDay = 86400_000L
        return listOf(
            DebtLoanEntity(
                id = 1L,
                personName = "Budi Santoso",
                totalAmount = 1_000_000.0,
                remainingAmount = 400_000.0,
                type = DebtType.PIUTANG,
                dueDateMillis = now + 3 * oneDay,
                notes = "Pinjam untuk servis motor",
                phone = "081234567890",
                currencyCode = "IDR",
                isSettled = false
            ),
            DebtLoanEntity(
                id = 2L,
                personName = "Pak Joko (Sewa Lapangan)",
                totalAmount = 500_000.0,
                remainingAmount = 500_000.0,
                type = DebtType.HUTANG,
                dueDateMillis = now + 1 * oneDay,
                notes = "Sewa futsal komunitas",
                phone = "085678901234",
                currencyCode = "IDR",
                isSettled = false
            ),
            DebtLoanEntity(
                id = 3L,
                personName = "Toko Berkah Jaya",
                totalAmount = 350_000.0,
                remainingAmount = 350_000.0,
                type = DebtType.HUTANG,
                dueDateMillis = now - 2 * oneDay,
                notes = "Kredit perlengkapan kantor",
                phone = "081987654321",
                currencyCode = "IDR",
                isSettled = false
            ),
            DebtLoanEntity(
                id = 4L,
                personName = "Rian Ardiansyah",
                totalAmount = 250_000.0,
                remainingAmount = 0.0,
                type = DebtType.PIUTANG,
                dueDateMillis = now - 5 * oneDay,
                notes = "Pinjaman makan siang",
                phone = "082134567891",
                currencyCode = "IDR",
                isSettled = true
            )
        )
    }
}
