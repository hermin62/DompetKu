package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountEntity
import com.example.data.BillReminderEntity
import com.example.data.CategoryEntity
import com.example.data.TransactionType
import com.example.util.BillNotificationHelper
import com.example.util.CategoryIcons
import com.example.util.CurrencyHelper
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillReminderScreen(
    bills: List<BillReminderEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity> = emptyList(),
    activeCurrency: String,
    reminderDaysBefore: Int = 3,
    notificationsEnabled: Boolean = true,
    onAddBill: (title: String, amount: Double, categoryId: Long, dueDateMillis: Long, repeatMonths: Int, notes: String, currency: String) -> Unit,
    onPayBill: (billId: Long, accountId: Long?) -> Unit,
    onDeleteBill: (Long) -> Unit = {},
    onToggleNotifications: (Boolean) -> Unit = {},
    onSendTestReminder: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(BillNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted || BillNotificationHelper.hasNotificationPermission(context)
        if (hasNotifPermission) {
            onToggleNotifications(true)
            onSendTestReminder()
        }
    }

    var filterMode by remember { mutableIntStateOf(0) } // 0: Belum Lunas, 1: Jatuh Tempo Dekat, 2: Lunas, 3: Semua
    var showAddSheet by remember { mutableStateOf(false) }
    var payingBill by remember { mutableStateOf<BillReminderEntity?>(null) }
    var deletingBill by remember { mutableStateOf<BillReminderEntity?>(null) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val unpaidBills = remember(bills) {
        bills.filter { !it.isPaid }.sortedBy { it.dueDateMillis }
    }
    val paidBills = remember(bills) {
        bills.filter { it.isPaid }.sortedByDescending { it.dueDateMillis }
    }
    val dueSoonAlerts = remember(bills, reminderDaysBefore) {
        BillNotificationHelper.findDueSoonBills(
            bills = bills,
            reminderDaysBefore = reminderDaysBefore
        )
    }
    val totalUnpaid = remember(unpaidBills) { unpaidBills.sumOf { it.amount } }

    val displayedBills = remember(bills, unpaidBills, paidBills, dueSoonAlerts, filterMode) {
        when (filterMode) {
            0 -> unpaidBills
            1 -> dueSoonAlerts.map { it.bill }
            2 -> paidBills
            else -> unpaidBills + paidBills
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_bill_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Tagihan Berkala")
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

            // Header with Back Button & Check Notification Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Pengingat Tagihan Berkala",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Notifikasi otomatis H-$reminderDaysBefore jatuh tempo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSendTestReminder()
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("check_bill_notifications_btn")
                ) {
                    Icon(
                        imageVector = if (notificationsEnabled && hasNotifPermission) {
                            Icons.Default.NotificationsActive
                        } else {
                            Icons.Default.NotificationsNone
                        },
                        contentDescription = "Cek & Kirim Pengingat Tagihan",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Notification Permission Card on Android 13+ if not yet granted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Izin Notifikasi Belum Aktif",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Izinkan notifikasi agar pengingat jatuh tempo muncul otomatis.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Izinkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Summary Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Total Tagihan Aktif (${unpaidBills.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = CurrencyHelper.format(totalUnpaid, activeCurrency),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (dueSoonAlerts.isNotEmpty()) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${dueSoonAlerts.size}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dueSoonAlerts.isNotEmpty()) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Segera Tempo",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Urgent Due-Date Banner
                if (dueSoonAlerts.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("urgent_bills_banner")
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
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${dueSoonAlerts.size} Tagihan Mendekati Jatuh Tempo",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B)
                                    )
                                    val firstAlert = dueSoonAlerts.first()
                                    Text(
                                        text = "${firstAlert.bill.title}: ${BillNotificationHelper.formatDueBadgeLabel(firstAlert.daysUntilDue)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB91C1C),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Filter Chips
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val filters = listOf(
                            0 to "Belum Lunas (${unpaidBills.size})",
                            1 to "Jatuh Tempo (${dueSoonAlerts.size})",
                            2 to "Lunas (${paidBills.size})",
                            3 to "Semua (${bills.size})"
                        )
                        items(filters) { (idx, label) ->
                            FilterChip(
                                selected = filterMode == idx,
                                onClick = { filterMode = idx },
                                label = { Text(label, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }

                if (displayedBills.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (bills.isEmpty()) "Belum Ada Pengingat Tagihan" else "Tidak Ada Tagihan di Filter Ini",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Simpan tagihan listrik, WiFi, sewa, atau langganan agar tidak terlambat bayar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(onClick = { showAddSheet = true }) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tambah Tagihan Baru")
                                }
                            }
                        }
                    }
                } else {
                    items(displayedBills, key = { it.id }) { bill ->
                        val cat = categoryMap[bill.categoryId]
                        BillItemCard(
                            bill = bill,
                            category = cat,
                            reminderDaysBefore = reminderDaysBefore,
                            onPay = if (!bill.isPaid) { { payingBill = bill } } else null,
                            onDelete = { deletingBill = bill }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddSheet) {
        AddBillSheet(
            categories = categories,
            activeCurrency = activeCurrency,
            onDismiss = { showAddSheet = false },
            onSave = { title, amt, catId, due, repeat, notes, curr ->
                onAddBill(title, amt, catId, due, repeat, notes, curr)
                showAddSheet = false
            }
        )
    }

    if (payingBill != null) {
        val target = payingBill!!
        PayBillConfirmDialog(
            bill = target,
            accounts = accounts,
            onDismiss = { payingBill = null },
            onConfirm = { selectedAccId ->
                onPayBill(target.id, selectedAccId)
                payingBill = null
            }
        )
    }

    if (deletingBill != null) {
        val target = deletingBill!!
        AlertDialog(
            onDismissRequest = { deletingBill = null },
            title = { Text("Hapus Pengingat Tagihan?", fontWeight = FontWeight.Bold) },
            text = { Text("Tagihan \"${target.title}\" akan dihapus dari daftar pengingat.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBill(target.id)
                        deletingBill = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBill = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun BillItemCard(
    bill: BillReminderEntity,
    category: CategoryEntity?,
    reminderDaysBefore: Int,
    onPay: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val daysDiff = remember(bill.dueDateMillis) {
        BillNotificationHelper.calculateDaysUntilDue(bill.dueDateMillis)
    }
    val isUrgent = !bill.isPaid && daysDiff <= reminderDaysBefore
    val isOverdue = !bill.isPaid && daysDiff < 0

    val catColor = remember(category?.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(category?.colorHex ?: "#6366F1"))
        } catch (e: Exception) {
            Color(0xFF6366F1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bill_card_${bill.id}"),
        shape = RoundedCornerShape(16.dp),
        border = if (isUrgent) BorderStroke(1.dp, if (isOverdue) Color(0xFFFCA5A5) else Color(0xFFFCD34D)) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIcons.getIcon(category?.iconKey ?: "bills"),
                        contentDescription = category?.name ?: "Tagihan",
                        tint = catColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = bill.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${BillNotificationHelper.formatRepeatLabel(bill.repeatIntervalMonths)} • ${DateUtils.formatDate(bill.dueDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyHelper.format(bill.amount, bill.currencyCode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val badgeBg = when {
                        bill.isPaid -> Color(0xFFD1FAE5)
                        isOverdue -> Color(0xFFFEE2E2)
                        isUrgent -> Color(0xFFFEF3C7)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val badgeFg = when {
                        bill.isPaid -> Color(0xFF047857)
                        isOverdue -> Color(0xFFDC2626)
                        isUrgent -> Color(0xFFB45309)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val badgeLabel = if (bill.isPaid) {
                        "LUNAS"
                    } else {
                        BillNotificationHelper.formatDueBadgeLabel(daysDiff)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                        Text(
                            text = badgeLabel,
                            color = badgeFg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (bill.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Catatan: ${bill.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus tagihan ${bill.title}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onPay != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onPay,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (bill.repeatIntervalMonths > 0) "Bayar & Lanjut Siklus" else "Tandai Lunas",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayBillConfirmDialog(
    bill: BillReminderEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    var recordExpense by remember { mutableStateOf(accounts.isNotEmpty()) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfirmasi Pembayaran Tagihan", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tagihan \"${bill.title}\" sebesar ${CurrencyHelper.format(bill.amount, bill.currencyCode)} akan ditandai lunas.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (bill.repeatIntervalMonths > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "Karena tagihan ini berulang (${BillNotificationHelper.formatRepeatLabel(bill.repeatIntervalMonths)}), jadwal siklus berikutnya akan otomatis dibuat.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (accounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Catat & potong saldo akun", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Switch(checked = recordExpense, onCheckedChange = { recordExpense = it })
                    }
                    if (recordExpense) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accounts, key = { it.id }) { acc ->
                                FilterChip(
                                    selected = selectedAccountId == acc.id,
                                    onClick = { selectedAccountId = acc.id },
                                    label = { Text(acc.name, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(if (recordExpense) selectedAccountId else null) }) {
                Text("Konfirmasi Lunas")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillSheet(
    categories: List<CategoryEntity>,
    activeCurrency: String,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, categoryId: Long, dueDateMillis: Long, repeatMonths: Int, notes: String, currency: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val expenseCategories = remember(categories) {
        categories.filter { it.type == TransactionType.EXPENSE }.ifEmpty { categories }
    }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategoryId by remember {
        mutableStateOf(
            expenseCategories.find { it.name.contains("Tagihan", ignoreCase = true) }?.id
                ?: expenseCategories.firstOrNull()?.id
                ?: 5L
        )
    }
    var selectedRepeatMonths by remember { mutableIntStateOf(1) }
    var selectedDueDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 3 * 86400000L) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDueDateMillis)
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tambah Pengingat Tagihan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                label = { Text("Nama Tagihan") },
                placeholder = { Text("cth: Listrik PLN, WiFi IndiHome, Sewa Kost") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("bill_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    amountText = CurrencyHelper.formatThousands(digits)
                    error = null
                },
                label = { Text("Nominal Tagihan ($activeCurrency)") },
                placeholder = { Text("0 (cth: 250.000)") },
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
                modifier = Modifier.fillMaxWidth().testTag("bill_amount_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Siklus Pengulangan Tagihan:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cycles = listOf(
                    1 to "Bulanan",
                    3 to "Per 3 Bulan",
                    12 to "Tahunan",
                    0 to "Sekali Saja"
                )
                items(cycles) { (months, label) ->
                    FilterChip(
                        selected = selectedRepeatMonths == months,
                        onClick = { selectedRepeatMonths = months },
                        label = { Text(label, maxLines = 1, softWrap = false) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Jatuh Tempo: ${DateUtils.formatFullDate(selectedDueDateMillis)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (expenseCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Kategori Tagihan:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(expenseCategories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Catatan / ID Pelanggan (Opsional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val amt = CurrencyHelper.parseThousands(amountText)
                    if (title.isBlank()) {
                        error = "Nama tagihan harus diisi"
                        return@Button
                    }
                    if (amt <= 0.0) {
                        error = "Nominal tagihan tidak valid (cth: 250.000)"
                        return@Button
                    }
                    onSave(title.trim(), amt, selectedCategoryId, selectedDueDateMillis, selectedRepeatMonths, notes.trim(), activeCurrency)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_bill_btn")
            ) {
                Text("Simpan Pengingat Tagihan", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDueDateMillis = it
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
