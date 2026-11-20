package com.example

import com.example.data.AccountEntity
import com.example.data.AccountNature
import com.example.data.AccountType
import com.example.data.BillReminderEntity
import com.example.data.CategoryEntity
import com.example.data.DebtLoanEntity
import com.example.data.DebtType
import com.example.data.ExpenseGroup
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.data.local.toDomain
import com.example.data.local.toRoom
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomCacheEntityMapperTest {

    @Test
    fun `transaction entity round-trips losslessly with account isolation`() {
        val accountKey = "alice@gmail.com"
        val tx = TransactionEntity(
            id = 42L,
            title = "Transfer Tabungan",
            amount = 750_000.0,
            type = TransactionType.TRANSFER,
            categoryId = 2L,
            dateMillis = 1_760_000_000_000L,
            notes = "Sisihkan gaji",
            currencyCode = "IDR",
            accountId = 10L,
            toAccountId = 20L,
            receiptUri = "content://media/external/images/99"
        )

        val cached = tx.toRoom(accountKey)
        assertEquals(accountKey, cached.accountKey)
        assertEquals(tx, cached.toDomain())
    }

    @Test
    fun `account category debt and bill entities round-trip losslessly`() {
        val key = "local_offline"
        val account = AccountEntity(
            id = 10L,
            name = "Bank BCA",
            type = AccountType.BANK,
            nature = AccountNature.DEBIT,
            balance = 5_250_000.0,
            currencyCode = "IDR",
            colorHex = "#0EA5E9",
            isDefault = true
        )
        val category = CategoryEntity(
            id = 3L,
            name = "Transportasi",
            type = TransactionType.EXPENSE,
            iconKey = "car",
            colorHex = "#F59E0B",
            expenseGroup = ExpenseGroup.NEEDS,
            monthlyBudget = 800_000.0,
            isDefault = true
        )
        val debt = DebtLoanEntity(
            id = 7L,
            personName = "Budi",
            totalAmount = 500_000.0,
            remainingAmount = 200_000.0,
            type = DebtType.PIUTANG,
            dueDateMillis = 1_761_000_000_000L,
            notes = "Pinjaman darurat",
            phone = "08123456789",
            currencyCode = "IDR",
            isSettled = false
        )
        val bill = BillReminderEntity(
            id = 9L,
            title = "Internet Fiber",
            amount = 385_000.0,
            categoryId = 3L,
            dueDateMillis = 1_762_000_000_000L,
            repeatIntervalMonths = 1,
            notes = "Auto bayar tgl 20",
            currencyCode = "IDR",
            isPaid = false
        )

        assertEquals(account, account.toRoom(key).toDomain())
        assertEquals(category, category.toRoom(key).toDomain())
        assertEquals(debt, debt.toRoom(key).toDomain())
        assertEquals(bill, bill.toRoom(key).toDomain())
    }
}
