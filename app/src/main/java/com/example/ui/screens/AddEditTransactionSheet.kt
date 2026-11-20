package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.util.CategoryIcons
import com.example.util.CurrencyHelper
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity> = emptyList(),
    activeCurrency: String,
    existingTransaction: TransactionEntity? = null,
    initialDateMillis: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, type: TransactionType, categoryId: Long, dateMillis: Long, notes: String, currency: String, accountId: Long, receiptUri: String?, toAccountId: Long?) -> Unit,
    onDelete: ((TransactionEntity) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember { mutableStateOf(existingTransaction?.type ?: TransactionType.EXPENSE) }
    var title by remember { mutableStateOf(existingTransaction?.title ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (existingTransaction != null) {
                CurrencyHelper.formatAmountToThousands(existingTransaction.amount)
            } else ""
        )
    }
    var notes by remember { mutableStateOf(existingTransaction?.notes ?: "") }
    var selectedCurrency by remember { mutableStateOf(existingTransaction?.currencyCode ?: activeCurrency) }
    var transactionDateMillis by remember { mutableStateOf(existingTransaction?.dateMillis ?: initialDateMillis) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = transactionDateMillis)
    var showDatePicker by remember { mutableStateOf(false) }

    // CRITICAL REQUIREMENT: Before a category is selected, it must be empty (null) initially
    var selectedCategoryId by remember(type) {
        mutableStateOf<Long?>(existingTransaction?.categoryId)
    }

    val selectedCategory = remember(categories, selectedCategoryId) {
        selectedCategoryId?.let { id -> categories.find { it.id == id } }
    }

    var selectedAccountId by remember(accounts) {
        mutableStateOf(
            existingTransaction?.accountId ?: accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 1L
        )
    }
    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }

    // Destination account for TRANSFER
    var selectedToAccountId by remember(accounts, selectedAccountId) {
        mutableStateOf(
            existingTransaction?.toAccountId ?: accounts.firstOrNull { it.id != selectedAccountId }?.id ?: (selectedAccountId + 1)
        )
    }
    val selectedToAccount = remember(accounts, selectedToAccountId) {
        accounts.find { it.id == selectedToAccountId } ?: accounts.firstOrNull { it.id != selectedAccountId }
    }

    var isAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isToAccountDropdownExpanded by remember { mutableStateOf(false) }
    var showCategoryPickerPopup by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTransaction == null) "Catat Transaksi Baru" else "Edit Transaksi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Transaction Type Selector (Pengeluaran, Pemasukan, Transfer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pengeluaran
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                    color = if (type == TransactionType.EXPENSE) RedExpense else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = {
                        type = TransactionType.EXPENSE
                        if (selectedCategory?.type != TransactionType.EXPENSE) {
                            selectedCategoryId = null
                        }
                    }
                ) {
                    Box(modifier = Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Pengeluaran",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (type == TransactionType.EXPENSE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Pemasukan
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                    color = if (type == TransactionType.INCOME) GreenIncome else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = {
                        type = TransactionType.INCOME
                        if (selectedCategory?.type != TransactionType.INCOME) {
                            selectedCategoryId = null
                        }
                    }
                ) {
                    Box(modifier = Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Pemasukan",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (type == TransactionType.INCOME) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Transfer
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                    color = if (type == TransactionType.TRANSFER) Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { type = TransactionType.TRANSFER }
                ) {
                    Box(modifier = Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Transfer",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (type == TransactionType.TRANSFER) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Standardized Nominal Input Field (format: 50.000 / 1.000.000)
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    amountText = CurrencyHelper.formatThousands(digits)
                    errorMessage = null
                },
                label = { Text("Nominal Transaksi") },
                placeholder = { Text("0 (cth: 50.000)") },
                leadingIcon = {
                    Text(
                        text = CurrencyHelper.getSymbol(selectedCurrency),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("transaction_amount_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Kategori Transaksi (Hanya jika bukan TRANSFER)
            if (type != TransactionType.TRANSFER) {
                Text(
                    text = "Kategori Transaksi:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                val currentCat = selectedCategory
                val currentCatColor = remember(currentCat) {
                    try {
                        Color(android.graphics.Color.parseColor(currentCat?.colorHex ?: "#0D9488"))
                    } catch (e: Exception) {
                        Color(0xFF0D9488)
                    }
                }

                // CATEGORY SELECTION FIELD: Opens Pop-up Dialog
                Surface(
                    onClick = { showCategoryPickerPopup = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedCategoryId == null && errorMessage != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("category_dropdown_trigger")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // Category Icon Box
                            if (currentCat != null) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(currentCatColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CategoryIcons.getIcon(currentCat.iconKey),
                                        contentDescription = currentCat.name,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = "Pilih Kategori",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Category Name & Budget Hint (clean single-line layout without wrapped badge)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                if (currentCat != null) {
                                    Text(
                                        text = currentCat.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (currentCat.type == TransactionType.EXPENSE && currentCat.monthlyBudget > 0.0) {
                                        Text(
                                            text = "Batas Anggaran: ${CurrencyHelper.format(currentCat.monthlyBudget, selectedCurrency)}/bln",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Pilih Kategori",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "Kategori masih kosong, ketuk untuk memilih",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Trailing Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (currentCat == null) "Pilih" else "Ubah",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // POP-UP CATEGORY PICKER (Modal Dialog with ONLY logo & name)
            if (showCategoryPickerPopup) {
                CategoryPickerPopupDialog(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    type = type,
                    onSelectCategory = { catId ->
                        selectedCategoryId = catId
                        showCategoryPickerPopup = false
                        errorMessage = null
                    },
                    onDismiss = { showCategoryPickerPopup = false }
                )
            }

            // Keterangan Transaksi
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Keterangan Transaksi") },
                placeholder = { Text("cth: Makan siang, Gaji, Belanja") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("transaction_title_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Akun Sumber
            Text(
                text = if (type == TransactionType.TRANSFER) "Akun Asal (Pengirim):" else "Akun:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { isAccountDropdownExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedAccount?.name ?: "Pilih Akun", fontWeight = FontWeight.Medium)
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = isAccountDropdownExpanded,
                    onDismissRequest = { isAccountDropdownExpanded = false }
                ) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currencyCode)})") },
                            onClick = {
                                selectedAccountId = acc.id
                                isAccountDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Akun Tujuan (Transfer)
            if (type == TransactionType.TRANSFER) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Akun Tujuan (Penerima):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { isToAccountDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedToAccount?.name ?: "Pilih Akun Tujuan", fontWeight = FontWeight.Medium)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = isToAccountDropdownExpanded,
                        onDismissRequest = { isToAccountDropdownExpanded = false }
                    ) {
                        accounts.filter { it.id != selectedAccountId }.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currencyCode)})") },
                                onClick = {
                                    selectedToAccountId = acc.id
                                    isToAccountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tanggal Transaksi Button
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Tanggal: ${DateUtils.formatDate(transactionDateMillis)}")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Catatan Tambahan (Opsional)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Catatan Tambahan (Opsional)") },
                placeholder = { Text("Nomor struk, referensi mutasi, nama tempat, dll.") },
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("transaction_notes_input")
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save and Action Buttons
            Button(
                onClick = {
                    val amount = CurrencyHelper.parseThousands(amountText)
                    if (amount <= 0.0) {
                        errorMessage = "Masukkan nominal transaksi yang valid (format: 1.000)"
                        return@Button
                    }
                    if (type != TransactionType.TRANSFER && selectedCategoryId == null) {
                        errorMessage = "Silakan pilih kategori transaksi terlebih dahulu"
                        return@Button
                    }
                    if (type == TransactionType.TRANSFER) {
                        if (selectedAccountId == selectedToAccountId) {
                            errorMessage = "Akun asal dan akun tujuan transfer harus berbeda"
                            return@Button
                        }
                    } else if (title.isBlank()) {
                        errorMessage = "Keterangan transaksi tidak boleh kosong"
                        return@Button
                    }

                    val finalTitle = if (type == TransactionType.TRANSFER && title.isBlank()) {
                        val fromName = selectedAccount?.name ?: "Akun Asal"
                        val toName = selectedToAccount?.name ?: "Akun Tujuan"
                        "Transfer: $fromName ➔ $toName"
                    } else title.trim()

                    onSave(
                        finalTitle,
                        amount,
                        type,
                        if (type == TransactionType.TRANSFER) 0L else (selectedCategoryId ?: 0L),
                        transactionDateMillis,
                        notes.trim(),
                        selectedCurrency,
                        selectedAccountId,
                        null,
                        if (type == TransactionType.TRANSFER) selectedToAccountId else null
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_transaction_button")
            ) {
                Text(
                    text = if (existingTransaction == null) "Simpan Transaksi" else "Perbarui Transaksi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (existingTransaction != null && onDelete != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDelete(existingTransaction) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Hapus Transaksi", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        transactionDateMillis = it
                    }
                    showDatePicker = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * POP-UP CATEGORY PICKER DIALOG
 * Displays ONLY the Category Logo and Category Name in a neat grid.
 * All categories are visible clearly without becoming a dropdown.
 */
@Composable
fun CategoryPickerPopupDialog(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    type: TransactionType,
    onSelectCategory: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGroupFilter by remember { mutableStateOf<ExpenseGroup?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val typeFilteredCategories = remember(categories, type) {
        categories.filter { it.type == type }
    }

    val needsCategories = remember(typeFilteredCategories) {
        typeFilteredCategories.filter { it.expenseGroup == ExpenseGroup.NEEDS }
    }
    val wantsCategories = remember(typeFilteredCategories) {
        typeFilteredCategories.filter { it.expenseGroup == ExpenseGroup.WANTS }
    }

    val displayedCategories = remember(typeFilteredCategories, selectedGroupFilter, searchQuery) {
        var list = if (type == TransactionType.EXPENSE && selectedGroupFilter != null) {
            typeFilteredCategories.filter { it.expenseGroup == selectedGroupFilter }
        } else {
            typeFilteredCategories
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .testTag("category_picker_popup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (type == TransactionType.EXPENSE) "Pilih Kategori Pengeluaran" else "Pilih Kategori Pemasukan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "Hanya logo dan nama kategori",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", modifier = Modifier.size(20.dp))
                    }
                }

                if (type == TransactionType.EXPENSE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedGroupFilter == null,
                                onClick = { selectedGroupFilter = null },
                                label = { Text("Semua (${typeFilteredCategories.size})", fontSize = 11.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedGroupFilter == ExpenseGroup.NEEDS,
                                onClick = { selectedGroupFilter = if (selectedGroupFilter == ExpenseGroup.NEEDS) null else ExpenseGroup.NEEDS },
                                label = { Text("Kebutuhan (${needsCategories.size})", fontSize = 11.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF047857)
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedGroupFilter == ExpenseGroup.WANTS,
                                onClick = { selectedGroupFilter = if (selectedGroupFilter == ExpenseGroup.WANTS) null else ExpenseGroup.WANTS },
                                label = { Text("Keinginan (${wantsCategories.size})", fontSize = 11.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF6D28D9)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Grid - ONLY Logo and Category Name
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (type == TransactionType.EXPENSE && selectedGroupFilter == null && searchQuery.isBlank()) {
                        // Kebutuhan Section Header
                        item(span = { GridItemSpan(2) }) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("KEBUTUHAN (${needsCategories.size} Kategori)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF047857))
                                    Text("Esensial", fontSize = 10.sp, color = Color(0xFF047857), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        gridItems(needsCategories, key = { it.id }) { cat ->
                            CategoryCompactGridItem(
                                cat = cat,
                                isSelected = cat.id == selectedCategoryId,
                                onClick = { onSelectCategory(cat.id) }
                            )
                        }

                        // Keinginan Section Header
                        item(span = { GridItemSpan(2) }) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("KEINGINAN (${wantsCategories.size} Kategori)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF6D28D9))
                                    Text("Gaya Hidup", fontSize = 10.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        gridItems(wantsCategories, key = { it.id }) { cat ->
                            CategoryCompactGridItem(
                                cat = cat,
                                isSelected = cat.id == selectedCategoryId,
                                onClick = { onSelectCategory(cat.id) }
                            )
                        }
                    } else {
                        if (displayedCategories.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("Kategori tidak ditemukan", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                            }
                        } else {
                            gridItems(displayedCategories, key = { it.id }) { cat ->
                                CategoryCompactGridItem(
                                    cat = cat,
                                    isSelected = cat.id == selectedCategoryId,
                                    onClick = { onSelectCategory(cat.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * COMPACT CATEGORY ITEM IN POPUP:
 * Shows ONLY Logo and Category Name. Clean, no extra clutter.
 */
@Composable
private fun CategoryCompactGridItem(
    cat: CategoryEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val catColor = remember(cat.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(cat.colorHex))
        } catch (e: Exception) {
            Color(0xFF0D9488)
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cat_popup_item_${cat.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ONLY LOGO
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(catColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryIcons.getIcon(cat.iconKey),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ONLY CATEGORY NAME
            Text(
                text = cat.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
