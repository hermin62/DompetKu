package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BudgetAlertHelper
import com.example.data.BudgetHealthStatus
import com.example.data.CategoryEntity
import com.example.data.ExpenseGroup
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.util.CategoryIcons
import com.example.util.CurrencyHelper
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity> = emptyList(),
    activeCurrency: String,
    overallMonthlyBudget: Double,
    onAddCategory: (CategoryEntity) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onSwitchGroup: (Long) -> Unit,
    onSetOverallBudget: (Double, String) -> Unit,
    onUpdateCategoryBudget: (Long, Double) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Kebutuhan, 1: Keinginan, 2: Pemasukan
    var showAddEditSheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showOverallBudgetDialog by remember { mutableStateOf(false) }
    var settingBudgetCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    val expenseCategories = remember(categories) { categories.filter { it.type == TransactionType.EXPENSE } }
    val needsCategories = remember(expenseCategories) { expenseCategories.filter { it.expenseGroup == ExpenseGroup.NEEDS } }
    val wantsCategories = remember(expenseCategories) { expenseCategories.filter { it.expenseGroup == ExpenseGroup.WANTS } }
    val incomeCategories = remember(categories) { categories.filter { it.type == TransactionType.INCOME } }

    val totalExpenseBudget = remember(expenseCategories) { expenseCategories.sumOf { it.monthlyBudget } }
    val totalNeedsBudget = remember(needsCategories) { needsCategories.sumOf { it.monthlyBudget } }
    val totalWantsBudget = remember(wantsCategories) { wantsCategories.sumOf { it.monthlyBudget } }

    // Current month transactions & spending per category
    val monthlyTransactions = remember(transactions) {
        transactions.filter { DateUtils.isCurrentMonth(it.dateMillis) }
    }

    val spentPerCategory = remember(monthlyTransactions) {
        monthlyTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    val incomePerCategory = remember(monthlyTransactions) {
        monthlyTransactions
            .filter { it.type == TransactionType.INCOME }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    val totalMonthlyExpenseSpent = remember(monthlyTransactions) {
        monthlyTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    val totalNeedsSpent = remember(monthlyTransactions, needsCategories) {
        val ids = needsCategories.map { it.id }.toSet()
        monthlyTransactions.filter { it.type == TransactionType.EXPENSE && it.categoryId in ids }.sumOf { it.amount }
    }

    val totalWantsSpent = remember(monthlyTransactions, wantsCategories) {
        val ids = wantsCategories.map { it.id }.toSet()
        monthlyTransactions.filter { it.type == TransactionType.EXPENSE && it.categoryId in ids }.sumOf { it.amount }
    }

    val budgetAlerts = remember(categories, transactions) {
        BudgetAlertHelper.extractAlerts(
            BudgetAlertHelper.evaluateExpenseCategories(categories, transactions)
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCategory = null
                    showAddEditSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_category_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Kategori")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "Kategori & Anggaran",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Overall Monthly Budget Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overall_budget_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Total Anggaran Bulanan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = CurrencyHelper.format(overallMonthlyBudget, activeCurrency),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { showOverallBudgetDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("set_overall_budget_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atur Total", maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Overall Spending & Progress Bar
                    val overallRemaining = overallMonthlyBudget - totalMonthlyExpenseSpent
                    val isOverallOver = overallRemaining < 0.0
                    val overallProgress = if (overallMonthlyBudget > 0.0) {
                        (totalMonthlyExpenseSpent / overallMonthlyBudget).toFloat().coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terpakai: ${CurrencyHelper.format(totalMonthlyExpenseSpent, activeCurrency)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (!isOverallOver) "Sisa: ${CurrencyHelper.format(overallRemaining, activeCurrency)}" else "Lebih: ${CurrencyHelper.format(-overallRemaining, activeCurrency)}!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (!isOverallOver) Color(0xFF047857) else Color(0xFFEF4444),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            isOverallOver -> Color(0xFFEF4444)
                            overallProgress >= 0.8f -> Color(0xFFF59E0B)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Needs & Wants Sub-Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Kebutuhan (Needs)
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Kebutuhan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CurrencyHelper.format(totalNeedsBudget, activeCurrency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val needsRem = totalNeedsBudget - totalNeedsSpent
                                val needsProg = if (totalNeedsBudget > 0) (totalNeedsSpent / totalNeedsBudget).toFloat().coerceIn(0f, 1f) else 0f
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { needsProg },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = if (needsRem < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (needsRem >= 0) "Sisa ${CurrencyHelper.format(needsRem, activeCurrency)}" else "Lebih ${CurrencyHelper.format(-needsRem, activeCurrency)}!",
                                    fontSize = 10.sp,
                                    color = if (needsRem >= 0) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFEF4444),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Keinginan (Wants)
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Keinginan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6D28D9),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CurrencyHelper.format(totalWantsBudget, activeCurrency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val wantsRem = totalWantsBudget - totalWantsSpent
                                val wantsProg = if (totalWantsBudget > 0) (totalWantsSpent / totalWantsBudget).toFloat().coerceIn(0f, 1f) else 0f
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { wantsProg },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = if (wantsRem < 0) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (wantsRem >= 0) "Sisa ${CurrencyHelper.format(wantsRem, activeCurrency)}" else "Lebih ${CurrencyHelper.format(-wantsRem, activeCurrency)}!",
                                    fontSize = 10.sp,
                                    color = if (wantsRem >= 0) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFEF4444),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visual Warning Banner when any category approaches (>=80%) or exceeds (>100%) monthly budget
            if (budgetAlerts.isNotEmpty()) {
                val hasExceeded = budgetAlerts.any { it.status == BudgetHealthStatus.EXCEEDED }
                val alertBg = if (hasExceeded) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                val alertBorder = if (hasExceeded) Color(0xFFFCA5A5) else Color(0xFFFCD34D)
                val alertFg = if (hasExceeded) Color(0xFF991B1B) else Color(0xFF92400E)
                val alertIconTint = if (hasExceeded) Color(0xFFDC2626) else Color(0xFFD97706)
                val previewNames = budgetAlerts.take(2).joinToString(", ") {
                    "${it.category.name} (${it.usagePercent}%)"
                } + if (budgetAlerts.size > 2) " +${budgetAlerts.size - 2} lainnya" else ""

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = alertBg,
                    border = BorderStroke(1.dp, alertBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_budget_warning_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Peringatan Anggaran",
                            tint = alertIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasExceeded) {
                                    "Peringatan: ${budgetAlerts.size} kategori melewati atau mendekati batas!"
                                } else {
                                    "Peringatan: ${budgetAlerts.size} kategori hampir melampaui anggaran"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = alertFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = previewNames,
                                fontSize = 11.sp,
                                color = alertFg.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Tabs: Kebutuhan, Keinginan, Pemasukan
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Kebutuhan (${needsCategories.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (selectedTab == 0) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Keinginan (${wantsCategories.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (selectedTab == 1) Color(0xFF6D28D9) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Pemasukan (${incomeCategories.size})",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // List Categories with Per-Category Budget & Progress Bar
            val currentList = when (selectedTab) {
                0 -> needsCategories
                1 -> wantsCategories
                else -> incomeCategories
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentList, key = { it.id }) { cat ->
                    val spent = if (cat.type == TransactionType.EXPENSE) {
                        spentPerCategory[cat.id] ?: 0.0
                    } else {
                        incomePerCategory[cat.id] ?: 0.0
                    }

                    CategoryCardRow(
                        cat = cat,
                        activeCurrency = activeCurrency,
                        spentThisMonth = spent,
                        totalExpenseBudget = totalExpenseBudget,
                        onEdit = {
                            editingCategory = cat
                            showAddEditSheet = true
                        },
                        onDelete = { onDeleteCategory(cat.id) },
                        onSwitchGroup = if (cat.type == TransactionType.EXPENSE) {
                            { onSwitchGroup(cat.id) }
                        } else null,
                        onSetBudget = {
                            settingBudgetCategory = cat
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // DIALOG: SET OVERALL MONTHLY BUDGET
    if (showOverallBudgetDialog) {
        SetOverallMonthlyBudgetDialog(
            currentTotalBudget = overallMonthlyBudget,
            activeCurrency = activeCurrency,
            onDismiss = { showOverallBudgetDialog = false },
            onConfirm = { newTotal, strategy ->
                onSetOverallBudget(newTotal, strategy)
                showOverallBudgetDialog = false
            }
        )
    }

    // DIALOG: SET CATEGORY BUDGET (PER KATEGORI)
    if (settingBudgetCategory != null) {
        val cat = settingBudgetCategory!!
        val spent = spentPerCategory[cat.id] ?: 0.0
        SetCategoryBudgetDialog(
            category = cat,
            currentSpentThisMonth = spent,
            activeCurrency = activeCurrency,
            onDismiss = { settingBudgetCategory = null },
            onConfirm = { newBudget ->
                onUpdateCategoryBudget(cat.id, newBudget)
                settingBudgetCategory = null
            }
        )
    }

    // MODAL SHEET: ADD / EDIT CATEGORY
    if (showAddEditSheet) {
        AddEditCategorySheet(
            category = editingCategory,
            activeCurrency = activeCurrency,
            defaultType = if (selectedTab == 2) TransactionType.INCOME else TransactionType.EXPENSE,
            defaultGroup = if (selectedTab == 1) ExpenseGroup.WANTS else ExpenseGroup.NEEDS,
            onDismiss = { showAddEditSheet = false },
            onSave = { savedCat ->
                if (editingCategory == null) {
                    onAddCategory(savedCat)
                } else {
                    onUpdateCategory(savedCat)
                }
                showAddEditSheet = false
            }
        )
    }
}

/**
 * CATEGORY CARD ROW WITH BUDGET PROGRESS BAR & AVAILABLE BUDGET MONITORING
 */
@Composable
private fun CategoryCardRow(
    cat: CategoryEntity,
    activeCurrency: String,
    spentThisMonth: Double,
    totalExpenseBudget: Double = 0.0,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSwitchGroup: (() -> Unit)?,
    onSetBudget: () -> Unit
) {
    val catColor = remember(cat.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(cat.colorHex))
        } catch (e: Exception) {
            Color(0xFF0D9488)
        }
    }

    val eval = remember(cat, spentThisMonth) {
        BudgetAlertHelper.evaluateCategory(cat, spentThisMonth)
    }
    val cardBorder = when (eval.status) {
        BudgetHealthStatus.EXCEEDED -> BorderStroke(1.2.dp, Color(0xFFFCA5A5))
        BudgetHealthStatus.WARNING -> BorderStroke(1.2.dp, Color(0xFFFCD34D))
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_card_${cat.id}"),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // TOP ROW: Category Icon, Name, and Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(catColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIcons.getIcon(cat.iconKey),
                        contentDescription = cat.name,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Category Name (Clean, single line without Kebutuhan/Keinginan badge)
                Text(
                    text = cat.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (cat.type == TransactionType.EXPENSE) {
                        IconButton(
                            onClick = onSetBudget,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_set_budget_${cat.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Atur Anggaran Kategori",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (onSwitchGroup != null) {
                            IconButton(
                                onClick = onSwitchGroup,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Pindah Kelompok",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Kategori",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!cat.isDefault) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Kategori",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // BUDGET & PROGRESS BAR SECTION
            if (cat.type == TransactionType.EXPENSE) {
                val budget = cat.monthlyBudget
                if (budget > 0.0) {
                    val remaining = eval.remaining
                    val isOverBudget = eval.status == BudgetHealthStatus.EXCEEDED
                    val isWarningBudget = eval.status == BudgetHealthStatus.WARNING
                    val progress = eval.usageRatio.coerceIn(0f, 1f)
                    val pct = eval.usagePercent

                    val barColor = when (eval.status) {
                        BudgetHealthStatus.EXCEEDED -> Color(0xFFEF4444)
                        BudgetHealthStatus.WARNING -> Color(0xFFF59E0B)
                        else -> catColor
                    }

                    // Visual Warning Pill when approaching (>=80%) or exceeding (>100%) limit
                    if (isOverBudget || isWarningBudget) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOverBudget) Color(0xFFFEE2E2) else Color(0xFFFEF3C7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("budget_alert_pill_${cat.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isOverBudget) Color(0xFFDC2626) else Color(0xFFD97706),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isOverBudget) {
                                        "Anggaran terlampaui! Pengeluaran lebih ${CurrencyHelper.format(-remaining, activeCurrency)}"
                                    } else {
                                        "Hampir terlampaui ($pct% terpakai) • Sisa ${CurrencyHelper.format(remaining, activeCurrency)}"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOverBudget) Color(0xFF991B1B) else Color(0xFF92400E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Status Bar: Terpakai & Sisa Budget
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terpakai: ${CurrencyHelper.format(spentThisMonth, activeCurrency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (!isOverBudget) {
                                "Sisa: ${CurrencyHelper.format(remaining, activeCurrency)}"
                            } else {
                                "Lebih: ${CurrencyHelper.format(-remaining, activeCurrency)}!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (!isOverBudget) Color(0xFF047857) else Color(0xFFEF4444),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress Bar for Remaining/Spent Budget
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSetBudget() },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom info: Monthly Budget & Percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Anggaran: ${CurrencyHelper.format(budget, activeCurrency)} /bln",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onSetBudget() }
                        ) {
                            Text(
                                text = when {
                                    isOverBudget -> "$pct% (Terlampaui)"
                                    isWarningBudget -> "$pct% (Hampir Penuh)"
                                    else -> "$pct% terpakai"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = barColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• Ubah",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // No Budget Set for this category
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (spentThisMonth > 0.0) "Terpakai: ${CurrencyHelper.format(spentThisMonth, activeCurrency)}" else "Belum ada pengeluaran",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Belum diatur anggaran bulanan",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onSetBudget,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("setup_budget_btn_${cat.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pasang Budget", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            } else {
                // Income Category Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total diterima bulan ini:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = CurrencyHelper.format(spentThisMonth, activeCurrency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * DIALOG: SET CATEGORY BUDGET (PER KATEGORI)
 */
@Composable
fun SetCategoryBudgetDialog(
    category: CategoryEntity,
    currentSpentThisMonth: Double,
    activeCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var budgetText by remember {
        mutableStateOf(if (category.monthlyBudget > 0) CurrencyHelper.formatAmountToThousands(category.monthlyBudget) else "")
    }

    val catColor = remember(category.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(category.colorHex))
        } catch (e: Exception) {
            Color(0xFF0D9488)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(catColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIcons.getIcon(category.iconKey),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Atur Anggaran Kategori",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pengeluaran Bulan Ini:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyHelper.format(currentSpentThisMonth, activeCurrency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        budgetText = CurrencyHelper.formatThousands(digits)
                    },
                    label = { Text("Batas Anggaran Bulanan ($activeCurrency)") },
                    placeholder = { Text("0 (cth: 1.000.000)") },
                    leadingIcon = {
                        Text(
                            text = CurrencyHelper.getSymbol(activeCurrency),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_budget_input_dialog")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pilihan Cepat Nominal:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500_000.0, 1_000_000.0, 1_500_000.0, 2_000_000.0).forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                budgetText = CurrencyHelper.formatAmountToThousands(preset)
                            }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (preset >= 1_000_000.0) "${(preset / 1_000_000).toInt()}jt" else "${(preset / 1000).toInt()}rb",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Live preview warning if proposed budget puts spending >=80% or >100%
                val previewBudget = CurrencyHelper.parseThousands(budgetText)
                if (previewBudget > 0.0 && currentSpentThisMonth > 0.0) {
                    val previewRatio = currentSpentThisMonth / previewBudget
                    val previewPct = (previewRatio * 100).toInt()
                    if (previewRatio >= BudgetAlertHelper.WARNING_THRESHOLD) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val isOver = currentSpentThisMonth > previewBudget
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isOver) Color(0xFFFEE2E2) else Color(0xFFFEF3C7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isOver) Color(0xFFDC2626) else Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isOver) {
                                        "Pengeluaran saat ini sudah $previewPct% (melebihi batas baru)."
                                    } else {
                                        "Pengeluaran saat ini sudah mencapai $previewPct% dari batas ini."
                                    },
                                    fontSize = 11.sp,
                                    color = if (isOver) Color(0xFF991B1B) else Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyHelper.parseThousands(budgetText)
                    onConfirm(amount)
                },
                modifier = Modifier.testTag("confirm_set_category_budget_btn")
            ) {
                Text("Simpan Anggaran")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * DIALOG: SET OVERALL MONTHLY BUDGET
 * All category budgets will follow and sync with this total monthly budget.
 */
@Composable
fun SetOverallMonthlyBudgetDialog(
    currentTotalBudget: Double,
    activeCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var budgetText by remember {
        mutableStateOf(CurrencyHelper.formatAmountToThousands(currentTotalBudget))
    }
    var selectedStrategy by remember { mutableStateOf("SMART_50_30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Atur Total Anggaran Bulanan", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Anggaran masing-masing kategori pengeluaran akan otomatis mengikuti total budget bulanan ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Standardized nominal input with thousands formatting
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        budgetText = CurrencyHelper.formatThousands(digits)
                    },
                    label = { Text("Total Budget Bulanan ($activeCurrency)") },
                    placeholder = { Text("0 (cth: 10.000.000)") },
                    leadingIcon = {
                        Text(
                            text = CurrencyHelper.getSymbol(activeCurrency),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overall_budget_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick presets
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(5_000_000.0, 10_000_000.0, 15_000_000.0, 20_000_000.0)
                    items(presets) { amount ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable {
                                budgetText = CurrencyHelper.formatAmountToThousands(amount)
                            }
                        ) {
                            Text(
                                text = CurrencyHelper.format(amount, activeCurrency),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Strategi Pembagian Kategori:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStrategy = "SMART_50_30" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedStrategy == "SMART_50_30",
                        onClick = { selectedStrategy = "SMART_50_30" }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Proporsional Cerdas (Kebutuhan 65% : Keinginan 35%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Kategori esensial dapat porsi lebih besar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStrategy = "EQUAL" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedStrategy == "EQUAL",
                        onClick = { selectedStrategy = "EQUAL" }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dibagi Rata ke Seluruh Kategori", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Setiap kategori mendapat budget sama besar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyHelper.parseThousands(budgetText)
                    onConfirm(amount, selectedStrategy)
                },
                modifier = Modifier.testTag("confirm_set_overall_budget_btn")
            ) {
                Text("Terapkan ke Kategori")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * ADD / EDIT CATEGORY SHEET
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCategorySheet(
    category: CategoryEntity?,
    activeCurrency: String,
    defaultType: TransactionType = TransactionType.EXPENSE,
    defaultGroup: ExpenseGroup = ExpenseGroup.NEEDS,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(category?.name ?: "") }
    var type by remember { mutableStateOf(category?.type ?: defaultType) }
    var group by remember { mutableStateOf(category?.expenseGroup ?: defaultGroup) }
    var selectedColor by remember { mutableStateOf(category?.colorHex ?: "#0D9488") }
    var selectedIcon by remember { mutableStateOf(category?.iconKey ?: "category") }
    var budgetText by remember {
        mutableStateOf(if (category != null) CurrencyHelper.formatAmountToThousands(category.monthlyBudget) else "")
    }

    val categoryColorPalette = listOf(
        "#0D9488", "#10B981", "#0284C7", "#6366F1", "#8B5CF6",
        "#EC4899", "#F43F5E", "#F59E0B", "#F97316", "#64748B"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (category == null) "Tambah Kategori Baru" else "Edit Kategori",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup") }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Nama Kategori
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama Kategori") },
                placeholder = { Text("cth: Kopi & Nongkrong, Servis Kendaraan") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Budget Bulanan (Jika Pengeluaran)
            if (type == TransactionType.EXPENSE) {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        budgetText = CurrencyHelper.formatThousands(digits)
                    },
                    label = { Text("Batas Anggaran Bulanan") },
                    placeholder = { Text("0 (cth: 500.000)") },
                    leadingIcon = {
                        Text(
                            text = CurrencyHelper.getSymbol(activeCurrency),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_budget_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Kelompok Pengeluaran:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp)),
                        color = if (group == ExpenseGroup.NEEDS) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { group = ExpenseGroup.NEEDS }
                    ) {
                        Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                            Text("Kebutuhan", fontWeight = FontWeight.Bold, color = if (group == ExpenseGroup.NEEDS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp)),
                        color = if (group == ExpenseGroup.WANTS) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { group = ExpenseGroup.WANTS }
                    ) {
                        Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                            Text("Keinginan", fontWeight = FontWeight.Bold, color = if (group == ExpenseGroup.WANTS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Color Palette Selector
            Text("Pilih Warna", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryColorPalette) { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = selectedColor == hex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val budget = CurrencyHelper.parseThousands(budgetText)
                    val cat = CategoryEntity(
                        id = category?.id ?: System.currentTimeMillis(),
                        name = name.trim(),
                        type = type,
                        iconKey = selectedIcon,
                        colorHex = selectedColor,
                        expenseGroup = group,
                        monthlyBudget = budget,
                        isDefault = category?.isDefault ?: false
                    )
                    onSave(cat)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_category_btn")
            ) {
                Text("Simpan Kategori", fontWeight = FontWeight.Bold)
            }
        }
    }
}
