package com.example

import com.example.data.CategoryEntity
import com.example.data.ExpenseGroup
import com.example.data.InsightPeriod
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.data.WeeklySummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WeeklySummaryCalculatorTest {

    private fun dayMillis(daysOffset: Int, hour: Int = 12): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, daysOffset)
        }.timeInMillis
    }

    private val sampleCategories = listOf(
        CategoryEntity(
            id = 1L,
            name = "Makanan & Minuman",
            type = TransactionType.EXPENSE,
            expenseGroup = ExpenseGroup.NEEDS,
            monthlyBudget = 1_000_000.0
        ),
        CategoryEntity(
            id = 2L,
            name = "Restoran & Cafe",
            type = TransactionType.EXPENSE,
            expenseGroup = ExpenseGroup.WANTS,
            monthlyBudget = 300_000.0
        ),
        CategoryEntity(
            id = 3L,
            name = "Gaji",
            type = TransactionType.INCOME,
            expenseGroup = ExpenseGroup.NEEDS
        )
    )

    @Test
    fun `calculates rolling 7-day totals, previous week delta, and needs-wants ratio accurately`() {
        val now = dayMillis(0, 15)
        val transactions = listOf(
            // Current 7 days (offset 0 .. -6)
            TransactionEntity(id = 1L, title = "Gaji Mingguan", amount = 3_000_000.0, type = TransactionType.INCOME, categoryId = 3L, dateMillis = dayMillis(-2)),
            TransactionEntity(id = 2L, title = "Makan Siang", amount = 200_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = dayMillis(0)),
            TransactionEntity(id = 3L, title = "Belanja Harian", amount = 400_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = dayMillis(-5)),
            TransactionEntity(id = 4L, title = "Ngopi", amount = 400_000.0, type = TransactionType.EXPENSE, categoryId = 2L, dateMillis = dayMillis(-1)),
            // Previous 7 days (offset -7 .. -13)
            TransactionEntity(id = 5L, title = "Makan Minggu Lalu", amount = 500_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = dayMillis(-8)),
            // Older than 14 days (excluded from both 7-day windows)
            TransactionEntity(id = 6L, title = "Pengeluaran Lama", amount = 9_000_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = dayMillis(-16))
        )

        val metrics = WeeklySummaryCalculator.calculate(
            accountKey = "alice@gmail.com",
            transactions = transactions,
            categories = sampleCategories,
            currencyCode = "IDR",
            overallMonthlyBudget = 5_000_000.0,
            nowMillis = now
        )

        assertEquals(3_000_000.0, metrics.weekIncome, 0.01)
        assertEquals(1_000_000.0, metrics.weekExpense, 0.01)
        assertEquals(500_000.0, metrics.prevWeekExpense, 0.01)
        assertEquals(100.0, metrics.expenseChangePercent!!, 0.01)
        assertEquals(2_000_000.0, metrics.netCashFlow, 0.01)
        assertEquals(600_000.0, metrics.needsExpense, 0.01)
        assertEquals(400_000.0, metrics.wantsExpense, 0.01)
        assertEquals(60f, metrics.needsPercent, 0.01f)
        assertEquals(40f, metrics.wantsPercent, 0.01f)
        assertEquals(2, metrics.topCategories.size)
        assertEquals("Makanan & Minuman", metrics.topCategories.first().categoryName)
        assertEquals(600_000.0, metrics.topCategories.first().amount, 0.01)
        assertTrue(metrics.overBudgetCategoryNames.contains("Restoran & Cafe"))
    }

    @Test
    fun `handles zero previous week expense without division by zero`() {
        val now = dayMillis(0, 15)
        val transactions = listOf(
            TransactionEntity(id = 10L, title = "Makan", amount = 140_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = dayMillis(-1))
        )

        val metrics = WeeklySummaryCalculator.calculate(
            accountKey = "local_offline",
            transactions = transactions,
            categories = sampleCategories,
            currencyCode = "IDR",
            overallMonthlyBudget = 2_000_000.0,
            nowMillis = now
        )

        assertNull(metrics.expenseChangePercent)
        assertEquals(20_000.0, metrics.dailyAverageExpense, 0.01)
    }

    @Test
    fun `signature changes when accountKey or transaction data changes`() {
        val now = dayMillis(0, 15)
        val baseTx = listOf(
            TransactionEntity(id = 1L, title = "Kopi", amount = 30_000.0, type = TransactionType.EXPENSE, categoryId = 2L, dateMillis = dayMillis(0))
        )

        val aliceMetrics = WeeklySummaryCalculator.calculate(
            accountKey = "alice@gmail.com",
            transactions = baseTx,
            categories = sampleCategories,
            currencyCode = "IDR",
            overallMonthlyBudget = 5_000_000.0,
            nowMillis = now
        )
        val bobMetrics = WeeklySummaryCalculator.calculate(
            accountKey = "bob@gmail.com",
            transactions = baseTx,
            categories = sampleCategories,
            currencyCode = "IDR",
            overallMonthlyBudget = 5_000_000.0,
            nowMillis = now
        )
        val updatedAliceMetrics = WeeklySummaryCalculator.calculate(
            accountKey = "alice@gmail.com",
            transactions = baseTx + TransactionEntity(id = 2L, title = "Roti", amount = 25_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = dayMillis(0)),
            categories = sampleCategories,
            currencyCode = "IDR",
            overallMonthlyBudget = 5_000_000.0,
            nowMillis = now
        )

        assertNotEquals(aliceMetrics.dataSignature, bobMetrics.dataSignature)
        assertNotEquals(aliceMetrics.dataSignature, updatedAliceMetrics.dataSignature)
    }

    @Test
    fun `calculates monthly totals, previous month comparison, and distinct signature vs weekly`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 20, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nov5 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 5, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val nov18 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val oct12 = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 12, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val transactions = listOf(
            TransactionEntity(id = 1L, title = "Gaji Bulanan", amount = 8_000_000.0, type = TransactionType.INCOME, categoryId = 3L, dateMillis = nov5),
            TransactionEntity(id = 2L, title = "Belanja Bulanan", amount = 1_500_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = nov5),
            TransactionEntity(id = 3L, title = "Makan Cafe", amount = 500_000.0, type = TransactionType.EXPENSE, categoryId = 2L, dateMillis = nov18),
            TransactionEntity(id = 4L, title = "Pengeluaran Oktober", amount = 1_000_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = oct12)
        )

        val monthly = WeeklySummaryCalculator.calculateMonthly(
            accountKey = "alice@gmail.com",
            transactions = transactions,
            categories = sampleCategories,
            currencyCode = "IDR",
            overallMonthlyBudget = 4_000_000.0,
            nowMillis = now
        )
        val weekly = WeeklySummaryCalculator.calculate(
            accountKey = "alice@gmail.com",
            transactions = transactions,
            categories = sampleCategories,
            currencyCode = "IDR",
            overallMonthlyBudget = 4_000_000.0,
            nowMillis = now
        )

        assertEquals(InsightPeriod.MONTHLY, monthly.period)
        assertEquals(InsightPeriod.WEEKLY, weekly.period)
        assertEquals(8_000_000.0, monthly.weekIncome, 0.01)
        assertEquals(2_000_000.0, monthly.weekExpense, 0.01)
        assertEquals(1_000_000.0, monthly.prevWeekExpense, 0.01)
        assertEquals(100.0, monthly.expenseChangePercent!!, 0.01)
        assertEquals(6_000_000.0, monthly.netCashFlow, 0.01)
        assertEquals(100_000.0, monthly.dailyAverageExpense, 0.01) // 2_000_000 / 20 days elapsed in Nov
        assertNotEquals(weekly.dataSignature, monthly.dataSignature)
    }
}
