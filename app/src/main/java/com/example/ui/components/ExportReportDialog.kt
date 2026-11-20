package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.util.CurrencyHelper
import com.example.util.DateUtils
import com.example.util.ExportFormat
import java.util.Calendar

enum class ExportPeriodOption(val label: String, val description: String) {
    THIS_MONTH("Bulan Ini", "Transaksi di bulan kalender saat ini"),
    LAST_MONTH("Bulan Lalu", "Transaksi di 1 bulan kalender sebelumnya"),
    LAST_30_DAYS("30 Hari Terakhir", "Transaksi dari 30 hari ke belakang"),
    THIS_YEAR("Tahun Ini", "Semua transaksi di tahun kalender ini"),
    ALL_TIME("Semua Waktu", "Seluruh riwayat transaksi tanpa batasan tanggal"),
    CUSTOM_RANGE("Rentang Kustom", "Pilih sendiri tanggal mulai dan selesai")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportDialog(
    allTransactions: List<TransactionEntity>,
    activeCurrency: String,
    onDismiss: () -> Unit,
    onExport: (format: ExportFormat, saveToDevice: Boolean, periodLabel: String, filteredTransactions: List<TransactionEntity>) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var selectedPeriod by remember { mutableStateOf(ExportPeriodOption.THIS_MONTH) }

    val now = remember { System.currentTimeMillis() }
    val lastMonthMillis = remember { DateUtils.getLastMonthMillis() }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var customStartDateMillis by remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        mutableStateOf(cal.timeInMillis)
    }
    var customEndDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Calculate filtered transactions based on selected period
    val (filteredList, periodLabel) = remember(
        allTransactions,
        selectedPeriod,
        customStartDateMillis,
        customEndDateMillis
    ) {
        when (selectedPeriod) {
            ExportPeriodOption.THIS_MONTH -> {
                val list = allTransactions.filter { DateUtils.isCurrentMonth(it.dateMillis) }
                val label = "Bulan Ini (${DateUtils.formatMonthYear(now)})"
                Pair(list, label)
            }
            ExportPeriodOption.LAST_MONTH -> {
                val list = allTransactions.filter { DateUtils.isLastMonth(it.dateMillis) }
                val label = "Bulan Lalu (${DateUtils.formatMonthYear(lastMonthMillis)})"
                Pair(list, label)
            }
            ExportPeriodOption.LAST_30_DAYS -> {
                val thirtyDaysAgo = now - 30L * 86_400_000L
                val list = allTransactions.filter { it.dateMillis >= thirtyDaysAgo }
                val label = "30 Hari Terakhir"
                Pair(list, label)
            }
            ExportPeriodOption.THIS_YEAR -> {
                val list = allTransactions.filter { DateUtils.isCurrentYear(it.dateMillis) }
                val label = "Tahun $currentYear"
                Pair(list, label)
            }
            ExportPeriodOption.ALL_TIME -> {
                Pair(allTransactions, "Semua Waktu")
            }
            ExportPeriodOption.CUSTOM_RANGE -> {
                val start = DateUtils.getStartOfDay(customStartDateMillis)
                val end = DateUtils.getEndOfDay(customEndDateMillis)
                val list = allTransactions.filter { it.dateMillis in start..end }
                val label = "${DateUtils.formatDate(start)} s/d ${DateUtils.formatDate(end)}"
                Pair(list, label)
            }
        }
    }

    val totalIncome = remember(filteredList) {
        filteredList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }
    val totalExpense = remember(filteredList) {
        filteredList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .heightIn(max = 700.dp)
                .testTag("export_report_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ekspor Laporan Keuangan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Unduh atau bagikan ringkasan bulanan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: FORMAT SELECTION (PDF, EXCEL .xlsx, CSV)
                    item {
                        Text(
                            text = "Format File Laporan:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportFormatCard(
                                title = "PDF",
                                subtitle = "Siap cetak (.pdf)",
                                icon = Icons.Default.PictureAsPdf,
                                selected = selectedFormat == ExportFormat.PDF,
                                onClick = { selectedFormat = ExportFormat.PDF },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("format_pdf_btn")
                            )
                            ExportFormatCard(
                                title = "Excel",
                                subtitle = "Workbook (.xlsx)",
                                icon = Icons.Default.TableChart,
                                selected = selectedFormat == ExportFormat.EXCEL,
                                onClick = { selectedFormat = ExportFormat.EXCEL },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("format_excel_btn")
                            )
                            ExportFormatCard(
                                title = "CSV",
                                subtitle = "Tabel teks (.csv)",
                                icon = Icons.Default.Description,
                                selected = selectedFormat == ExportFormat.CSV,
                                onClick = { selectedFormat = ExportFormat.CSV },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("format_csv_btn")
                            )
                        }
                    }

                    // SECTION 2: PILIH PERIODE LAPORAN
                    item {
                        Text(
                            text = "Pilih Periode Laporan:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportPeriodOption.entries.forEach { option ->
                                val isSelected = selectedPeriod == option
                                Surface(
                                    onClick = { selectedPeriod = option },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("period_option_${option.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = option.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = option.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedPeriod = option },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 3: CUSTOM DATE RANGE PICKER (IF CUSTOM SELECTED)
                    if (selectedPeriod == ExportPeriodOption.CUSTOM_RANGE) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Tentukan Tanggal Mulai & Tanggal Selesai:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showStartDatePicker = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("custom_start_date_btn")
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Mulai", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(DateUtils.formatShortDate(customStartDateMillis), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { showEndDatePicker = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("custom_end_date_btn")
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Selesai", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(DateUtils.formatShortDate(customEndDateMillis), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 4: RINGKASAN DATA TERPILIH
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_summary_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Periode Terpilih:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = periodLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Jumlah Transaksi:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${filteredList.size} transaksi",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (filteredList.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (filteredList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total Pemasukan:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "+${CurrencyHelper.format(totalIncome, activeCurrency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenIncome
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total Pengeluaran:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "-${CurrencyHelper.format(totalExpense, activeCurrency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = RedExpense
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tidak ada catatan transaksi pada periode yang dipilih.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Download to Device (SAF) + Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onExport(selectedFormat, false, periodLabel, filteredList)
                            onDismiss()
                        },
                        enabled = filteredList.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_export_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bagikan",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = {
                            onExport(selectedFormat, true, periodLabel, filteredList)
                            onDismiss()
                        },
                        enabled = filteredList.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("confirm_export_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unduh ${selectedFormat.shortLabel}",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customStartDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        customStartDateMillis = it
                        if (customStartDateMillis > customEndDateMillis) {
                            customEndDateMillis = it
                        }
                    }
                    showStartDatePicker = false
                }) {
                    Text("Pilih Tanggal Mulai")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customEndDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        customEndDateMillis = it
                        if (customEndDateMillis < customStartDateMillis) {
                            customStartDateMillis = it
                        }
                    }
                    showEndDatePicker = false
                }) {
                    Text("Pilih Tanggal Selesai")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ExportFormatCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.5.dp,
            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
