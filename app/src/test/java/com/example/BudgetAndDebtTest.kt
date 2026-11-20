package com.example

import com.example.data.BudgetAlertHelper
import com.example.data.BudgetHealthStatus
import com.example.data.CategoryEntity
import com.example.data.DebtLoanEntity
import com.example.data.DebtType
import com.example.data.ExpenseGroup
import com.example.data.FinanceRepository
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BudgetAndDebtTest {

    private fun currentMonthMillis(day: Int = 10): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, day.coerceAtMost(28))
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `evaluates category budget thresholds for safe, warning, exceeded, and no budget`() {
        val safeCat = CategoryEntity(1L, "Transport", TransactionType.EXPENSE, expenseGroup = ExpenseGroup.NEEDS, monthlyBudget = 1_000_000.0)
        val warningCat = CategoryEntity(2L, "Makanan", TransactionType.EXPENSE, expenseGroup = ExpenseGroup.NEEDS, monthlyBudget = 1_000_000.0)
        val exceededCat = CategoryEntity(3L, "Hiburan", TransactionType.EXPENSE, expenseGroup = ExpenseGroup.WANTS, monthlyBudget = 500_000.0)
        val noBudgetCat = CategoryEntity(4L, "Lainnya", TransactionType.EXPENSE, expenseGroup = ExpenseGroup.WANTS, monthlyBudget = 0.0)

        val now = currentMonthMillis()
        val transactions = listOf(
            TransactionEntity(10L, "Bensin", 500_000.0, TransactionType.EXPENSE, 1L, now),
            TransactionEntity(11L, "Makan", 850_000.0, TransactionType.EXPENSE, 2L, now),
            TransactionEntity(12L, "Nonton", 650_000.0, TransactionType.EXPENSE, 3L, now),
            TransactionEntity(13L, "Lain", 100_000.0, TransactionType.EXPENSE, 4L, now)
        )

        val evaluations = BudgetAlertHelper.evaluateExpenseCategories(
            categories = listOf(safeCat, warningCat, exceededCat, noBudgetCat),
            transactions = transactions,
            nowMillis = now
        )

        val byId = evaluations.associateBy { it.category.id }
        assertEquals(BudgetHealthStatus.SAFE, byId[1L]?.status)
        assertEquals(50, byId[1L]?.usagePercent)
        assertEquals(BudgetHealthStatus.WARNING, byId[2L]?.status)
        assertEquals(85, byId[2L]?.usagePercent)
        assertEquals(150_000.0, byId[2L]!!.remaining, 0.01)
        assertEquals(BudgetHealthStatus.EXCEEDED, byId[3L]?.status)
        assertEquals(130, byId[3L]?.usagePercent)
        assertEquals(BudgetHealthStatus.NO_BUDGET, byId[4L]?.status)

        val alerts = BudgetAlertHelper.extractAlerts(evaluations)
        assertEquals(2, alerts.size)
        assertEquals(3L, alerts[0].category.id) // Exceeded first
        assertEquals(2L, alerts[1].category.id) // Warning second
    }

    @Test
    fun `repository tracks partial and full debt repayment and updates account balance`() {
        val repo = FinanceRepository()
        val initialBalance = repo.accounts.value.first().balance
        val accountId = repo.accounts.value.first().id

        val debt = DebtLoanEntity(
            id = 999L,
            personName = "Bank Perkreditan",
            totalAmount = 1_000_000.0,
            remainingAmount = 1_000_000.0,
            type = DebtType.HUTANG,
            dueDateMillis = System.currentTimeMillis() + 86_400_000L,
            notes = "Cicilan modal"
        )
        repo.addDebt(debt)

        // Partial repayment 400.000
        repo.repayDebt(debtId = 999L, paymentAmount = 400_000.0, accountId = accountId)
        val afterPartial = repo.debts.value.first { it.id == 999L }
        assertFalse(afterPartial.isSettled)
        assertEquals(600_000.0, afterPartial.remainingAmount, 0.01)
        assertEquals(initialBalance - 400_000.0, repo.accounts.value.first().balance, 0.01)

        // Final repayment 600.000
        repo.repayDebt(debtId = 999L, paymentAmount = 600_000.0, accountId = accountId)
        val afterFull = repo.debts.value.first { it.id == 999L }
        assertTrue(afterFull.isSettled)
        assertEquals(0.0, afterFull.remainingAmount, 0.01)
        assertEquals(initialBalance - 1_000_000.0, repo.accounts.value.first().balance, 0.01)
    }
}
