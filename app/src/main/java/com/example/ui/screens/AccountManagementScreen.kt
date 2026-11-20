package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.util.CurrencyHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    accounts: List<AccountEntity>,
    activeCurrency: String,
    onAddAccount: (AccountEntity) -> Unit,
    onUpdateAccount: (AccountEntity) -> Unit,
    onDeleteAccount: ((Long) -> Unit)? = null,
    onAdjustBalance: (accountId: Long, newBalance: Double) -> Unit,
    onTransfer: (fromAccountId: Long, toAccountId: Long, amount: Double, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddAccountSheet by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }

    val totalBalance = remember(accounts) { accounts.sumOf { it.balance } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddAccountSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_account_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Akun")
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

            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Akun & Saldo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (accounts.size <= 1) "1 Akun Aktif" else "${accounts.size} Akun Terdaftar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (accounts.size >= 2) {
                    OutlinedButton(
                        onClick = { showTransferDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("transfer_between_accounts_btn")
                    ) {
                        Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Transfer", maxLines = 1, softWrap = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total Balance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Total Saldo di Seluruh Akun",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyHelper.format(totalBalance, activeCurrency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Daftar Akun Saldo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(accounts, key = { it.id }) { acc ->
                    val accColor = try {
                        Color(android.graphics.Color.parseColor(acc.colorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingAccount = acc }
                            .testTag("account_item_${acc.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Account Icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(accColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (acc.type) {
                                        AccountType.CASH -> Icons.Default.Payments
                                        AccountType.BANK -> Icons.Default.AccountBalance
                                        AccountType.EWALLET -> Icons.Default.PhoneAndroid
                                        AccountType.SAVINGS -> Icons.Default.Savings
                                        else -> Icons.Default.CreditCard
                                    },
                                    contentDescription = acc.name,
                                    tint = accColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Middle: Account Name & Type
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (acc.type) {
                                        AccountType.CASH -> "Uang Tunai"
                                        AccountType.BANK -> "Rekening Bank"
                                        AccountType.EWALLET -> "E-Wallet Digital"
                                        AccountType.SAVINGS -> "Tabungan Khusus"
                                        else -> "Akun Lainnya"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right: Balance & Edit Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CurrencyHelper.format(acc.balance, acc.currencyCode),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Ubah nama & saldo",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { editingAccount = acc },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ubah Akun ${acc.name}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // DIALOG: EDIT ACCOUNT (Change Name, Type, and Balance)
    if (editingAccount != null) {
        val acc = editingAccount!!
        var editName by remember { mutableStateOf(acc.name) }
        var editType by remember { mutableStateOf(acc.type) }
        var editBalanceText by remember {
            mutableStateOf(CurrencyHelper.formatAmountToThousands(acc.balance))
        }
        var editErrorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = {
                Text(
                    text = "Ubah Akun Saldo",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Perbarui nama akun, tipe, atau sesuaikan saldo:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Field 1: Nama Akun (Editable!)
                    OutlinedTextField(
                        value = editName,
                        onValueChange = {
                            editName = it
                            editErrorMessage = null
                        },
                        label = { Text("Nama Akun") },
                        placeholder = { Text("cth: Kas Utama, BCA, Mandiri, GoPay") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_account_name_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Field 2: Tipe Akun
                    Text("Tipe Akun:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AccountType.CASH to "Tunai",
                            AccountType.BANK to "Bank",
                            AccountType.EWALLET to "E-Wallet",
                            AccountType.SAVINGS to "Tabungan"
                        ).forEach { (typeOption, label) ->
                            val isSelected = editType == typeOption
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                onClick = { editType = typeOption }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Field 3: Saldo Akun (Standardized thousands format)
                    OutlinedTextField(
                        value = editBalanceText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            editBalanceText = CurrencyHelper.formatThousands(digits)
                            editErrorMessage = null
                        },
                        label = { Text("Saldo Akun ($activeCurrency)") },
                        placeholder = { Text("0 (cth: 5.000.000)") },
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_account_balance_input")
                    )

                    if (editErrorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = editErrorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isBlank()) {
                            editErrorMessage = "Nama akun tidak boleh kosong"
                            return@Button
                        }
                        val newBalance = CurrencyHelper.parseThousands(editBalanceText)
                        val updated = acc.copy(
                            name = editName.trim(),
                            type = editType,
                            balance = newBalance
                        )
                        onUpdateAccount(updated)
                        onAdjustBalance(acc.id, newBalance)
                        editingAccount = null
                    },
                    modifier = Modifier.testTag("save_edit_account_btn")
                ) {
                    Text("Simpan Perubahan")
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accounts.size > 1 && onDeleteAccount != null) {
                        TextButton(
                            onClick = {
                                onDeleteAccount(acc.id)
                                editingAccount = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Hapus")
                        }
                    }
                    TextButton(onClick = { editingAccount = null }) {
                        Text("Batal")
                    }
                }
            }
        )
    }

    // Dialog: Transfer Between Accounts (Available if accounts >= 2)
    if (showTransferDialog && accounts.size >= 2) {
        var fromAccountId by remember { mutableStateOf(accounts[0].id) }
        var toAccountId by remember { mutableStateOf(accounts[1].id) }
        var amountText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer Antar Akun", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Akun Pengirim:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    var isFromExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { isFromExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = accounts.find { it.id == fromAccountId }?.name ?: "",
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        DropdownMenu(expanded = isFromExpanded, onDismissRequest = { isFromExpanded = false }) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currencyCode)})") },
                                    onClick = {
                                        fromAccountId = acc.id
                                        if (toAccountId == acc.id) {
                                            toAccountId = accounts.firstOrNull { it.id != acc.id }?.id ?: acc.id
                                        }
                                        isFromExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Akun Penerima:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    var isToExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { isToExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = accounts.find { it.id == toAccountId }?.name ?: "",
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        DropdownMenu(expanded = isToExpanded, onDismissRequest = { isToExpanded = false }) {
                            accounts.filter { it.id != fromAccountId }.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currencyCode)})") },
                                    onClick = {
                                        toAccountId = acc.id
                                        isToExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Standardized thousands formatted transfer input
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            amountText = CurrencyHelper.formatThousands(digits)
                            errorMessage = null
                        },
                        label = { Text("Nominal Transfer ($activeCurrency)") },
                        placeholder = { Text("0 (cth: 100.000)") },
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
                        modifier = Modifier.fillMaxWidth().testTag("transfer_amount_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan Transfer (Opsional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = CurrencyHelper.parseThousands(amountText)
                        if (amount <= 0.0) {
                            errorMessage = "Nominal transfer tidak valid"
                            return@Button
                        }
                        if (fromAccountId == toAccountId) {
                            errorMessage = "Akun pengirim dan penerima harus berbeda"
                            return@Button
                        }
                        onTransfer(fromAccountId, toAccountId, amount, notes.trim())
                        showTransferDialog = false
                    }
                ) {
                    Text("Kirim Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) { Text("Batal") }
            }
        )
    }

    // Modal Bottom Sheet: Add Account
    if (showAddAccountSheet) {
        var name by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(AccountType.BANK) }
        var initialBalanceText by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showAddAccountSheet = false },
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
                    Text("Tambah Akun / Dompet Baru", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showAddAccountSheet = false }) { Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup") }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Akun / Bank") },
                    placeholder = { Text("cth: Bank BCA, Mandiri, Dompet Tunai, DANA") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_account_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Tipe Akun:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        AccountType.CASH to "Tunai",
                        AccountType.BANK to "Bank",
                        AccountType.EWALLET to "E-Wallet",
                        AccountType.SAVINGS to "Tabungan"
                    ).forEach { (typeOption, label) ->
                        val isSelected = selectedType == typeOption
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            onClick = { selectedType = typeOption }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Standardized thousands formatted input
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        initialBalanceText = CurrencyHelper.formatThousands(digits)
                    },
                    label = { Text("Saldo Awal ($activeCurrency)") },
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
                    modifier = Modifier.fillMaxWidth().testTag("add_account_initial_balance_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (name.isBlank()) return@Button
                        val balance = CurrencyHelper.parseThousands(initialBalanceText)
                        val colorHex = when (selectedType) {
                            AccountType.CASH -> "#10B981"
                            AccountType.BANK -> "#0284C7"
                            AccountType.EWALLET -> "#8B5CF6"
                            AccountType.SAVINGS -> "#F59E0B"
                            else -> "#0D9488"
                        }
                        val acc = AccountEntity(
                            id = System.currentTimeMillis(),
                            name = name.trim(),
                            type = selectedType,
                            balance = balance,
                            currencyCode = activeCurrency,
                            colorHex = colorHex
                        )
                        onAddAccount(acc)
                        showAddAccountSheet = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_new_account_btn")
                ) {
                    Text("Simpan Akun Baru", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
