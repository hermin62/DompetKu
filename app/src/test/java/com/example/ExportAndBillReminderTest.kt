package com.example

import com.example.data.AccountEntity
import com.example.data.AccountType
import com.example.data.BillReminderEntity
import com.example.data.CategoryEntity
import com.example.data.ExpenseGroup
import com.example.data.FinanceRepository
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.util.BillNotificationHelper
import com.example.util.ExportHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.zip.ZipInputStream

class ExportAndBillReminderTest {

    @Test
    fun `writeExcelWorkbookToStream produces valid OOXML xlsx with summary and ledger sheets`() {
        val categories = listOf(
            CategoryEntity(1L, "Makanan & Minuman", TransactionType.EXPENSE, "food", "#0D9488", ExpenseGroup.NEEDS),
            CategoryEntity(11L, "Gaji Bulanan", TransactionType.INCOME, "salary", "#10B981", ExpenseGroup.NEEDS)
        )
        val accounts = listOf(
            AccountEntity(1L, "Kas Utama", AccountType.CASH, balance = 4_500_000.0)
        )
        val transactions = listOf(
            TransactionEntity(101L, "Gaji & Bonus", 10_000_000.0, TransactionType.INCOME, 11L, System.currentTimeMillis(), "Gaji Mei", "IDR", 1L),
            TransactionEntity(102L, "Makan Siang", 50_000.0, TransactionType.EXPENSE, 1L, System.currentTimeMillis(), "Soto & Es Teh", "IDR", 1L)
        )

        val baos = ByteArrayOutputStream()
        ExportHelper.writeExcelWorkbookToStream(
            outputStream = baos,
            periodName = "Mei 2026",
            transactions = transactions,
            categories = categories,
            currencyCode = "IDR",
            accounts = accounts
        )

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/styles.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))

        val workbookXml = entries["xl/workbook.xml"].orEmpty()
        assertTrue(workbookXml.contains("Ringkasan Bulanan"))
        assertTrue(workbookXml.contains("Daftar Transaksi"))

        val sheet1 = entries["xl/worksheets/sheet1.xml"].orEmpty()
        assertTrue(sheet1.contains("Mei 2026"))
        assertTrue(sheet1.contains("Total Pemasukan"))
        assertTrue(sheet1.contains("1.0E7") || sheet1.contains("10000000"))

        val sheet2 = entries["xl/worksheets/sheet2.xml"].orEmpty()
        assertTrue(sheet2.contains("Gaji &amp; Bonus"))
        assertTrue(sheet2.contains("Makan Siang"))
    }

    @Test
    fun `buildCsvContent includes monthly summary block and escaped transaction rows`() {
        val categories = listOf(
            CategoryEntity(5L, "Tagihan & Utilitas", TransactionType.EXPENSE, "bills", "#6366F1", ExpenseGroup.NEEDS)
        )
        val accounts = listOf(
            AccountEntity(1L, "Bank BCA", AccountType.BANK, balance = 2_000_000.0)
        )
        val transactions = listOf(
            TransactionEntity(1L, "Bayar WiFi \"Utama\"", 350_000.0, TransactionType.EXPENSE, 5L, System.currentTimeMillis(), "Tagihan bulanan", "IDR", 1L)
        )

        val csv = ExportHelper.buildCsvContent(
            periodName = "Bulan Ini",
            transactions = transactions,
            categories = categories,
            currencyCode = "IDR",
            accounts = accounts
        )

        assertTrue(csv.contains("RINGKASAN LAPORAN KEUANGAN DOMPETKU"))
        assertTrue(csv.contains("\"Total Pengeluaran\",350000.0"))
        assertTrue(csv.contains("\"Bayar WiFi \"\"Utama\"\"\""))
        assertTrue(csv.contains("\"Bank BCA\""))
    }

    @Test
    fun `findDueSoonBills detects overdue and upcoming unpaid bills within window`() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = baseCal.timeInMillis
        val oneDay = 86_400_000L

        val bills = listOf(
            BillReminderEntity(1L, "Listrik PLN", 400_000.0, 5L, now - 1 * oneDay, 1, isPaid = false), // overdue -1
            BillReminderEntity(2L, "Internet WiFi", 300_000.0, 5L, now + 2 * oneDay, 1, isPaid = false), // +2 days (due soon)
            BillReminderEntity(3L, "Sewa Kost", 1_500_000.0, 2L, now + 7 * oneDay, 1, isPaid = false), // +7 days (far)
            BillReminderEntity(4L, "Air PDAM", 100_000.0, 5L, now, 1, isPaid = true) // already paid
        )

        val dueAlerts = BillNotificationHelper.findDueSoonBills(
            bills = bills,
            nowMillis = now,
            reminderDaysBefore = 3
        )

        assertEquals(2, dueAlerts.size)
        assertEquals("Listrik PLN", dueAlerts[0].bill.title)
        assertEquals(-1, dueAlerts[0].daysUntilDue)
        assertEquals("Internet WiFi", dueAlerts[1].bill.title)
        assertEquals(2, dueAlerts[1].daysUntilDue)
    }

    @Test
    fun `payBill marks bill paid generates next recurring cycle and deducts account balance`() {
        val repository = FinanceRepository()
        val initialBalance = repository.accounts.value.first().balance
        val accountId = repository.accounts.value.first().id

        val dueCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val bill = BillReminderEntity(
            id = 999L,
            title = "WiFi IndiHome",
            amount = 350_000.0,
            categoryId = 5L,
            dueDateMillis = dueCal.timeInMillis,
            repeatIntervalMonths = 1,
            notes = "122334455",
            currencyCode = "IDR",
            isPaid = false
        )
        repository.addBill(bill)

        repository.payBill(billId = 999L, accountId = accountId)

        val allBills = repository.bills.value
        assertEquals(2, allBills.size)

        val paidOriginal = allBills.find { it.id == 999L }!!
        assertTrue(paidOriginal.isPaid)

        val nextCycle = allBills.find { it.id != 999L }!!
        assertFalse(nextCycle.isPaid)
        assertEquals("WiFi IndiHome", nextCycle.title)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextCycle.dueDateMillis }
        assertEquals(Calendar.JUNE, nextCal.get(Calendar.MONTH))
        assertEquals(10, nextCal.get(Calendar.DAY_OF_MONTH))

        val updatedBalance = repository.accounts.value.first { it.id == accountId }.balance
        assertEquals(initialBalance - 350_000.0, updatedBalance, 0.01)
        assertTrue(repository.transactions.value.any { it.title == "Bayar Tagihan: WiFi IndiHome" })
    }
}
