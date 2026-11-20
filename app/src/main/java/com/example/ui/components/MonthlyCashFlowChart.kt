package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.util.CurrencyHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class MonthlyCashFlowData(
    val monthIndex: Int,
    val year: Int,
    val monthShortName: String,
    val monthFullName: String,
    val income: Double,
    val expense: Double,
    val netCashFlow: Double = income - expense
)

object MonthlyCashFlowAggregator {

    fun buildRollingSeries(
        transactions: List<TransactionEntity>,
        monthsCount: Int = 6,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MonthlyCashFlowData> {
        val localeId = Locale("id", "ID")
        val shortSdf = SimpleDateFormat("MMM", localeId)
        val fullSdf = SimpleDateFormat("MMMM yyyy", localeId)

        val months = mutableListOf<MonthlyCashFlowData>()
        for (i in (monthsCount - 1) downTo 0) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -i)
            }
            val m = cal.get(Calendar.MONTH)
            val y = cal.get(Calendar.YEAR)

            val txThisMonth = transactions.filter { tx ->
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
                txCal.get(Calendar.MONTH) == m && txCal.get(Calendar.YEAR) == y
            }

            val income = txThisMonth
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }
            val expense = txThisMonth
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }

            months.add(
                MonthlyCashFlowData(
                    monthIndex = m,
                    year = y,
                    monthShortName = shortSdf.format(cal.time),
                    monthFullName = fullSdf.format(cal.time),
                    income = income,
                    expense = expense
                )
            )
        }
        return months
    }

    fun formatCompactAxisLabel(amount: Double, currencyCode: String): String {
        val symbol = CurrencyHelper.getSymbol(currencyCode)
        return when {
            amount <= 0.0 -> "0"
            amount >= 1_000_000_000.0 -> String.format(Locale.US, "%s %.1fM", symbol, amount / 1_000_000_000.0)
            amount >= 1_000_000.0 -> {
                val millions = amount / 1_000_000.0
                if (millions % 1.0 == 0.0) "$symbol ${millions.toInt()}jt"
                else String.format(Locale.US, "%s %.1fjt", symbol, millions)
            }
            amount >= 1_000.0 -> "$symbol ${(amount / 1_000.0).roundToInt()}rb"
            else -> "$symbol ${amount.roundToInt()}"
        }
    }
}

/**
 * Recharts-style Monthly Cash Flow Visualization Component for Jetpack Compose.
 * Includes Y-Axis scale ticks, dashed CartesianGrid, grouped animated Income vs Expense bars,
 * direct Canvas tap-to-inspect + clickable X-Axis labels, interactive Tooltip card, and Legend.
 */
