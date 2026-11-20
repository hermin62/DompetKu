package com.example

import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.components.MonthlyCashFlowAggregator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthlyCashFlowTest {

    private fun monthMillis(monthsOffset: Int, day: Int = 10): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, monthsOffset)
        }.timeInMillis
    }

    @Test
    fun `aggregates 6 rolling months separating income and expense and ignoring transfers`() {
        val now = monthMillis(0, 18)
        val transactions = listOf(
            TransactionEntity(id = 1L, title = "Gaji Nov", amount = 10_000_000.0, type = TransactionType.INCOME, categoryId = 11L, dateMillis = monthMillis(0, 5)),
            TransactionEntity(id = 2L, title = "Belanja Nov", amount = 3_500_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = monthMillis(0, 10)),
            TransactionEntity(id = 3L, title = "Transfer Nov", amount = 2_000_000.0, type = TransactionType.TRANSFER, categoryId = 0L, dateMillis = monthMillis(0, 12), toAccountId = 2L),
            TransactionEntity(id = 4L, title = "Gaji Okt", amount = 8_000_000.0, type = TransactionType.INCOME, categoryId = 11L, dateMillis = monthMillis(-1, 5)),
            TransactionEntity(id = 5L, title = "Makan Okt", amount = 9_000_000.0, type = TransactionType.EXPENSE, categoryId = 1L, dateMillis = monthMillis(-1, 15)),
            TransactionEntity(id = 6L, title = "Transaksi Lama", amount = 5_000_000.0, type = TransactionType.INCOME, categoryId = 11L, dateMillis = monthMillis(-7, 15))
        )

        val series = MonthlyCashFlowAggregator.buildRollingSeries(
            transactions = transactions,
            monthsCount = 6,
            nowMillis = now
        )

        assertEquals(6, series.size)

        val currentMonth = series.last()
        assertEquals(10_000_000.0, currentMonth.income, 0.01)
        assertEquals(3_500_000.0, currentMonth.expense, 0.01)
        assertEquals(6_500_000.0, currentMonth.netCashFlow, 0.01)

        val lastMonth = series[series.size - 2]
        assertEquals(8_000_000.0, lastMonth.income, 0.01)
        assertEquals(9_000_000.0, lastMonth.expense, 0.01)
        assertEquals(-1_000_000.0, lastMonth.netCashFlow, 0.01)

        // Months with no data should be zeroed out cleanly
        val oldestInWindow = series.first()
        assertEquals(0.0, oldestInWindow.income, 0.01)
        assertEquals(0.0, oldestInWindow.expense, 0.01)
    }

    @Test
    fun `formats compact Y-axis amounts for IDR`() {
        assertEquals("0", MonthlyCashFlowAggregator.formatCompactAxisLabel(0.0, "IDR"))
        assertEquals("Rp 500rb", MonthlyCashFlowAggregator.formatCompactAxisLabel(500_000.0, "IDR"))
        assertEquals("Rp 10jt", MonthlyCashFlowAggregator.formatCompactAxisLabel(10_000_000.0, "IDR"))
        assertEquals("Rp 2.5jt", MonthlyCashFlowAggregator.formatCompactAxisLabel(2_500_000.0, "IDR"))
    }
}
