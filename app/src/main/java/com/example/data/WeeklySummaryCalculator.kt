package com.example.data

import com.example.util.DateUtils
import java.security.MessageDigest
import java.util.Calendar

enum class InsightPeriod {
    WEEKLY,
    MONTHLY
}

data class WeeklyCategoryBreakdown(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val expenseGroup: ExpenseGroup,
    val amount: Double,
    val sharePercent: Float,
    val monthlyBudget: Double,
    val monthlySpent: Double,
    val transactionCount: Int
)

data class WeeklyExpenseHighlight(
    val title: String,
    val categoryName: String,
    val amount: Double,
    val dateLabel: String
)

data class WeeklySpendingMetrics(
    val accountKey: String,
    val period: InsightPeriod = InsightPeriod.WEEKLY,
    val periodLabel: String,
    val weekStartMillis: Long,
    val weekEndMillis: Long,
    val currencyCode: String,
    val weekIncome: Double,
    val weekExpense: Double,
    val prevWeekExpense: Double,
    val expenseChangePercent: Double?,
    val netCashFlow: Double,
    val dailyAverageExpense: Double,
    val needsExpense: Double,
    val wantsExpense: Double,
    val needsPercent: Float,
    val wantsPercent: Float,
    val expenseTransactionCount: Int,
    val incomeTransactionCount: Int,
    val totalTransactionCount: Int,
    val topCategories: List<WeeklyCategoryBreakdown>,
    val largestExpenseTitle: String?,
    val largestExpenseAmount: Double,
    val overBudgetCategoryNames: List<String>,
    val overallMonthlyBudget: Double,
    val currentMonthTotalExpense: Double,
    val recentHighlights: List<WeeklyExpenseHighlight>,
    val dataSignature: String
)

object WeeklySummaryCalculator {

    fun calculate(
        accountKey: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String,
        overallMonthlyBudget: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): WeeklySpendingMetrics {
        return calculateForPeriod(
            period = InsightPeriod.WEEKLY,
            accountKey = accountKey,
            transactions = transactions,
            categories = categories,
            currencyCode = currencyCode,
            overallMonthlyBudget = overallMonthlyBudget,
            nowMillis = nowMillis
        )
    }

    fun calculateMonthly(
        accountKey: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String,
        overallMonthlyBudget: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): WeeklySpendingMetrics {
        return calculateForPeriod(
            period = InsightPeriod.MONTHLY,
            accountKey = accountKey,
            transactions = transactions,
            categories = categories,
            currencyCode = currencyCode,
            overallMonthlyBudget = overallMonthlyBudget,
            nowMillis = nowMillis
        )
    }

    fun calculateForPeriod(
        period: InsightPeriod,
        accountKey: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String,
        overallMonthlyBudget: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): WeeklySpendingMetrics {
        val periodStartMillis: Long
        val periodEndMillis: Long
        val prevPeriodStartMillis: Long
        val prevPeriodEndMillis: Long
        val periodLabel: String
        val activeDaysCount: Int

        if (period == InsightPeriod.MONTHLY) {
            val startCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.DAY_OF_MONTH, 1)
            }
            periodStartMillis = DateUtils.getStartOfDay(startCal.timeInMillis)
            val endCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            periodEndMillis = DateUtils.getEndOfDay(endCal.timeInMillis)

