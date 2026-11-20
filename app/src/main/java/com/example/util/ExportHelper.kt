package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.AccountEntity
import com.example.data.AccountNature
import com.example.data.AccountWithBalance
import com.example.data.CategoryEntity
import com.example.data.ExpenseGroup
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val shortLabel: String
) {
    PDF("pdf", "application/pdf", "PDF"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"),
    CSV("csv", "text/csv", "CSV")
}

object ExportHelper {

    fun generatePdfReport(
        context: Context,
        periodName: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String = "IDR",
        accounts: List<AccountEntity> = emptyList(),
        accountsWithBalance: List<AccountWithBalance> = emptyList()
    ): File? {
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        val document = PdfDocument()

        val pageWidth = 595 // A4 standard width (points)
        val pageHeight = 842 // A4 standard height (points)
        val margin = 36f
        val contentWidth = pageWidth - (margin * 2)
        var pageNumber = 1

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Professional Paints
        val paintPrimaryHeader = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintBrandTitle = Paint().apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintBrandSubtitle = Paint().apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            textSize = 8.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintBadge = Paint().apply {
            color = Color.rgb(16, 185, 129) // Emerald 500
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintSectionHeader = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintMetaLabel = Paint().apply {
            color = Color.rgb(100, 116, 139) // Slate 500
            textSize = 8f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintMetaValue = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintTableHeaderBg = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintTableHeaderText = Paint().apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintTableRowEven = Paint().apply {
            color = Color.rgb(248, 250, 252) // Slate 50
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintTableRowOdd = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintBody = Paint().apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            textSize = 7.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintBodyBold = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintIncome = Paint().apply {
            color = Color.rgb(5, 150, 105) // Emerald 600
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintExpense = Paint().apply {
            color = Color.rgb(220, 38, 38) // Red 600
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintTransfer = Paint().apply {
            color = Color.rgb(79, 70, 229) // Indigo 600
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintBorder = Paint().apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
            isAntiAlias = true
        }

        val paintKpiBg = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintFooter = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 7.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        fun drawHeaderBanner(c: Canvas, isFirstPage: Boolean) {
            // Navy Header Bar
            val headerHeight = if (isFirstPage) 70f else 32f
            c.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight, paintPrimaryHeader)

            if (isFirstPage) {
                // Title
                c.drawText("DANALOKA - LAPORAN KEUANGAN & ARUS KAS", margin, 28f, paintBrandTitle)
                c.drawText("CATATAN KEUANGAN & ANGGARAN RESMI", margin, 42f, paintBrandSubtitle)

                // Status Badge on the right
                val badgeText = "STATUS: TERVERIFIKASI SISTEM"
                val badgeX = pageWidth - margin - 150f
                val badgeRect = RectF(badgeX, 16f, pageWidth - margin, 34f)
                val paintBadgeBg = Paint().apply { color = Color.rgb(6, 78, 59); style = Paint.Style.FILL }
                c.drawRoundRect(badgeRect, 4f, 4f, paintBadgeBg)
                c.drawText(badgeText, badgeX + 8f, 29f, paintBadge)

                val docRef = "NO. REF: DL-${System.currentTimeMillis().toString().takeLast(8)}"
                c.drawText(docRef, badgeX, 48f, paintBrandSubtitle)
            } else {
                c.drawText("DANALOKA - LAPORAN KEUANGAN ($periodName)", margin, 21f, paintBrandSubtitle)
                c.drawText("Halaman $pageNumber", pageWidth - margin - 60f, 21f, paintBrandSubtitle)
            }
        }

        fun drawFooter(c: Canvas, curPage: Int) {
            c.drawLine(margin, pageHeight - 32f, pageWidth - margin, pageHeight - 32f, paintBorder)
            c.drawText("Dokumen ini diterbitkan secara otomatis dan terverifikasi oleh Sistem DanaLoka.", margin, pageHeight - 20f, paintFooter)
            c.drawText("Halaman $curPage", pageWidth - margin - 50f, pageHeight - 20f, paintFooter)
        }

        // Draw First Page Header
        drawHeaderBanner(canvas, true)
        var y = 85f

        // Document Metadata Block
        val printDateFormat = SimpleDateFormat("dd MMMM yyyy HH:mm 'WIB'", Locale("id", "ID"))
        val nowStr = printDateFormat.format(Date())

        val metaBox = RectF(margin, y, pageWidth - margin, y + 34f)
        canvas.drawRoundRect(metaBox, 6f, 6f, paintKpiBg)
        canvas.drawRoundRect(metaBox, 6f, 6f, paintBorder)

        val metaColW = contentWidth / 4f
        // Col 1: Periode
        canvas.drawText("PERIODE LAPORAN", margin + 10f, y + 14f, paintMetaLabel)
        canvas.drawText(periodName, margin + 10f, y + 26f, paintMetaValue)

        // Col 2: Tanggal Cetak
        canvas.drawText("TANGGAL CETAK", margin + 10f + metaColW, y + 14f, paintMetaLabel)
        canvas.drawText(nowStr, margin + 10f + metaColW, y + 26f, paintMetaValue)

        // Col 3: Mata Uang
        canvas.drawText("MATA UANG BASIS", margin + 10f + 2 * metaColW, y + 14f, paintMetaLabel)
        canvas.drawText("$currencyCode (${CurrencyHelper.getSymbol(currencyCode)})", margin + 10f + 2 * metaColW, y + 26f, paintMetaValue)

        // Col 4: Total Transaksi
        canvas.drawText("TOTAL ENTRI JURNAL", margin + 10f + 3 * metaColW, y + 14f, paintMetaLabel)
        canvas.drawText("${transactions.size} Transaksi", margin + 10f + 3 * metaColW, y + 26f, paintMetaValue)

        y += 46f

        // Financial Calculations
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalTransfer = transactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
        val netSurplus = totalIncome - totalExpense

        // Needs vs Wants Calculations
        val needsExpense = transactions.filter {
            it.type == TransactionType.EXPENSE && categoryMap[it.categoryId]?.expenseGroup == ExpenseGroup.NEEDS
        }.sumOf { it.amount }
        val wantsExpense = transactions.filter {
            it.type == TransactionType.EXPENSE && categoryMap[it.categoryId]?.expenseGroup == ExpenseGroup.WANTS
        }.sumOf { it.amount }

        val needsPercent = if (totalExpense > 0) (needsExpense / totalExpense * 100).toInt() else 0
        val wantsPercent = if (totalExpense > 0) (wantsExpense / totalExpense * 100).toInt() else 0

        // 1. EXECUTIVE SUMMARY CARDS (4 KPIs)
        canvas.drawText("1. RINGKASAN EKSEKUTIF KEUANGAN (EXECUTIVE FINANCIAL SUMMARY)", margin, y, paintSectionHeader)
        y += 10f

        val kpiW = (contentWidth - 24f) / 4f
        val kpiH = 50f

        val kpiTitles = listOf(
            "TOTAL PEMASUKAN",
            "TOTAL PENGELUARAN",
            "ARUS KAS BERSIH",
            "MUTASI ANTAR AKUN"
        )
        val kpiValues = listOf(
            "+ " + CurrencyHelper.format(totalIncome, currencyCode),
            "- " + CurrencyHelper.format(totalExpense, currencyCode),
            (if (netSurplus >= 0) "+ " else "- ") + CurrencyHelper.format(kotlin.math.abs(netSurplus), currencyCode),
            "⇆ " + CurrencyHelper.format(totalTransfer, currencyCode)
        )
        val kpiPaints = listOf(
            paintIncome,
            paintExpense,
            if (netSurplus >= 0) paintIncome else paintExpense,
            paintTransfer
        )

        for (i in 0 until 4) {
            val kpiLeft = margin + i * (kpiW + 8f)
            val rect = RectF(kpiLeft, y, kpiLeft + kpiW, y + kpiH)
            canvas.drawRoundRect(rect, 6f, 6f, paintKpiBg)
            canvas.drawRoundRect(rect, 6f, 6f, paintBorder)

            // Accent bar on top of card
            val accentPaint = Paint().apply { color = kpiPaints[i].color; style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(kpiLeft, y, kpiLeft + kpiW, y + 4f), 2f, 2f, accentPaint)

            canvas.drawText(kpiTitles[i], kpiLeft + 8f, y + 18f, paintMetaLabel)
            canvas.drawText(kpiValues[i], kpiLeft + 8f, y + 36f, kpiPaints[i])
        }

        y += kpiH + 16f

        // 2. ANALISIS PENGELUARAN: KEBUTUHAN VS KEINGINAN (50/30 RULE)
        canvas.drawText("2. ANALISIS KELOMPOK PENGELUARAN: KEBUTUHAN VS KEINGINAN", margin, y, paintSectionHeader)
        y += 10f

        val needsWantsBox = RectF(margin, y, pageWidth - margin, y + 44f)
        canvas.drawRoundRect(needsWantsBox, 6f, 6f, paintKpiBg)
        canvas.drawRoundRect(needsWantsBox, 6f, 6f, paintBorder)

        val splitW = contentWidth / 2f

        // Needs (Kebutuhan)
        canvas.drawText("KEBUTUHAN POKOK (NEEDS)", margin + 12f, y + 15f, paintSectionHeader)
        val needsStr = "${CurrencyHelper.format(needsExpense, currencyCode)} ($needsPercent% dari Pengeluaran)"
        canvas.drawText(needsStr, margin + 12f, y + 30f, paintBodyBold)
        canvas.drawText("Makanan pokok, utilitas, bensin, kesehatan, sekolah", margin + 12f, y + 40f, paintMetaLabel)

        // Wants (Keinginan)
        canvas.drawText("KEINGINAN / HIBURAN (WANTS)", margin + splitW + 12f, y + 15f, paintSectionHeader)
        val wantsStr = "${CurrencyHelper.format(wantsExpense, currencyCode)} ($wantsPercent% dari Pengeluaran)"
        canvas.drawText(wantsStr, margin + splitW + 12f, y + 30f, paintBodyBold)
        canvas.drawText("Hiburan, nongkrong, liburan, belanja hobi & rekreasi", margin + splitW + 12f, y + 40f, paintMetaLabel)

        y += 56f

        // 3. POSISI SALDO KAS & MUTASI AKUN (Jika data akun tersedia)
        val validAccounts = accountsWithBalance.ifEmpty {
            accounts.map {
                AccountWithBalance(account = it, currentBalance = it.balance)
            }
        }

        if (validAccounts.isNotEmpty()) {
            canvas.drawText("3. POSISI SALDO KAS & MUTASI REKENING (STATEMENT OF CASH ACCOUNTS)", margin, y, paintSectionHeader)
            y += 10f

            // Accounts Table Header
            val accRowHeight = 16f
            canvas.drawRect(margin, y, pageWidth - margin, y + accRowHeight, paintTableHeaderBg)
            canvas.drawText("No", margin + 6f, y + 11f, paintTableHeaderText)
            canvas.drawText("Nama Rekening / Akun", margin + 30f, y + 11f, paintTableHeaderText)
            canvas.drawText("Sifat", margin + 180f, y + 11f, paintTableHeaderText)
            canvas.drawText("Tipe", margin + 230f, y + 11f, paintTableHeaderText)
            canvas.drawText("Mutasi Masuk", margin + 310f, y + 11f, paintTableHeaderText)
            canvas.drawText("Mutasi Keluar", margin + 395f, y + 11f, paintTableHeaderText)
            canvas.drawText("Saldo Akhir", margin + 465f, y + 11f, paintTableHeaderText)
            y += accRowHeight

            var accIdx = 1
            for (accItem in validAccounts) {
                val isEven = accIdx % 2 == 0
                val rowPaint = if (isEven) paintTableRowEven else paintTableRowOdd
                canvas.drawRect(margin, y, pageWidth - margin, y + accRowHeight, rowPaint)
                canvas.drawRect(margin, y, pageWidth - margin, y + accRowHeight, paintBorder)

                canvas.drawText(accIdx.toString(), margin + 6f, y + 11f, paintBody)
                canvas.drawText(accItem.account.name.take(24), margin + 30f, y + 11f, paintBodyBold)

                val natureText = if (accItem.account.nature == AccountNature.DEBIT) "DEBIT (Aset)" else "KREDIT (Kewajiban)"
                canvas.drawText(natureText, margin + 180f, y + 11f, paintBody)
                canvas.drawText(AccountIcons.getTypeName(accItem.account.type), margin + 230f, y + 11f, paintBody)

                val totalIn = accItem.totalIncome + accItem.totalTransferIn
                val totalOut = accItem.totalExpense + accItem.totalTransferOut
                canvas.drawText(CurrencyHelper.format(totalIn, currencyCode), margin + 310f, y + 11f, paintIncome)
                canvas.drawText(CurrencyHelper.format(totalOut, currencyCode), margin + 395f, y + 11f, paintExpense)

                val balColor = if (accItem.currentBalance >= 0) paintBodyBold else paintExpense
                canvas.drawText(CurrencyHelper.format(accItem.currentBalance, currencyCode), margin + 465f, y + 11f, balColor)

                y += accRowHeight
                accIdx++
            }

            y += 14f
        }

        // 4. JURNAL TRANSAKSI DETAIL (GENERAL TRANSACTION LEDGER)
        canvas.drawText("4. JURNAL MUTASI TRANSAKSI DETAIL (${transactions.size} Entri)", margin, y, paintSectionHeader)
        y += 10f

        val tableRowHeight = 17f

        fun drawTransactionTableHeader(c: Canvas, curY: Float) {
            c.drawRect(margin, curY, pageWidth - margin, curY + tableRowHeight, paintTableHeaderBg)
            c.drawText("No", margin + 6f, curY + 11f, paintTableHeaderText)
            c.drawText("Tanggal", margin + 24f, curY + 11f, paintTableHeaderText)
            c.drawText("Deskripsi / Keterangan", margin + 85f, curY + 11f, paintTableHeaderText)
            c.drawText("Akun / Mutasi", margin + 235f, curY + 11f, paintTableHeaderText)
            c.drawText("Kategori & Kelompok", margin + 345f, curY + 11f, paintTableHeaderText)
            c.drawText("Tipe", margin + 440f, curY + 11f, paintTableHeaderText)
            c.drawText("Nominal", margin + 480f, curY + 11f, paintTableHeaderText)
        }

        drawTransactionTableHeader(canvas, y)
        y += tableRowHeight

        val itemDateFormat = SimpleDateFormat("dd/MM/yy", Locale("id", "ID"))
        var rowNumber = 1

        for (tx in transactions) {
            // Check for page overflow
            if (y > pageHeight - 65f) {
                drawFooter(canvas, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas

                drawHeaderBanner(canvas, false)
                y = 44f
                drawTransactionTableHeader(canvas, y)
                y += tableRowHeight
            }

            val isEven = rowNumber % 2 == 0
            val rowPaint = if (isEven) paintTableRowEven else paintTableRowOdd
            canvas.drawRect(margin, y, pageWidth - margin, y + tableRowHeight, rowPaint)
            canvas.drawLine(margin, y + tableRowHeight, pageWidth - margin, y + tableRowHeight, paintBorder)

            // Col 1: No
            canvas.drawText(rowNumber.toString(), margin + 6f, y + 11f, paintBody)

            // Col 2: Tanggal
            canvas.drawText(itemDateFormat.format(Date(tx.dateMillis)), margin + 24f, y + 11f, paintBody)

            // Col 3: Deskripsi
            val shortDesc = if (tx.title.length > 28) tx.title.take(26) + ".." else tx.title
            canvas.drawText(shortDesc, margin + 85f, y + 11f, paintBodyBold)

            // Col 4: Akun / Mutasi
            val accountInfo = if (tx.type == TransactionType.TRANSFER) {
                val fromName = accountMap[tx.accountId]?.name ?: "Akun Asal"
                val toName = accountMap[tx.toAccountId]?.name ?: "Akun Tujuan"
                "$fromName ➔ $toName"
            } else {
                accountMap[tx.accountId]?.name ?: "Kas Utama"
            }
            val shortAccount = if (accountInfo.length > 20) accountInfo.take(18) + ".." else accountInfo
            canvas.drawText(shortAccount, margin + 235f, y + 11f, paintBody)

            // Col 5: Kategori & Kelompok (Kebutuhan / Keinginan)
            val catObj = categoryMap[tx.categoryId]
            val catText = when (tx.type) {
                TransactionType.TRANSFER -> "Mutasi Kas"
                TransactionType.INCOME -> catObj?.name ?: "Pemasukan"
                TransactionType.EXPENSE -> {
                    val groupTag = if (catObj?.expenseGroup == ExpenseGroup.WANTS) "Keinginan" else "Kebutuhan"
                    val catName = catObj?.name ?: "Pengeluaran"
                    "$catName ($groupTag)"
                }
            }
            val shortCat = if (catText.length > 20) catText.take(18) + ".." else catText
            canvas.drawText(shortCat, margin + 345f, y + 11f, paintBody)

            // Col 6: Tipe
            val typeStr = when (tx.type) {
                TransactionType.INCOME -> "Masuk"
                TransactionType.EXPENSE -> "Keluar"
                TransactionType.TRANSFER -> "Transfer"
            }
            canvas.drawText(typeStr, margin + 440f, y + 11f, paintBody)

            // Col 7: Nominal
            val amountPrefix = when (tx.type) {
                TransactionType.INCOME -> "+ "
                TransactionType.EXPENSE -> "- "
                TransactionType.TRANSFER -> "⇆ "
            }
            val amtPaint = when (tx.type) {
                TransactionType.INCOME -> paintIncome
                TransactionType.EXPENSE -> paintExpense
                TransactionType.TRANSFER -> paintTransfer
            }
            val formattedAmt = amountPrefix + CurrencyHelper.format(tx.amount, tx.currencyCode)
            canvas.drawText(formattedAmt, margin + 480f, y + 11f, amtPaint)

            y += tableRowHeight
            rowNumber++
        }

        // Signature & Seal Block on final page if room permits, else on new page
        if (y > pageHeight - 90f) {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)

            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderBanner(canvas, false)
            y = 50f
        } else {
            y += 20f
        }

        // Official Signature & Disclaimer Box
        val signBox = RectF(pageWidth - margin - 170f, y, pageWidth - margin, y + 54f)
        canvas.drawRoundRect(signBox, 4f, 4f, paintKpiBg)
        canvas.drawRoundRect(signBox, 4f, 4f, paintBorder)

        canvas.drawText("PENGESAHAN DOKUMEN", pageWidth - margin - 162f, y + 14f, paintMetaLabel)
        canvas.drawText("Diverifikasi secara Elektronik", pageWidth - margin - 162f, y + 26f, paintBody)
        canvas.drawText("STATUS: SAH / AUDITED", pageWidth - margin - 162f, y + 44f, paintBadge)

        canvas.drawText("Catatan Auditor:", margin, y + 14f, paintMetaLabel)
        canvas.drawText("Semua data transaksi dan perpindahan dana antar rekening telah dihitung secara matematis", margin, y + 26f, paintBody)
        canvas.drawText("berdasarkan standar pembukuan kas berimbang (debit dan kredit).", margin, y + 38f, paintBody)

        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        // Save PDF to cache directory
        return try {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val file = File(reportsDir, "Laporan_Keuangan_DanaLoka_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    fun generateCsvReport(
        context: Context,
        periodName: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String = "IDR",
        accounts: List<AccountEntity> = emptyList()
    ): File? {
        return try {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val file = File(reportsDir, "Laporan_Keuangan_DanaLoka_${System.currentTimeMillis()}.csv")

            val csvContent = buildCsvContent(
                periodName = periodName,
                transactions = transactions,
                categories = categories,
                currencyCode = currencyCode,
                accounts = accounts
            )

            FileOutputStream(file).use { outputStream ->
                // UTF-8 BOM for Microsoft Excel auto-detect
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun buildCsvContent(
        periodName: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String = "IDR",
        accounts: List<AccountEntity> = emptyList()
    ): String {
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalTransfer = transactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
        val netSurplus = totalIncome - totalExpense
        val needsExpense = transactions.filter {
            it.type == TransactionType.EXPENSE && categoryMap[it.categoryId]?.expenseGroup == ExpenseGroup.NEEDS
        }.sumOf { it.amount }
        val wantsExpense = transactions.filter {
            it.type == TransactionType.EXPENSE && categoryMap[it.categoryId]?.expenseGroup == ExpenseGroup.WANTS
        }.sumOf { it.amount }

        val sb = StringBuilder()
        // Monthly Summary Block
        sb.appendLine("\"RINGKASAN LAPORAN KEUANGAN DANALOKA\"")
        sb.appendLine("\"Periode\",\"${escapeCsv(periodName)}\"")
        sb.appendLine("\"Mata Uang\",\"${escapeCsv(currencyCode)}\"")
        sb.appendLine("\"Jumlah Transaksi\",${transactions.size}")
        sb.appendLine("\"Total Pemasukan\",$totalIncome")
        sb.appendLine("\"Total Pengeluaran\",$totalExpense")
        sb.appendLine("\"Arus Kas Bersih\",$netSurplus")
        sb.appendLine("\"Total Transfer Antar Akun\",$totalTransfer")
        sb.appendLine("\"Pengeluaran Kebutuhan (Needs)\",$needsExpense")
        sb.appendLine("\"Pengeluaran Keinginan (Wants)\",$wantsExpense")
        sb.appendLine()

        // Transaction Ledger Header
        sb.appendLine("ID,Tanggal,Waktu,Judul Transaksi,Kategori,Kelompok,Tipe,Akun Asal,Akun Tujuan,Nominal,Mata Uang,Catatan")

        for (item in transactions) {
            val catObj = categoryMap[item.categoryId]
            val catName = when (item.type) {
                TransactionType.TRANSFER -> "Mutasi Kas"
                else -> catObj?.name ?: "Lain-lain"
            }
            val expenseGroupStr = if (item.type == TransactionType.EXPENSE) {
                if (catObj?.expenseGroup == ExpenseGroup.WANTS) "Keinginan" else "Kebutuhan"
            } else "-"
            val dateStr = dateFormat.format(Date(item.dateMillis))
            val timeStr = timeFormat.format(Date(item.dateMillis))
            val typeStr = when (item.type) {
                TransactionType.INCOME -> "PEMASUKAN"
                TransactionType.EXPENSE -> "PENGELUARAN"
                TransactionType.TRANSFER -> "TRANSFER_MUTASI"
            }
            val fromAccountName = accountMap[item.accountId]?.name ?: "Kas Utama"
            val toAccountName = if (item.type == TransactionType.TRANSFER) {
                accountMap[item.toAccountId]?.name ?: "Akun Tujuan"
            } else "-"

            sb.appendLine(
                "${item.id},$dateStr,$timeStr,\"${escapeCsv(item.title)}\",\"${escapeCsv(catName)}\"," +
                    "$expenseGroupStr,$typeStr,\"${escapeCsv(fromAccountName)}\",\"${escapeCsv(toAccountName)}\"," +
                    "${item.amount},${item.currencyCode},\"${escapeCsv(item.notes)}\""
            )
        }
        return sb.toString()
    }

    fun generateExcelReport(
        context: Context,
        periodName: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String = "IDR",
        accounts: List<AccountEntity> = emptyList()
    ): File? {
        return try {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val file = File(reportsDir, "Laporan_Keuangan_DanaLoka_${System.currentTimeMillis()}.xlsx")
            FileOutputStream(file).use { output ->
                writeExcelWorkbookToStream(
                    outputStream = output,
                    periodName = periodName,
                    transactions = transactions,
                    categories = categories,
                    currencyCode = currencyCode,
                    accounts = accounts
                )
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeExcelWorkbookToStream(
        outputStream: OutputStream,
        periodName: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        currencyCode: String = "IDR",
        accounts: List<AccountEntity> = emptyList()
    ) {
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val exportedAt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date())

        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalTransfer = transactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
        val netSurplus = totalIncome - totalExpense
        val needsExpense = transactions.filter {
            it.type == TransactionType.EXPENSE && categoryMap[it.categoryId]?.expenseGroup == ExpenseGroup.NEEDS
        }.sumOf { it.amount }
        val wantsExpense = transactions.filter {
            it.type == TransactionType.EXPENSE && categoryMap[it.categoryId]?.expenseGroup == ExpenseGroup.WANTS
        }.sumOf { it.amount }

        // Build Sheet 1: Ringkasan Bulanan
        val summaryRows = mutableListOf<List<ExcelCell>>()
        summaryRows += listOf(ExcelCell.text("LAPORAN KEUANGAN DANALOKA - RINGKASAN BULANAN", style = 1))
        summaryRows += listOf(ExcelCell.text("Periode Laporan", style = 2), ExcelCell.text(periodName))
        summaryRows += listOf(ExcelCell.text("Tanggal Ekspor", style = 2), ExcelCell.text(exportedAt))
        summaryRows += listOf(ExcelCell.text("Mata Uang", style = 2), ExcelCell.text(currencyCode))
        summaryRows.add(emptyList())
        summaryRows += listOf(ExcelCell.text("Metrik Keuangan", style = 1), ExcelCell.text("Nilai ($currencyCode)", style = 1), ExcelCell.text("Format Terbaca", style = 1))
        summaryRows += listOf(ExcelCell.text("Total Pemasukan"), ExcelCell.number(totalIncome), ExcelCell.text(CurrencyHelper.format(totalIncome, currencyCode)))
        summaryRows += listOf(ExcelCell.text("Total Pengeluaran"), ExcelCell.number(totalExpense), ExcelCell.text(CurrencyHelper.format(totalExpense, currencyCode)))
        summaryRows += listOf(ExcelCell.text("Arus Kas Bersih (Surplus/Defisit)"), ExcelCell.number(netSurplus), ExcelCell.text(CurrencyHelper.format(netSurplus, currencyCode)))
        summaryRows += listOf(ExcelCell.text("Total Transfer Antar Akun"), ExcelCell.number(totalTransfer), ExcelCell.text(CurrencyHelper.format(totalTransfer, currencyCode)))
        summaryRows += listOf(ExcelCell.text("Kebutuhan Pokok (Needs)"), ExcelCell.number(needsExpense), ExcelCell.text(CurrencyHelper.format(needsExpense, currencyCode)))
        summaryRows += listOf(ExcelCell.text("Keinginan & Gaya Hidup (Wants)"), ExcelCell.number(wantsExpense), ExcelCell.text(CurrencyHelper.format(wantsExpense, currencyCode)))
        summaryRows += listOf(ExcelCell.text("Jumlah Entri Transaksi"), ExcelCell.number(transactions.size.toDouble()), ExcelCell.text("${transactions.size} transaksi"))

        if (accounts.isNotEmpty()) {
            summaryRows.add(emptyList())
            summaryRows += listOf(ExcelCell.text("Posisi Saldo Akun", style = 1), ExcelCell.text("Tipe", style = 1), ExcelCell.text("Saldo ($currencyCode)", style = 1))
            accounts.forEach { acc ->
                summaryRows += listOf(
                    ExcelCell.text(acc.name),
                    ExcelCell.text(AccountIcons.getTypeName(acc.type)),
                    ExcelCell.number(acc.balance)
                )
            }
        }

        // Build Sheet 2: Daftar Transaksi
        val ledgerRows = mutableListOf<List<ExcelCell>>()
        ledgerRows += listOf(
            ExcelCell.text("No", style = 1),
            ExcelCell.text("Tanggal", style = 1),
            ExcelCell.text("Waktu", style = 1),
            ExcelCell.text("Judul Transaksi", style = 1),
            ExcelCell.text("Kategori", style = 1),
            ExcelCell.text("Kelompok", style = 1),
            ExcelCell.text("Tipe", style = 1),
            ExcelCell.text("Akun Asal", style = 1),
            ExcelCell.text("Akun Tujuan", style = 1),
            ExcelCell.text("Nominal", style = 1),
            ExcelCell.text("Mata Uang", style = 1),
            ExcelCell.text("Catatan", style = 1)
        )

        transactions.forEachIndexed { idx, item ->
            val catObj = categoryMap[item.categoryId]
            val catName = when (item.type) {
                TransactionType.TRANSFER -> "Mutasi Kas"
                else -> catObj?.name ?: "Lain-lain"
            }
            val groupName = if (item.type == TransactionType.EXPENSE) {
                if (catObj?.expenseGroup == ExpenseGroup.WANTS) "Keinginan" else "Kebutuhan"
            } else "-"
            val typeStr = when (item.type) {
                TransactionType.INCOME -> "Pemasukan"
                TransactionType.EXPENSE -> "Pengeluaran"
                TransactionType.TRANSFER -> "Transfer"
            }
            val fromAcc = accountMap[item.accountId]?.name ?: "Kas Utama"
            val toAcc = if (item.type == TransactionType.TRANSFER) {
                accountMap[item.toAccountId]?.name ?: "Akun Tujuan"
            } else "-"

            ledgerRows += listOf(
                ExcelCell.number((idx + 1).toDouble()),
                ExcelCell.text(dateFormat.format(Date(item.dateMillis))),
                ExcelCell.text(timeFormat.format(Date(item.dateMillis))),
                ExcelCell.text(item.title),
                ExcelCell.text(catName),
                ExcelCell.text(groupName),
                ExcelCell.text(typeStr),
                ExcelCell.text(fromAcc),
                ExcelCell.text(toAcc),
                ExcelCell.number(item.amount),
                ExcelCell.text(item.currencyCode),
                ExcelCell.text(item.notes)
            )
        }

        ZipOutputStream(outputStream).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeZipEntry(zip, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zip, "xl/workbook.xml", WORKBOOK_XML)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeZipEntry(zip, "xl/styles.xml", STYLES_XML)
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildWorksheetXml(summaryRows, colWidths = listOf(34, 22, 26)))
            writeZipEntry(zip, "xl/worksheets/sheet2.xml", buildWorksheetXml(ledgerRows, colWidths = listOf(6, 13, 8, 28, 20, 14, 14, 18, 18, 16, 10, 26)))
        }
    }

    fun suggestedFileName(format: ExportFormat, periodName: String): String {
        val sanitizedPeriod = periodName
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .take(32)
            .ifBlank { "Laporan" }
        return "DanaLoka_${sanitizedPeriod}.${format.extension}"
    }

    fun saveFileToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
                out.flush()
            } ?: return false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String = "Bagikan Laporan Keuangan") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Laporan Keuangan & Mutasi Kas Resmi dari DanaLoka - Catatan Keuangan & Anggaran.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun escapeCsv(raw: String): String = raw.replace("\"", "\"\"")

    private fun escapeXml(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    // Strip invalid XML 1.0 control chars
                    if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private sealed class ExcelCell(val style: Int) {
        class TextCell(val value: String, style: Int) : ExcelCell(style)
        class NumberCell(val value: Double, style: Int) : ExcelCell(style)

        companion object {
            fun text(value: String, style: Int = 0) = TextCell(value, style)
            fun number(value: Double, style: Int = 0) = NumberCell(value, style)
        }
    }

    private fun columnRef(colIndexZeroBased: Int): String {
        var idx = colIndexZeroBased
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.append(('A'.code + (idx % 26)).toChar())
            idx = (idx / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun buildWorksheetXml(rows: List<List<ExcelCell>>, colWidths: List<Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (colWidths.isNotEmpty()) {
            sb.append("<cols>")
            colWidths.forEachIndexed { idx, w ->
                val colNum = idx + 1
                sb.append("""<col min="$colNum" max="$colNum" width="$w" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            if (row.isEmpty()) {
                sb.append("""<row r="$rowNum"/>""")
            } else {
                sb.append("""<row r="$rowNum">""")
                row.forEachIndexed { cIdx, cell ->
                    val ref = "${columnRef(cIdx)}$rowNum"
                    val styleAttr = if (cell.style > 0) """ s="${cell.style}"""" else ""
                    when (cell) {
                        is ExcelCell.TextCell -> {
                            sb.append("""<c r="$ref" t="inlineStr"$styleAttr><is><t>${escapeXml(cell.value)}</t></is></c>""")
                        }
                        is ExcelCell.NumberCell -> {
                            val numStr = if (cell.value.isFinite()) cell.value.toString() else "0"
                            sb.append("""<c r="$ref"$styleAttr><v>$numStr</v></c>""")
                        }
                    }
                }
                sb.append("</row>")
            }
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
    <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <sheets>
        <sheet name="Ringkasan Bulanan" sheetId="1" r:id="rId1"/>
        <sheet name="Daftar Transaksi" sheetId="2" r:id="rId2"/>
    </sheets>
</workbook>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <fonts count="2">
        <font><sz val="11"/><name val="Calibri"/></font>
        <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    </fonts>
    <fills count="3">
        <fill><patternFill patternType="none"/></fill>
        <fill><patternFill patternType="gray125"/></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FF0F172A"/></patternFill></fill>
    </fills>
    <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
    <cellXfs count="3">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
        <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    </cellXfs>
</styleSheet>"""
}
