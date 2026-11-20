package com.example.data

import java.util.Calendar

enum class BudgetHealthStatus {
    NO_BUDGET,
    SAFE,
    WARNING,
    EXCEEDED
}

data class CategoryBudgetEvaluation(
    val category: CategoryEntity,
    val spent: Double,
    val remaining: Double,
    val usageRatio: Float,
    val usagePercent: Int,
    val status: BudgetHealthStatus
)

object BudgetAlertHelper {

    const val WARNING_THRESHOLD = 0.80

    fun evaluateCategory(
        category: CategoryEntity,
        spentThisMonth: Double
    ): CategoryBudgetEvaluation {
        val budget = category.monthlyBudget
        if (budget <= 0.0) {
            return CategoryBudgetEvaluation(
                category = category,
                spent = spentThisMonth,
                remaining = 0.0,
                usageRatio = 0f,
                usagePercent = 0,
                status = BudgetHealthStatus.NO_BUDGET
            )
        }

        val ratio = spentThisMonth / budget
        val remaining = budget - spentThisMonth
        val pct = (ratio * 100.0).toInt()
        val status = when {
            spentThisMonth > budget -> BudgetHealthStatus.EXCEEDED
            ratio >= WARNING_THRESHOLD -> BudgetHealthStatus.WARNING
            else -> BudgetHealthStatus.SAFE
        }

        return CategoryBudgetEvaluation(
            category = category,
            spent = spentThisMonth,
            remaining = remaining,
            usageRatio = ratio.toFloat(),
            usagePercent = pct,
            status = status
        )
    }

    fun evaluateExpenseCategories(
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<CategoryBudgetEvaluation> {
        val refCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val refMonth = refCal.get(Calendar.MONTH)
        val refYear = refCal.get(Calendar.YEAR)

        val spentByCategory = transactions
            .filter { tx ->
                if (tx.type != TransactionType.EXPENSE) return@filter false
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
                txCal.get(Calendar.MONTH) == refMonth && txCal.get(Calendar.YEAR) == refYear
            }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        return categories
            .filter { it.type == TransactionType.EXPENSE }
            .map { cat -> evaluateCategory(cat, spentByCategory[cat.id] ?: 0.0) }
    }

    fun extractAlerts(evaluations: List<CategoryBudgetEvaluation>): List<CategoryBudgetEvaluation> {
        return evaluations
            .filter { it.status == BudgetHealthStatus.EXCEEDED || it.status == BudgetHealthStatus.WARNING }
            .sortedWith(
                compareBy<CategoryBudgetEvaluation> {
                    if (it.status == BudgetHealthStatus.EXCEEDED) 0 else 1
                }.thenByDescending { it.usageRatio }
            )
    }
}