            val prevEndCal = Calendar.getInstance().apply {
                timeInMillis = periodStartMillis - 1L
            }
            prevPeriodEndMillis = prevEndCal.timeInMillis
            val prevStartCal = (prevEndCal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
            }
            prevPeriodStartMillis = DateUtils.getStartOfDay(prevStartCal.timeInMillis)

            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            activeDaysCount = nowCal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
            periodLabel = DateUtils.formatMonthYear(nowMillis)
        } else {
            periodEndMillis = DateUtils.getEndOfDay(nowMillis)
            val weekStartCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -6)
            }
            periodStartMillis = DateUtils.getStartOfDay(weekStartCal.timeInMillis)

            prevPeriodEndMillis = periodStartMillis - 1L
            val prevWeekStartCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -13)
            }
            prevPeriodStartMillis = DateUtils.getStartOfDay(prevWeekStartCal.timeInMillis)

            activeDaysCount = 7
            periodLabel = "${DateUtils.formatShortDate(periodStartMillis)} - ${DateUtils.formatShortDate(periodEndMillis)}"
        }

        val categoryMap = categories.associateBy { it.id }

        val periodTransactions = transactions.filter {
            it.dateMillis in periodStartMillis..periodEndMillis
        }
        val prevPeriodTransactions = transactions.filter {
            it.dateMillis in prevPeriodStartMillis..prevPeriodEndMillis
        }

        val periodExpenses = periodTransactions.filter { it.type == TransactionType.EXPENSE }
        val periodIncomes = periodTransactions.filter { it.type == TransactionType.INCOME }

        val periodExpense = periodExpenses.sumOf { it.amount }
        val periodIncome = periodIncomes.sumOf { it.amount }
        val prevPeriodExpense = prevPeriodTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val expenseChangePercent = if (prevPeriodExpense > 0.0) {
            ((periodExpense - prevPeriodExpense) / prevPeriodExpense) * 100.0
        } else {
            null
        }

        val netCashFlow = periodIncome - periodExpense
        val dailyAverageExpense = periodExpense / activeDaysCount.toDouble()

        var needsExpense = 0.0
        var wantsExpense = 0.0
        periodExpenses.forEach { tx ->
            val group = categoryMap[tx.categoryId]?.expenseGroup ?: ExpenseGroup.NEEDS
            if (group == ExpenseGroup.WANTS) {
                wantsExpense += tx.amount
            } else {
                needsExpense += tx.amount
            }
        }

        val needsPercent = if (periodExpense > 0.0) {
            ((needsExpense / periodExpense) * 100.0).toFloat()
        } else 0f
        val wantsPercent = if (periodExpense > 0.0) {
            ((wantsExpense / periodExpense) * 100.0).toFloat()
        } else 0f

        val currentMonthExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE && isInSameMonth(it.dateMillis, nowMillis)
        }
        val currentMonthTotalExpense = currentMonthExpenses.sumOf { it.amount }
        val monthlySpentByCategory = currentMonthExpenses
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val overBudgetCategoryNames = categories
            .filter { it.type == TransactionType.EXPENSE && it.monthlyBudget > 0.0 }
            .filter { (monthlySpentByCategory[it.id] ?: 0.0) > it.monthlyBudget }
            .map { it.name }

        val topCategories = periodExpenses
            .groupBy { it.categoryId }
            .map { (catId, txs) ->
                val cat = categoryMap[catId]
                val amt = txs.sumOf { it.amount }
                val share = if (periodExpense > 0.0) ((amt / periodExpense) * 100.0).toFloat() else 0f
                WeeklyCategoryBreakdown(
                    categoryId = catId,
                    categoryName = cat?.name ?: "Kategori Lain",
                    colorHex = cat?.colorHex ?: "#0D9488",
                    expenseGroup = cat?.expenseGroup ?: ExpenseGroup.NEEDS,
                    amount = amt,
                    sharePercent = share,
                    monthlyBudget = cat?.monthlyBudget ?: 0.0,
                    monthlySpent = monthlySpentByCategory[catId] ?: 0.0,
                    transactionCount = txs.size
                )
            }
            .sortedByDescending { it.amount }

        val largestExpense = periodExpenses.maxByOrNull { it.amount }
        val recentHighlights = periodExpenses
            .sortedWith(compareByDescending<TransactionEntity> { it.amount }.thenByDescending { it.dateMillis })
            .take(8)
            .map { tx ->
                WeeklyExpenseHighlight(
                    title = tx.title,
                    categoryName = categoryMap[tx.categoryId]?.name ?: "Umum",
                    amount = tx.amount,
                    dateLabel = DateUtils.formatShortDate(tx.dateMillis)
                )
            }

        val signature = buildSignature(
            period = period,
            accountKey = accountKey,
            periodLabel = periodLabel,
            currencyCode = currencyCode,
            overallMonthlyBudget = overallMonthlyBudget,
            prevPeriodExpense = prevPeriodExpense,
            currentMonthTotalExpense = currentMonthTotalExpense,
            periodTransactions = periodTransactions,
            categories = categories
        )

        return WeeklySpendingMetrics(
            accountKey = accountKey,
            period = period,
            periodLabel = periodLabel,
            weekStartMillis = periodStartMillis,
            weekEndMillis = periodEndMillis,
            currencyCode = currencyCode,
            weekIncome = periodIncome,
            weekExpense = periodExpense,
            prevWeekExpense = prevPeriodExpense,
            expenseChangePercent = expenseChangePercent,
            netCashFlow = netCashFlow,
            dailyAverageExpense = dailyAverageExpense,
            needsExpense = needsExpense,
            wantsExpense = wantsExpense,
            needsPercent = needsPercent,
            wantsPercent = wantsPercent,
            expenseTransactionCount = periodExpenses.size,
            incomeTransactionCount = periodIncomes.size,
            totalTransactionCount = periodTransactions.size,
            topCategories = topCategories,
            largestExpenseTitle = largestExpense?.title,
            largestExpenseAmount = largestExpense?.amount ?: 0.0,
            overBudgetCategoryNames = overBudgetCategoryNames,
            overallMonthlyBudget = overallMonthlyBudget,
            currentMonthTotalExpense = currentMonthTotalExpense,
            recentHighlights = recentHighlights,
            dataSignature = signature
        )
    }

    private fun isInSameMonth(millis: Long, referenceMillis: Long): Boolean {
        val ref = Calendar.getInstance().apply { timeInMillis = referenceMillis }
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        return ref.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            ref.get(Calendar.MONTH) == target.get(Calendar.MONTH)
    }

    private fun buildSignature(
        period: InsightPeriod,
        accountKey: String,
        periodLabel: String,
        currencyCode: String,
        overallMonthlyBudget: Double,
        prevPeriodExpense: Double,
        currentMonthTotalExpense: Double,
        periodTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ): String {
        val sb = StringBuilder()
        sb.append(period.name).append('|')
            .append(accountKey).append('|')
            .append(periodLabel).append('|')
            .append(currencyCode).append('|')
            .append(overallMonthlyBudget.toLong()).append('|')
            .append(prevPeriodExpense.toLong()).append('|')
            .append(currentMonthTotalExpense.toLong()).append('|')

        periodTransactions
            .sortedWith(compareBy<TransactionEntity> { it.id }.thenBy { it.dateMillis })
            .forEach { tx ->
                sb.append(tx.id).append(':')
                    .append(tx.type.name).append(':')
                    .append(tx.amount).append(':')
                    .append(tx.categoryId).append(':')
                    .append(tx.title).append(':')
                    .append(tx.dateMillis).append(';')
            }

        categories.sortedBy { it.id }.forEach { cat ->
            sb.append(cat.id).append(':')
                .append(cat.name).append(':')
                .append(cat.expenseGroup.name).append(':')
                .append(cat.monthlyBudget.toLong()).append(';')
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
