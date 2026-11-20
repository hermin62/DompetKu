package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CategoryEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.components.CategoryExpenseItem
import com.example.ui.components.DonutExpenseChart
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.util.CurrencyHelper
import com.example.util.DateUtils

@Composable
fun AnalyticsScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    activeCurrency: String,
    overallMonthlyBudget: Double,
    onExportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val monthlyTransactions = remember(transactions) {
        transactions.filter { DateUtils.isCurrentMonth(it.dateMillis) }
    }

    val totalIncome = remember(monthlyTransactions) {
        monthlyTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }
    val totalExpense = remember(monthlyTransactions) {
        monthlyTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    val categoryExpenseItems = remember(monthlyTransactions, categories, totalExpense) {
        if (totalExpense <= 0.0) emptyList()
        else {
            val expenseMap = monthlyTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            expenseMap.mapNotNull { (catId, amt) ->
                val cat = categoryMap[catId]
                if (cat != null) {
                    val color = try {
                        Color(android.graphics.Color.parseColor(cat.colorHex))
                    } catch (e: Exception) {
                        Color(0xFF0D9488)
                    }
                    CategoryExpenseItem(
                        categoryId = catId,
                        name = cat.name,
                        amount = amt,
                        percentage = (amt / totalExpense).toFloat(),
                        color = color,
                        iconKey = cat.iconKey,
                        monthlyBudget = cat.monthlyBudget
                    )
                } else null
            }.sortedByDescending { it.amount }
        }
    }

    val expenseCats = remember(categories) {
        categories.filter { it.type == TransactionType.EXPENSE && it.monthlyBudget > 0.0 }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analisis Pengeluaran",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
                if (onExportClick != null) {
                    FilledTonalButton(
                        onClick = onExportClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("analytics_export_report_btn")
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekspor", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Cash flow summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Pemasukan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+${CurrencyHelper.format(totalIncome, activeCurrency)}", fontWeight = FontWeight.Bold, color = GreenIncome)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pengeluaran", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-${CurrencyHelper.format(totalExpense, activeCurrency)}", fontWeight = FontWeight.Bold, color = RedExpense)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val net = totalIncome - totalExpense
                        Text("Arus Kas Net", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyHelper.format(net, activeCurrency),
                            fontWeight = FontWeight.Bold,
                            color = if (net >= 0) GreenIncome else RedExpense
                        )
                    }
                }
            }
        }

        // Donut Chart with Total Budget prominently integrated
        item {
            DonutExpenseChart(
                items = categoryExpenseItems,
                totalExpense = totalExpense,
                currencyCode = activeCurrency,
                totalBudget = overallMonthlyBudget
            )
        }

        // Category Budget Tracking
        if (expenseCats.isNotEmpty()) {
            item {
                Text(
                    text = "Realisasi Anggaran Per Kategori",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(expenseCats, key = { it.id }) { cat ->
                val spent = monthlyTransactions.filter { it.categoryId == cat.id }.sumOf { it.amount }
                val ratio = (spent / cat.monthlyBudget).coerceIn(0.0, 2.0).toFloat()
                val isOver = spent > cat.monthlyBudget

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${CurrencyHelper.format(spent, activeCurrency)} / ${CurrencyHelper.format(cat.monthlyBudget, activeCurrency)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOver) RedExpense else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { ratio.coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (isOver) RedExpense else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(70.dp)) }
    }
}
