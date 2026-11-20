package com.example

import com.example.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CloudSnapshotSyncTest {

    @Test
    fun testSnapshotJsonRoundTrip() {
        val repo = FinanceRepository()
        repo.addAccount(
            AccountEntity(
                id = 99L,
                name = "Bank BCA",
                type = AccountType.BANK,
                nature = AccountNature.DEBIT,
                balance = 3_250_000.0,
                currencyCode = "IDR"
            )
        )
        repo.addTransaction(
            TransactionEntity(
                id = 888L,
                title = "Transfer Tabungan",
                amount = 500_000.0,
                type = TransactionType.TRANSFER,
                categoryId = 0L,
                dateMillis = 1_735_000_000_000L,
                notes = "Menabung rutin",
                currencyCode = "IDR",
                accountId = 1L,
                toAccountId = 99L
            )
        )

        val original = repo.exportSnapshot()
        val json = SnapshotSerializer.toJsonString(original)
        val restored = SnapshotSerializer.fromJsonString(json)

        assertNotNull(restored)
        assertEquals(original.currencyCode, restored!!.currencyCode)
        assertEquals(original.overallMonthlyBudget, restored.overallMonthlyBudget, 0.01)
        assertEquals(original.accounts.size, restored.accounts.size)
        assertEquals(original.categories.size, restored.categories.size)
        assertEquals(original.transactions.size, restored.transactions.size)
        assertEquals(original.debts.size, restored.debts.size)

        val transferTx = restored.transactions.find { it.id == 888L }
        assertNotNull(transferTx)
        assertEquals(99L, transferTx?.toAccountId)
        assertEquals("Transfer Tabungan", transferTx?.title)
    }

    @Test
    fun testGoogleSheetsTableRoundTrip() {
        val repo = FinanceRepository()
        repo.addBill(
            BillReminderEntity(
                id = 777L,
                title = "Internet Fiber",
                amount = 350_000.0,
                categoryId = 5L,
                dueDateMillis = 1_740_000_000_000L,
                repeatIntervalMonths = 1,
                notes = "Tagihan bulanan",
                currencyCode = "IDR",
                isPaid = false
            )
        )
        val original = repo.exportSnapshot()

        val tables = SnapshotSerializer.toSheetTables(original)
        assertEquals(6, tables.size)
        assertEquals(true, tables.containsKey(DompetKuSnapshot.SHEET_SETTINGS))
        assertEquals(true, tables.containsKey(DompetKuSnapshot.SHEET_ACCOUNTS))
        assertEquals(true, tables.containsKey(DompetKuSnapshot.SHEET_CATEGORIES))
        assertEquals(true, tables.containsKey(DompetKuSnapshot.SHEET_TRANSACTIONS))
        assertEquals(true, tables.containsKey(DompetKuSnapshot.SHEET_DEBTS))
        assertEquals(true, tables.containsKey(DompetKuSnapshot.SHEET_BILLS))

        val restored = SnapshotSerializer.fromSheetTables(tables)
        assertNotNull(restored)
        assertEquals(original.currencyCode, restored!!.currencyCode)
        assertEquals(original.overallMonthlyBudget, restored.overallMonthlyBudget, 0.01)
        assertEquals(original.accounts.size, restored.accounts.size)
        assertEquals(original.categories.size, restored.categories.size)
        assertEquals(original.transactions.size, restored.transactions.size)
        assertEquals(original.debts.size, restored.debts.size)
        assertEquals(original.bills.size, restored.bills.size)
        assertEquals("Internet Fiber", restored.bills.first().title)
        assertEquals(original.accounts.first().name, restored.accounts.first().name)
    }

    @Test
    fun testEmptyGoogleSheetsReturnsNull() {
        val emptyTables = mapOf(
            DompetKuSnapshot.SHEET_SETTINGS to listOf(listOf("Kunci", "Nilai")),
            DompetKuSnapshot.SHEET_ACCOUNTS to listOf(listOf("id", "name")),
            DompetKuSnapshot.SHEET_CATEGORIES to listOf(listOf("id", "name")),
            DompetKuSnapshot.SHEET_TRANSACTIONS to listOf(listOf("id", "title")),
            DompetKuSnapshot.SHEET_DEBTS to listOf(listOf("id", "personName")),
            DompetKuSnapshot.SHEET_BILLS to listOf(listOf("id", "title"))
        )
        val result = SnapshotSerializer.fromSheetTables(emptyTables)
        assertNull(result)
    }

    @Test
    fun testRepositoryRestoreAcrossAccounts() {
        val accountARepo = FinanceRepository()
        accountARepo.addAccount(
            AccountEntity(
                id = 50L,
                name = "Mandiri Bisnis",
                type = AccountType.BANK,
                balance = 7_500_000.0
            )
        )
        val accountASnapshot = accountARepo.exportSnapshot()

        // Simulate signing out & switching back to Account A on fresh repository
        val freshRepo = FinanceRepository()
        freshRepo.clearAllTransactions()
        freshRepo.restoreSnapshot(accountASnapshot)

        assertEquals(accountASnapshot.accounts.size, freshRepo.accounts.value.size)
        assertEquals("Mandiri Bisnis", freshRepo.accounts.value.find { it.id == 50L }?.name)
        assertEquals(accountASnapshot.transactions.size, freshRepo.transactions.value.size)
    }

    @Test
    fun testDifferentAccountsHaveDistinctDatabasesAndOwnerEmail() {
        val emailA = "akuna@gmail.com"
        val emailB = "akunb@gmail.com"

        assertEquals("DompetKu_Database (akuna@gmail.com)", GoogleDriveSheetsClient.spreadsheetTitleFor(emailA))
        assertEquals("DompetKu_Database (akunb@gmail.com)", GoogleDriveSheetsClient.spreadsheetTitleFor(emailB))

        val repoA = FinanceRepository()
        repoA.addAccount(AccountEntity(id = 101L, name = "Dompet A", type = AccountType.BANK, balance = 9_000_000.0))
        val snapA = repoA.exportSnapshot(ownerEmail = emailA)

        val repoB = FinanceRepository()
        repoB.clearAllTransactions()
        val snapB = repoB.exportSnapshot(ownerEmail = emailB)

        val tablesA = SnapshotSerializer.toSheetTables(snapA)
        val tablesB = SnapshotSerializer.toSheetTables(snapB)

        val restoredA = SnapshotSerializer.fromSheetTables(tablesA)
        val restoredB = SnapshotSerializer.fromSheetTables(tablesB)

        assertNotNull(restoredA)
        assertNotNull(restoredB)
        assertEquals(emailA, restoredA!!.ownerEmail)
        assertEquals(emailB, restoredB!!.ownerEmail)
        assertEquals(true, restoredA.accounts.any { it.name == "Dompet A" })
        assertEquals(false, restoredB.accounts.any { it.name == "Dompet A" })
        assertEquals(0, restoredB.transactions.size)
    }
}
