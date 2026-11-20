package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.CategoryExpenseItem
import com.example.ui.components.DonutExpenseChart
import com.example.ui.components.MonthlyCashFlowChart
import com.example.ui.components.WeeklyGeminiInsightCard
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.ui.viewmodel.WeeklyInsightUiState
import com.example.util.BillNotificationHelper
import com.example.util.CategoryIcons
import com.example.util.CurrencyHelper
import com.example.util.DateUtils

@Composable
fun DashboardScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    debts: List<DebtLoanEntity> = emptyList(),
    bills: List<BillReminderEntity> = emptyList(),
    activeCurrency: String,
    overallMonthlyBudget: Double,
    onAddTransaction: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToBills: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit,
    onExportReport: (() -> Unit)? = null,
    weeklyMetrics: WeeklySpendingMetrics? = null,
    weeklyInsightState: WeeklyInsightUiState = WeeklyInsightUiState.Idle,
    selectedInsightPeriod: InsightPeriod = InsightPeriod.WEEKLY,
    onSelectInsightPeriod: (InsightPeriod) -> Unit = {},
    onRefreshWeeklyInsight: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalBalance = remember(accounts) { accounts.sumOf { it.balance } }

    val now = remember { System.currentTimeMillis() }
    val urgentDebts = remember(debts, now) {
        debts.filter { !it.isSettled && (it.dueDateMillis <= now + 3 * 86400000L) }
    }
    val dueSoonBills = remember(bills) {
        BillNotificationHelper.findDueSoonBills(bills, reminderDaysBefore = 3)
    }

    val monthlyTransactions = remember(transactions) {
        transactions.filter { DateUtils.isCurrentMonth(it.dateMillis) }
    }

    val budgetAlerts = remember(categories, transactions) {
        BudgetAlertHelper.extractAlerts(
            BudgetAlertHelper.evaluateExpenseCategories(categories, transactions)
        )
    }

    val totalMonthlyExpense = remember(monthlyTransactions) {
        monthlyTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    val totalMonthlyIncome = remember(monthlyTransactions) {
        monthlyTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val recentList = remember(transactions) { transactions.take(5) }

    // Prepare Category Items for Donut Chart
    val categoryExpenseItems = remember(monthlyTransactions, categories, totalMonthlyExpense) {
        if (totalMonthlyExpense <= 0.0) emptyList()
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
                        percentage = (amt / totalMonthlyExpense).toFloat(),
                        color = color,
                        iconKey = cat.iconKey,
                        monthlyBudget = cat.monthlyBudget
                    )
                } else null
            }.sortedByDescending { it.amount }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top App Bar Greeting
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "DanaLoka",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Catatan Keuangan & Anggaran",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateToBills,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_bills_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (dueSoonBills.isNotEmpty()) {
                                    Badge(containerColor = RedExpense) {
                                        Text("${dueSoonBills.size}", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Pengingat Tagihan Berkala",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (onExportReport != null) {
                        IconButton(
                            onClick = onExportReport,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dashboard_export_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Ekspor Laporan Keuangan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Total Balance Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_balance_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Total Saldo Bersih",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyHelper.format(totalBalance, activeCurrency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Income & Expense summary pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.18f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF86EFAC), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pemasukan", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                                }
                                Text(
                                    text = CurrencyHelper.format(totalMonthlyIncome, activeCurrency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.18f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pengeluaran", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                                }
                                Text(
                                    text = CurrencyHelper.format(totalMonthlyExpense, activeCurrency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Action Buttons (clean single-line labels)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.AddCircle,
                    label = "Transaksi",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onAddTransaction,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.Category,
                    label = "Anggaran",
                    color = Color(0xFF8B5CF6),
                    onClick = onNavigateToCategories,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.EventRepeat,
                    label = "Tagihan",
                    color = Color(0xFF0284C7),
                    onClick = onNavigateToBills,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.Handshake,
                    label = "Hutang",
                    color = Color(0xFFF59E0B),
                    onClick = onNavigateToDebts,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Urgent Upcoming Bills Alert Banner (if any)
        if (dueSoonBills.isNotEmpty()) {
            item {
                val topBill = dueSoonBills.first()
                Surface(
                    onClick = onNavigateToBills,
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_urgent_bills_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tagihan Jatuh Tempo (${dueSoonBills.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "${topBill.bill.title}: ${BillNotificationHelper.formatDueBadgeLabel(topBill.daysUntilDue)}",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF92400E))
                    }
                }
            }
        }

        // Urgent Debts Due Date Alert Banner (if any)
        if (urgentDebts.isNotEmpty()) {
            item {
                Surface(
                    onClick = onNavigateToDebts,
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_urgent_debts_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pengingat Hutang/Piutang (${urgentDebts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "Ada pinjaman yang mendekati atau telah lewat jatuh tempo.",
                                fontSize = 11.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF991B1B))
                    }
                }
            }
        }

        // Category Budget Near-Limit / Over-Limit Alert Banner (if any)
        if (budgetAlerts.isNotEmpty()) {
            item {
                val hasExceeded = budgetAlerts.any { it.status == BudgetHealthStatus.EXCEEDED }
                val bg = if (hasExceeded) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                val borderCol = if (hasExceeded) Color(0xFFFCA5A5) else Color(0xFFFCD34D)
                val iconBg = if (hasExceeded) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                val iconTint = if (hasExceeded) Color(0xFFDC2626) else Color(0xFFD97706)
                val titleCol = if (hasExceeded) Color(0xFF991B1B) else Color(0xFF92400E)
                val bodyCol = if (hasExceeded) Color(0xFFB91C1C) else Color(0xFFB45309)
                val topAlert = budgetAlerts.first()

                Surface(
                    onClick = onNavigateToCategories,
                    shape = RoundedCornerShape(14.dp),
                    color = bg,
                    border = BorderStroke(1.dp, borderCol),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_budget_warning_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Peringatan Anggaran Kategori",
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasExceeded) {
                                    "Peringatan Anggaran (${budgetAlerts.size} Kategori)"
                                } else {
                                    "Anggaran Hampir Terlampaui (${budgetAlerts.size})"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = titleCol
                            )
                            Text(
                                text = "${topAlert.category.name}: ${topAlert.usagePercent}% terpakai dari ${CurrencyHelper.format(topAlert.category.monthlyBudget, activeCurrency)}",
                                fontSize = 11.sp,
                                color = bodyCol,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = titleCol)
                    }
                }
            }
        }

        // Automatic Weekly & Monthly Gemini AI Financial Summary
        if (weeklyMetrics != null) {
            item {
                WeeklyGeminiInsightCard(
                    metrics = weeklyMetrics,
                    uiState = weeklyInsightState,
                    selectedPeriod = selectedInsightPeriod,
                    onSelectPeriod = onSelectInsightPeriod,
                    onRefresh = onRefreshWeeklyInsight
                )
            }
        }

        // Monthly Cash Flow Chart (Recharts-inspired comparative bar chart)
        item {
            MonthlyCashFlowChart(
                transactions = transactions,
                activeCurrency = activeCurrency,
                modifier = Modifier.testTag("dashboard_cash_flow_chart")
            )
        }

        // Donut Chart with Total Budget prominently displayed
        item {
            DonutExpenseChart(
                items = categoryExpenseItems,
                totalExpense = totalMonthlyExpense,
                currencyCode = activeCurrency,
                totalBudget = overallMonthlyBudget,
                modifier = Modifier.testTag("dashboard_donut_chart")
            )
        }

        // Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaksi Terkini",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Text("Lihat Semua")
                }
            }
        }

        // Recent Transactions List (first 5)
        if (recentList.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada transaksi yang dicatat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = onAddTransaction) {
                            Text("Catat Transaksi Sekarang")
                        }
                    }
                }
            }
        } else {
            items(
                items = recentList,
                key = { it.id },
                contentType = { "dashboard_recent_tx" }
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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                text = "${category?.name ?: "Umum"} • ${DateUtils.formatDate(tx.dateMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
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
                            softWrap = false
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(70.dp)) }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