@Composable
fun MonthlyCashFlowChart(
    transactions: List<TransactionEntity>,
    activeCurrency: String,
    modifier: Modifier = Modifier
) {
    val monthlyData = remember(transactions) {
        MonthlyCashFlowAggregator.buildRollingSeries(transactions, monthsCount = 6)
    }

    var selectedIndex by remember(monthlyData.size) {
        mutableStateOf<Int?>(monthlyData.lastIndex.takeIf { it >= 0 })
    }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(monthlyData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(800))
    }

    val maxAmount = remember(monthlyData) {
        val peak = monthlyData.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0.0
        if (peak <= 0.0) 1_000_000.0 else peak * 1.2
    }

    val totalSixMonthIncome = remember(monthlyData) { monthlyData.sumOf { it.income } }
    val totalSixMonthExpense = remember(monthlyData) { monthlyData.sumOf { it.expense } }

    val currentMonthData = monthlyData.lastOrNull()
    val currentNet = currentMonthData?.netCashFlow ?: 0.0
    val isCurrentNetSurplus = currentNet >= 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_cashflow_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Title & Net Cashflow Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Grafik Arus Kas",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Grafik Arus Kas Bulanan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Pendapatan vs Pengeluaran (6 bulan terakhir)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCurrentNetSurplus) Color(0xFF10B981).copy(alpha = 0.15f)
                    else Color(0xFFEF4444).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrentNetSurplus) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (isCurrentNetSurplus) Color(0xFF047857) else Color(0xFFDC2626),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isCurrentNetSurplus) "Surplus" else "Defisit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentNetSurplus) Color(0xFF047857) else Color(0xFFDC2626)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Recharts-style Tooltip Card
            val selectedItem = selectedIndex?.let { monthlyData.getOrNull(it) } ?: currentMonthData
            if (selectedItem != null) {
                val net = selectedItem.netCashFlow
                val isSurplus = net >= 0.0

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashflow_tooltip_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedItem.monthFullName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Arus Bersih: ${if (isSurplus) "+" else ""}${CurrencyHelper.format(net, activeCurrency)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSurplus) Color(0xFF047857) else Color(0xFFDC2626)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Masuk: ${CurrencyHelper.format(selectedItem.income, activeCurrency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF43F5E)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Keluar: ${CurrencyHelper.format(selectedItem.expense, activeCurrency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFBE123C),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chart Body: YAxis labels + CartesianGrid + Grouped Bars with Tap Detection
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            val baselineColor = MaterialTheme.colorScheme.outlineVariant
            val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            val yAxisLabelWidth = 52.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Y-Axis Ticks
                Column(
                    modifier = Modifier
                        .width(yAxisLabelWidth)
                        .fillMaxHeight()
                        .padding(bottom = 8.dp, end = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = MonthlyCashFlowAggregator.formatCompactAxisLabel(maxAmount, activeCurrency),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = MonthlyCashFlowAggregator.formatCompactAxisLabel(maxAmount * 0.5, activeCurrency),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "0",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }

                // Plot Area Canvas
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(monthlyData) {
                            detectTapGestures { offset ->
                                if (monthlyData.isNotEmpty() && size.width > 0) {
                                    val groupWidth = size.width.toFloat() / monthlyData.size
                                    val tappedIndex = (offset.x / groupWidth)
                                        .toInt()
                                        .coerceIn(0, monthlyData.lastIndex)
                                    selectedIndex = tappedIndex
                                }
                            }
                        }
                        .testTag("cashflow_chart_canvas")
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height - 10.dp.toPx()
                    val count = monthlyData.size.coerceAtLeast(1)
                    val groupWidth = chartWidth / count
                    val barWidth = (groupWidth * 0.30f).coerceIn(7.dp.toPx(), 18.dp.toPx())
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                    // Dashed CartesianGrid lines (top, 75%, 50%, 25%) + solid baseline
                    for (step in 0..3) {
                        val y = chartHeight * (step / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }
                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, chartHeight),
                        end = Offset(chartWidth, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    monthlyData.forEachIndexed { index, item ->
                        val isSelected = selectedIndex == index
                        val groupCenter = groupWidth * index + groupWidth / 2f

                        if (isSelected) {
                            drawRoundRect(
                                color = highlightColor,
                                topLeft = Offset(groupWidth * index + 3.dp.toPx(), 0f),
                                size = Size((groupWidth - 6.dp.toPx()).coerceAtLeast(1f), chartHeight),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }

                        // Income bar (Green)
                        val incomeHeight = ((item.income / maxAmount) * chartHeight * animationProgress.value)
                            .toFloat()
                            .coerceIn(0f, chartHeight)
                        val incomeLeft = groupCenter - barWidth - 1.5.dp.toPx()
                        val incomeTop = chartHeight - incomeHeight

                        if (incomeHeight > 0f) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF34D399), Color(0xFF059669))
                                ),
                                topLeft = Offset(incomeLeft, incomeTop),
                                size = Size(barWidth, incomeHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        } else {
                            drawCircle(
                                color = Color(0xFF10B981).copy(alpha = 0.4f),
                                radius = 2.dp.toPx(),
                                center = Offset(incomeLeft + barWidth / 2f, chartHeight - 2.dp.toPx())
                            )
                        }

                        // Expense bar (Rose/Red)
                        val expenseHeight = ((item.expense / maxAmount) * chartHeight * animationProgress.value)
                            .toFloat()
                            .coerceIn(0f, chartHeight)
                        val expenseLeft = groupCenter + 1.5.dp.toPx()
                        val expenseTop = chartHeight - expenseHeight

                        if (expenseHeight > 0f) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFB7185), Color(0xFFE11D48))
                                ),
                                topLeft = Offset(expenseLeft, expenseTop),
                                size = Size(barWidth, expenseHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        } else {
                            drawCircle(
                                color = Color(0xFFF43F5E).copy(alpha = 0.4f),
                                radius = 2.dp.toPx(),
                                center = Offset(expenseLeft + barWidth / 2f, chartHeight - 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            // X-Axis Month Selectors aligned with Plot Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = yAxisLabelWidth),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                monthlyData.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedIndex = index
                            }
                            .testTag("cashflow_month_$index"),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.monthShortName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Legend + 6-Month Totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Masuk (${MonthlyCashFlowAggregator.formatCompactAxisLabel(totalSixMonthIncome, activeCurrency)})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF43F5E)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Keluar (${MonthlyCashFlowAggregator.formatCompactAxisLabel(totalSixMonthExpense, activeCurrency)})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
