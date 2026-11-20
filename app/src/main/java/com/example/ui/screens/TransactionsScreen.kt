package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.util.CategoryIcons
import com.example.util.CurrencyHelper
import com.example.util.DateUtils
import kotlinx.coroutines.launch

@Immutable
private data class TransactionRowUi(
    val id: Long,
    val tx: TransactionEntity,
    val categoryName: String,
    val icon: ImageVector,
    val badgeColor: Color,
    val subtitle: String,
    val formattedAmount: String,
    val amountColor: Color,
    val formattedDate: String,
    val hasReceipt: Boolean,
    val searchBlob: String
)

private data class FilteredTransactionsSummary(
    val items: List<TransactionRowUi>,
    val totalIncome: Double,
    val totalExpense: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    activeCurrency: String,
    onAddTransaction: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onClearAllTransactions: (() -> Unit)? = null,
    onAddTransactionWithDate: ((Long) -> Unit)? = null,
    onExportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Semua, 1: Pengeluaran, 2: Pemasukan, 3: Transfer
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var viewMode by remember { mutableIntStateOf(0) } // 0: Daftar, 1: Kalender
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val transferColor = remember { Color(0xFF4F46E5) }

    val categoryColorMap = remember(categories, primaryColor) {
        categories.associate { cat ->
            val parsed = try {
                Color(android.graphics.Color.parseColor(cat.colorHex))
            } catch (_: Exception) {
                primaryColor
            }
            cat.id to parsed
        }
    }

    // Pre-compute heavy formatting, lookups, colors, and search blobs once when data changes
    val indexedRows = remember(transactions, categories, accounts, categoryColorMap) {
        val catMap = categories.associateBy { it.id }
        val accMap = accounts.associateBy { it.id }
        transactions.map { tx ->
            val isTransfer = tx.type == TransactionType.TRANSFER
            val category = if (isTransfer) null else catMap[tx.categoryId]
            val catName = if (isTransfer) "Transfer" else (category?.name ?: "Umum")
            val badgeColor = if (isTransfer) {
                transferColor
            } else {
                categoryColorMap[tx.categoryId] ?: primaryColor
            }
            val icon = if (isTransfer) {
                Icons.Default.SwapHoriz
            } else {
                CategoryIcons.getIcon(category?.iconKey ?: "category")
            }

            val fromAccName = accMap[tx.accountId]?.name
            val toAccName = tx.toAccountId?.let { accMap[it]?.name }
            val accountLabel = if (isTransfer) {
                "${fromAccName ?: "Kas"} ➔ ${toAccName ?: "Kas"}"
            } else {
                fromAccName ?: "Kas Utama"
            }
            val subtitle = if (isTransfer) "Transfer: $accountLabel" else "$catName • $accountLabel"

            val formattedAmount: String
            val amountColor: Color
            when (tx.type) {
                TransactionType.INCOME -> {
                    formattedAmount = "+ " + CurrencyHelper.format(tx.amount, tx.currencyCode)
                    amountColor = GreenIncome
                }
                TransactionType.EXPENSE -> {
                    formattedAmount = "- " + CurrencyHelper.format(tx.amount, tx.currencyCode)
                    amountColor = RedExpense
                }
                TransactionType.TRANSFER -> {
                    formattedAmount = "⇄ " + CurrencyHelper.format(tx.amount, tx.currencyCode)
                    amountColor = transferColor
                }
            }

            val searchBlob = buildString {
                append(tx.title).append(' ')
                if (tx.notes.isNotBlank()) append(tx.notes).append(' ')
                append(catName).append(' ')
                if (fromAccName != null) append(fromAccName).append(' ')
                if (toAccName != null) append(toAccName)
            }.lowercase()

            TransactionRowUi(
                id = tx.id,
                tx = tx,
                categoryName = catName,
                icon = icon,
                badgeColor = badgeColor,
                subtitle = subtitle,
                formattedAmount = formattedAmount,
                amountColor = amountColor,
                formattedDate = DateUtils.formatDate(tx.dateMillis),
                hasReceipt = !tx.receiptUri.isNullOrBlank(),
                searchBlob = searchBlob
            )
        }
    }

    // Single-pass filter + aggregate sums for smooth performance with large datasets
    val filteredSummary = remember(indexedRows, selectedTab, selectedCategoryId, searchQuery) {
        val queryLower = searchQuery.trim().lowercase()
        val hasQuery = queryLower.isNotEmpty()
        val result = ArrayList<TransactionRowUi>(indexedRows.size.coerceAtMost(256))
        var incomeSum = 0.0
        var expenseSum = 0.0

        for (row in indexedRows) {
            val tx = row.tx
            val matchesTab = when (selectedTab) {
                1 -> tx.type == TransactionType.EXPENSE
                2 -> tx.type == TransactionType.INCOME
                3 -> tx.type == TransactionType.TRANSFER
                else -> true
            }
            if (!matchesTab) continue

            if (selectedCategoryId != null) {
                if (tx.type == TransactionType.TRANSFER || tx.categoryId != selectedCategoryId) {
                    continue
                }
            }

            if (hasQuery && !row.searchBlob.contains(queryLower)) {
                continue
            }

            result.add(row)
            when (tx.type) {
                TransactionType.INCOME -> incomeSum += tx.amount
                TransactionType.EXPENSE -> expenseSum += tx.amount
                TransactionType.TRANSFER -> Unit
            }
        }
        FilteredTransactionsSummary(
            items = result,
            totalIncome = incomeSum,
            totalExpense = expenseSum
        )
    }

    val filteredRows = filteredSummary.items
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 8 }
    }

    LaunchedEffect(selectedTab, selectedCategoryId, searchQuery) {
        if (listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }
    }

    if (viewMode == 1 && onAddTransactionWithDate != null) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kalender Finansial",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent,
                            onClick = { viewMode = 0 }
                        ) {
                            Text(
                                text = "Daftar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { viewMode = 1 }
                        ) {
                            Text(
                                text = "Kalender",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            FinancialCalendarScreen(
                transactions = transactions,
                categories = categories,
                activeCurrency = activeCurrency,
                onAddTransactionWithDate = onAddTransactionWithDate,
                onTransactionClick = onTransactionClick
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Screen Header & View Toggle & Clear Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Riwayat Transaksi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onExportClick != null && transactions.isNotEmpty()) {
                        IconButton(
                            onClick = onExportClick,
                            modifier = Modifier.testTag("transactions_export_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Ekspor Laporan Keuangan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (onClearAllTransactions != null && transactions.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_all_transactions_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Kosongkan Semua Transaksi",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (onAddTransactionWithDate != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (viewMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    onClick = { viewMode = 0 }
                                ) {
                                    Text(
                                        text = "Daftar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewMode == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (viewMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    onClick = { viewMode = 1 }
                                ) {
                                    Text(
                                        text = "Kalender",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewMode == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari keterangan atau catatan...", maxLines = 1, softWrap = false) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Cari")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_transactions_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs: Semua, Pengeluaran, Pemasukan, Transfer
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = {},
                modifier = Modifier.fillMaxWidth().testTag("transaction_filter_tabs")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Semua",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Pengeluaran",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = if (selectedTab == 1) RedExpense else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Pemasukan",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = if (selectedTab == 2) GreenIncome else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Text(
                            text = "Transfer",
                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = if (selectedTab == 3) transferColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips Row (with stable keys & contentType)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "all_categories", contentType = "category_chip") {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("Semua Kategori", maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                items(
                    items = categories,
                    key = { it.id },
                    contentType = { "category_chip" }
                ) { cat ->
                    val catColor = categoryColorMap[cat.id] ?: primaryColor
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = {
                            selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id
                        },
                        label = { Text(cat.name, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Bar
            val formattedIncomeTotal = remember(filteredSummary.totalIncome, activeCurrency) {
                "+ " + CurrencyHelper.format(filteredSummary.totalIncome, activeCurrency)
            }
            val formattedExpenseTotal = remember(filteredSummary.totalExpense, activeCurrency) {
                "- " + CurrencyHelper.format(filteredSummary.totalExpense, activeCurrency)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().testTag("transaction_summary_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredRows.size} Transaksi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (filteredSummary.totalIncome > 0) {
                            Text(
                                text = formattedIncomeTotal,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GreenIncome,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (filteredSummary.totalExpense > 0) {
                            Text(
                                text = formattedExpenseTotal,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RedExpense,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transaction Items List (High-Performance LazyColumn)
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Data Transaksi Masih Kosong",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Kondisi akun bersih dengan saldo Rp 0. Anda siap mencatat transaksi pertama Anda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onAddTransaction,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("empty_add_transaction_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Catat Transaksi Pertama")
                        }
                    }
                }
            } else if (filteredRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Tidak ada transaksi yang cocok",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Coba ubah kata kunci atau filter kategori Anda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("transactions_lazy_list"),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredRows,
                        key = { it.id },
                        contentType = { "transaction_row" }
                    ) { row ->
                        TransactionRowCard(
                            row = row,
                            onClick = { onTransactionClick(row.tx) }
                        )
                    }
                }
            }
        }

        // Quick jump-to-top pill when browsing long transaction lists
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("transactions_scroll_top_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Kembali ke Paling Atas"
                )
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = onAddTransaction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("transactions_fab_add"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Transaksi")
        }
    }

    if (showClearConfirmDialog && onClearAllTransactions != null) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Kosongkan Semua Transaksi?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Semua catatan transaksi akan dihapus dan saldo kembali menjadi Rp 0 seperti akun baru install. Kategori kustom Anda akan tetap aman tersimpan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAllTransactions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_transactions_btn")
                ) {
                    Text("Ya, Kosongkan Data")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_clear_transactions_btn")
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun TransactionRowCard(
    row: TransactionRowUi,
    onClick: () -> Unit
) {
    val tx = row.tx
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("transaction_item_${row.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(row.badgeColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = row.categoryName,
                    tint = row.badgeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = tx.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )

                if (tx.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tx.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = row.formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = row.amountColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = row.formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        fontSize = 11.sp
                    )
                    if (row.hasReceipt) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = "Ada Struk",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
