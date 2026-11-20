package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.util.CategoryIcons
import com.example.util.CurrencyHelper
import com.example.util.DateUtils
import java.util.Calendar

data class DayData(
    val dayOfMonth: Int,
    val dateMillis: Long,
    val income: Double,
    val expense: Double,
    val transactions: List<TransactionEntity>,
    val isToday: Boolean
)

@Composable
fun FinancialCalendarScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    activeCurrency: String,
    onAddTransactionWithDate: (Long) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var calendarDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDayOfMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    val currentYear = calendarDate.get(Calendar.YEAR)
    val currentMonth = calendarDate.get(Calendar.MONTH)

    val daysInMonth = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SUNDAY) 6 else dow - 2
    }

    // Single O(N) pass to bucket transactions by day-of-month instead of O(31 * N) scans
    val daysDataList = remember(transactions, currentYear, currentMonth, daysInMonth) {
        val today = Calendar.getInstance()
        val scratchCal = Calendar.getInstance()
        val bucketed = HashMap<Int, MutableList<TransactionEntity>>(daysInMonth)
        val incomeSums = DoubleArray(daysInMonth + 1)
        val expenseSums = DoubleArray(daysInMonth + 1)

        for (tx in transactions) {
            scratchCal.timeInMillis = tx.dateMillis
            if (scratchCal.get(Calendar.YEAR) == currentYear &&
                scratchCal.get(Calendar.MONTH) == currentMonth
            ) {
                val dom = scratchCal.get(Calendar.DAY_OF_MONTH)
                if (dom in 1..daysInMonth) {
                    bucketed.getOrPut(dom) { mutableListOf() }.add(tx)
                    when (tx.type) {
                        TransactionType.INCOME -> incomeSums[dom] += tx.amount
                        TransactionType.EXPENSE -> expenseSums[dom] += tx.amount
                        TransactionType.TRANSFER -> Unit
                    }
                }
            }
        }

        val todayMatchesMonth =
            today.get(Calendar.YEAR) == currentYear && today.get(Calendar.MONTH) == currentMonth
        val todayDom = today.get(Calendar.DAY_OF_MONTH)

        (1..daysInMonth).map { day ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            DayData(
                dayOfMonth = day,
                dateMillis = cal.timeInMillis,
                income = incomeSums[day],
                expense = expenseSums[day],
                transactions = bucketed[day] ?: emptyList(),
                isToday = todayMatchesMonth && todayDom == day
            )
        }
    }

    val selectedDayData = remember(daysDataList, selectedDayOfMonth) {
        daysDataList.find { it.dayOfMonth == selectedDayOfMonth }
    }

    val totalMonthIncome = remember(daysDataList) { daysDataList.sumOf { it.income } }
    val totalMonthExpense = remember(daysDataList) { daysDataList.sumOf { it.expense } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Month Navigation Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newCal = (calendarDate.clone() as Calendar).apply {
                                add(Calendar.MONTH, -1)
                            }
                            calendarDate = newCal
                            selectedDayOfMonth = 1
                        }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Bulan Sebelumnya")
                    }

                    Text(
                        text = DateUtils.formatMonthYear(calendarDate.timeInMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = {
                            val newCal = (calendarDate.clone() as Calendar).apply {
                                add(Calendar.MONTH, 1)
                            }
                            calendarDate = newCal
                            selectedDayOfMonth = 1
                        }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bulan Berikutnya")
                    }
                }
            }
        }

        // Monthly Totals Banner
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Pemasukan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "+${CurrencyHelper.format(totalMonthIncome, activeCurrency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenIncome
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Pengeluaran", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "-${CurrencyHelper.format(totalMonthExpense, activeCurrency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = RedExpense
                        )
                    }
                }
            }
        }

        // Interactive Calendar Grid
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val dayNames = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        dayNames.forEach { name ->
                            Text(
                                text = name,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (name == "Min") RedExpense else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val totalCells = firstDayOfWeek + daysInMonth
                    val numRows = (totalCells + 6) / 7

                    for (row in 0 until numRows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNumber = cellIndex - firstDayOfWeek + 1

                                if (dayNumber in 1..daysInMonth) {
                                    val dayData = daysDataList[dayNumber - 1]
                                    val isSelected = dayNumber == selectedDayOfMonth

                                    CalendarDayCell(
                                        dayData = dayData,
                                        isSelected = isSelected,
                                        onClick = { selectedDayOfMonth = dayNumber },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(0.9f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Details
        if (selectedDayData != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.formatFullDate(selectedDayData.dateMillis),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { onAddTransactionWithDate(selectedDayData.dateMillis) }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah")
                        }
                    }

                    // Daily Income & Expense Summary Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calendar_selected_day_summary")
                    ) {
                        val dayNet = selectedDayData.income - selectedDayData.expense
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Pemasukan Hari Ini",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "+ ${CurrencyHelper.format(selectedDayData.income, activeCurrency)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenIncome,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("calendar_day_income")
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "Pengeluaran Hari Ini",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "- ${CurrencyHelper.format(selectedDayData.expense, activeCurrency)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RedExpense,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("calendar_day_expense")
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedDayData.transactions.size} transaksi pada tanggal ini",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val netPrefix = if (dayNet >= 0.0) "+ " else "- "
                                Text(
                                    text = "Selisih: $netPrefix${CurrencyHelper.format(kotlin.math.abs(dayNet), activeCurrency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dayNet >= 0.0) GreenIncome else RedExpense,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            val dayTxs = selectedDayData.transactions
            if (dayTxs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tidak ada transaksi pada tanggal ini",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = dayTxs,
                    key = { it.id },
                    contentType = { "calendar_tx" }
                ) { tx ->
                    val category = categoryMap[tx.categoryId]
                    val primaryCol = MaterialTheme.colorScheme.primary
                    val catColor = remember(category?.colorHex, primaryCol) {
                        try {
                            Color(android.graphics.Color.parseColor(category?.colorHex ?: "#0D9488"))
                        } catch (_: Exception) {
                            primaryCol
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onTransactionClick(tx) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(catColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CategoryIcons.getIcon(category?.iconKey ?: "category"),
                                    contentDescription = null,
                                    tint = catColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = tx.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                                Text(
                                    text = "${category?.name ?: "Umum"} • ${DateUtils.formatTime(tx.dateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }

                            val isIncome = tx.type == TransactionType.INCOME
                            val prefix = if (isIncome) "+ " else "- "
                            val amountColor = if (isIncome) GreenIncome else RedExpense

                            Text(
                                text = prefix + CurrencyHelper.format(tx.amount, tx.currencyCode),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(70.dp)) }
    }
}

@Composable
private fun CalendarDayCell(
    dayData: DayData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else if (dayData.isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.aspectRatio(0.9f)
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayData.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected || dayData.isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else if (dayData.isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )

            if (dayData.income > 0 || dayData.expense > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (dayData.income > 0) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(GreenIncome)
                        )
                    }
                    if (dayData.expense > 0) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(RedExpense)
                        )
                    }
                }
            }
        }
    }
}
