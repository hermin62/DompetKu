package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.util.CurrencyHelper

data class MonthlyBarData(
    val monthLabel: String,
    val income: Double,
    val expense: Double
)

@Composable
fun MonthlyBarChart(
    monthlyData: List<MonthlyBarData>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (monthlyData.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(monthlyData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(900))
    }

    val maxAmount = remember(monthlyData) {
        val peak = monthlyData.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0
        if (peak <= 0.0) 1.0 else peak * 1.15
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_trend_chart_card"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tren Keuangan Bulanan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GreenIncome))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Masuk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RedExpense))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Keluar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Selected Detail banner
            val selectedItem = selectedIndex?.let { monthlyData.getOrNull(it) }
            if (selectedItem != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedItem.monthLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "+${CurrencyHelper.format(selectedItem.income, currencyCode)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenIncome
                            )
                            Text(
                                text = "-${CurrencyHelper.format(selectedItem.expense, currencyCode)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RedExpense
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart Canvas
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 24.dp.toPx()
                val count = monthlyData.size
                val groupWidth = chartWidth / count
                val barWidth = (groupWidth * 0.28f).coerceAtMost(22.dp.toPx())

                // Draw horizontal guide lines
                for (i in 1..3) {
                    val y = chartHeight * (i / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw bars
                monthlyData.forEachIndexed { index, item ->
                    val groupCenter = groupWidth * index + groupWidth / 2f

                    // Income bar
                    val incomeHeight = ((item.income / maxAmount) * chartHeight * animationProgress.value).toFloat()
                    val incomeLeft = groupCenter - barWidth - 2.dp.toPx()
                    val incomeTop = chartHeight - incomeHeight

                    if (incomeHeight > 0f) {
                        drawRoundRect(
                            color = GreenIncome,
                            topLeft = Offset(incomeLeft, incomeTop),
                            size = Size(barWidth, incomeHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    // Expense bar
                    val expenseHeight = ((item.expense / maxAmount) * chartHeight * animationProgress.value).toFloat()
                    val expenseLeft = groupCenter + 2.dp.toPx()
                    val expenseTop = chartHeight - expenseHeight

                    if (expenseHeight > 0f) {
                        drawRoundRect(
                            color = RedExpense,
                            topLeft = Offset(expenseLeft, expenseTop),
                            size = Size(barWidth, expenseHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }

            // Month Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                monthlyData.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    Text(
                        text = item.monthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                selectedIndex = if (isSelected) null else index
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
