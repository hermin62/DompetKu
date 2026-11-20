package com.example

import com.example.data.AccountEntity
import com.example.data.AccountNature
import com.example.data.AccountType
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.util.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAndReceiptTest {

    @Test
    fun testAccountNatureAndBalanceCalculation() {
        val debitAccount = AccountEntity(
            id = 1L,
            name = "Kas Utama",
            type = AccountType.CASH,
            nature = AccountNature.DEBIT,
            balance = 500000.0,
            isDefault = true
        )

        val creditAccount = AccountEntity(
            id = 2L,
            name = "Kartu Kredit",
            type = AccountType.CREDIT_CARD,
            nature = AccountNature.CREDIT,
            balance = 0.0,
            isDefault = false
        )

        assertEquals(AccountNature.DEBIT, debitAccount.nature)
        assertEquals(AccountNature.CREDIT, creditAccount.nature)

        val txIncome = TransactionEntity(
            id = 1L,
            title = "Gaji",
            amount = 1000000.0,
            type = TransactionType.INCOME,
            categoryId = 1L,
            dateMillis = System.currentTimeMillis(),
            accountId = 1L
        )

        val txExpense = TransactionEntity(
            id = 2L,
            title = "Belanja",
            amount = 200000.0,
            type = TransactionType.EXPENSE,
            categoryId = 2L,
            dateMillis = System.currentTimeMillis(),
            accountId = 1L
        )

        val remainingDebit = debitAccount.balance + txIncome.amount - txExpense.amount
        assertEquals(1300000.0, remainingDebit, 0.01)
    }

    @Test
    fun testCategoryBudgetAndRemainingCalculation() {
        val monthlyBudget = 1_000_000.0
        val spentNormal = 400_000.0
        val remainingNormal = monthlyBudget - spentNormal
        val progressNormal = (spentNormal / monthlyBudget).toFloat().coerceIn(0f, 1f)

        assertEquals(600_000.0, remainingNormal, 0.01)
        assertEquals(0.4f, progressNormal, 0.001f)

        val spentOver = 1_250_000.0
        val remainingOver = monthlyBudget - spentOver
        val progressOver = (spentOver / monthlyBudget).toFloat().coerceIn(0f, 1f)

        assertTrue(remainingOver < 0)
        assertEquals(-250_000.0, remainingOver, 0.01)
        assertEquals(1.0f, progressOver, 0.001f)
    }

    @Test
    fun testDebtSettlementAndDueCalculation() {
        val now = System.currentTimeMillis()
        val oneDay = 86400_000L

        // Piutang
        val piutang = com.example.data.DebtLoanEntity(
            id = 10L,
            personName = "Andi",
            totalAmount = 500_000.0,
            remainingAmount = 200_000.0,
            type = com.example.data.DebtType.PIUTANG,
            dueDateMillis = now + 2 * oneDay
        )

        assertEquals(300_000.0, piutang.totalAmount - piutang.remainingAmount, 0.01)
        val daysDiff = ((piutang.dueDateMillis - now) / oneDay).toInt()
        assertTrue(daysDiff in 1..2)
        assertFalse(piutang.isSettled)

        // After full payment
        val settledPiutang = piutang.copy(remainingAmount = 0.0, isSettled = true)
        assertTrue(settledPiutang.isSettled)
        assertEquals(0.0, settledPiutang.remainingAmount, 0.01)
    }

    @Test
    fun testMonthlyCashFlowNetCalculation() {
        val inflow = 12_500_000.0
        val outflow = 6_000_000.0
        val netCashFlow = inflow - outflow

        assertEquals(6_500_000.0, netCashFlow, 0.01)
        assertTrue(netCashFlow > 0) // Surplus
    }

    @Test
    fun testSampleReceiptsAvailable() {
        assertTrue(ReceiptParser.sampleReceipts.isNotEmpty())
        val minimarket = ReceiptParser.sampleReceipts.firstOrNull { it.merchantName.contains("Indomaret") }
        assertNotNull(minimarket)
        assertTrue(minimarket!!.amount > 0)
    }
}
