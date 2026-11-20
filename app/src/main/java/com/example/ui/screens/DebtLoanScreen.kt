package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountEntity
import com.example.data.DebtLoanEntity
import com.example.data.DebtType
import com.example.ui.theme.RedExpense
import com.example.util.CurrencyHelper
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtLoanScreen(
    debts: List<DebtLoanEntity>,
    accounts: List<AccountEntity> = emptyList(),
    activeCurrency: String,
    onAddDebt: (personName: String, totalAmount: Double, type: DebtType, dueDateMillis: Long, notes: String, phone: String, currency: String) -> Unit,
    onRepayDebt: (debtId: Long, paymentAmount: Double, accountId: Long?) -> Unit,
    onDeleteDebt: ((Long) -> Unit)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Piutang, 1: Hutang
    var filterStatus by remember { mutableIntStateOf(0) } // 0: Semua, 1: Belum Lunas, 2: Jatuh Tempo Dekat / Terlewat, 3: Lunas
    var showAddSheet by remember { mutableStateOf(false) }
    var repayingDebt by remember { mutableStateOf<DebtLoanEntity?>(null) }
    var deletingDebt by remember { mutableStateOf<DebtLoanEntity?>(null) }

    val now = System.currentTimeMillis()
    val oneDay = 86400_000L

    val piutangList = remember(debts) { debts.filter { it.type == DebtType.PIUTANG } }
    val hutangList = remember(debts) { debts.filter { it.type == DebtType.HUTANG } }

    val totalPiutangBelumLunas = remember(piutangList) {
        piutangList.filter { !it.isSettled }.sumOf { it.remainingAmount }
    }
    val totalHutangBelumLunas = remember(hutangList) {
        hutangList.filter { !it.isSettled }.sumOf { it.remainingAmount }
    }

    // Overdue and Due Soon (< 3 days)
    val urgentDebts = remember(debts, now) {
        debts.filter { !it.isSettled && (it.dueDateMillis <= now + 3 * oneDay) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_debt_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Catatan Pinjaman")
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

            // Screen Header with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Catatan Hutang & Piutang",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Pengingat jatuh tempo & status pelunasan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // URGENT DUE DATE REMINDER ALERT BANNER
            if (urgentDebts.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth().testTag("urgent_debts_alert_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pengingat Jatuh Tempo (${urgentDebts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "Ada transaksi yang mendekati atau telah lewat jatuh tempo. Segera tindak lanjuti pelunasan.",
                                fontSize = 11.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Summary Hero Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Piutang Hero Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB).copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CallMade, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Piutang (Hak Saya)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = CurrencyHelper.format(totalPiutangBelumLunas, activeCurrency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${piutangList.count { !it.isSettled }} catatan belum lunas",
                            fontSize = 10.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                }

                // Hutang Hero Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RedExpense.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CallReceived, contentDescription = null, tint = RedExpense, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hutang (Kewajiban)", style = MaterialTheme.typography.labelSmall, color = RedExpense, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = CurrencyHelper.format(totalHutangBelumLunas, activeCurrency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RedExpense,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${hutangList.count { !it.isSettled }} catatan belum lunas",
                            fontSize = 10.sp,
                            color = Color(0xFF991B1B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Tabs: Piutang vs Hutang
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
                            text = "Piutang (${piutangList.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) Color(0xFF1D4ED8) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Hutang (${hutangList.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) RedExpense else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Filter Chips: Status filter
            val currentGroupList = if (selectedTab == 0) piutangList else hutangList

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf(
                    0 to "Semua (${currentGroupList.size})",
                    1 to "Belum Lunas (${currentGroupList.count { !it.isSettled }})",
                    2 to "Jatuh Tempo Dekat (${currentGroupList.count { !it.isSettled && it.dueDateMillis <= now + 3 * oneDay }})",
                    3 to "Sudah Lunas (${currentGroupList.count { it.isSettled }})"
                )
                items(filters) { (idx, label) ->
                    val isSelected = filterStatus == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = { filterStatus = idx },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filtered list based on secondary chips
            val filteredList = remember(currentGroupList, filterStatus, now) {
                when (filterStatus) {
                    1 -> currentGroupList.filter { !it.isSettled }
                    2 -> currentGroupList.filter { !it.isSettled && it.dueDateMillis <= now + 3 * oneDay }
                    3 -> currentGroupList.filter { it.isSettled }
                    else -> currentGroupList
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (filterStatus) {
                                1 -> "Tidak ada pinjaman yang belum lunas"
                                2 -> "Tidak ada yang mendekati jatuh tempo"
                                3 -> "Belum ada catatan yang lunas"
                                else -> if (selectedTab == 0) "Belum ada catatan piutang" else "Belum ada catatan hutang"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        DebtItemCard(
                            item = item,
                            now = now,
                            activeCurrency = activeCurrency,
                            onRepay = { repayingDebt = item },
                            onDelete = { deletingDebt = item }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // DIALOG: REPAY / SETTLE DEBT
    if (repayingDebt != null) {
        RepayDebtDialog(
            debt = repayingDebt!!,
            accounts = accounts,
            activeCurrency = activeCurrency,
            onDismiss = { repayingDebt = null },
            onConfirm = { amount, accountId ->
                onRepayDebt(repayingDebt!!.id, amount, accountId)
                repayingDebt = null
            }
        )
    }

    // DIALOG: DELETE DEBT
    if (deletingDebt != null) {
        val debt = deletingDebt!!
        AlertDialog(
            onDismissRequest = { deletingDebt = null },
            title = { Text("Hapus Catatan?", fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menghapus catatan pinjaman atas nama ${debt.personName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDebt?.invoke(debt.id)
                        deletingDebt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDebt = null }) { Text("Batal") }
            }
        )
    }

    // BOTTOM SHEET: ADD DEBT / LOAN
    if (showAddSheet) {
        AddDebtLoanSheet(
            defaultType = if (selectedTab == 0) DebtType.PIUTANG else DebtType.HUTANG,
            activeCurrency = activeCurrency,
            onDismiss = { showAddSheet = false },
            onSave = { person, amt, type, due, notes, phone, curr ->
                onAddDebt(person, amt, type, due, notes, phone, curr)
                showAddSheet = false
            }
        )
    }
}

/**
 * INDIVIDUAL DEBT / LOAN CARD
 */
@Composable
private fun DebtItemCard(
    item: DebtLoanEntity,
    now: Long,
    activeCurrency: String,
    onRepay: () -> Unit,
    onDelete: () -> Unit
) {
    val oneDay = 86400_000L
    val dueStart = DateUtils.getStartOfDay(item.dueDateMillis)
    val todayStart = DateUtils.getStartOfDay(now)
    val daysDiff = ((dueStart - todayStart) / oneDay).toInt()
    val isOverdue = !item.isSettled && daysDiff < 0
    val isDueToday = !item.isSettled && daysDiff == 0
    val isDueSoon = !item.isSettled && daysDiff in 1..3

    val paidAmount = (item.totalAmount - item.remainingAmount).coerceAtLeast(0.0)
    val pctSettled = if (item.totalAmount > 0) ((paidAmount / item.totalAmount) * 100).toInt() else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!item.isSettled) onRepay() }
            .testTag("debt_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // TOP ROW: Person Name & Due Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isSettled) Color(0xFF10B981).copy(alpha = 0.15f)
                                else if (item.type == DebtType.PIUTANG) Color(0xFF2563EB).copy(alpha = 0.15f)
                                else RedExpense.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isSettled) Icons.Default.Check
                            else if (item.type == DebtType.PIUTANG) Icons.Default.CallMade
                            else Icons.Default.CallReceived,
                            contentDescription = null,
                            tint = if (item.isSettled) Color(0xFF047857)
                            else if (item.type == DebtType.PIUTANG) Color(0xFF1D4ED8)
                            else RedExpense,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.personName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val roleLabel = if (item.type == DebtType.HUTANG) "Pemberi Pinjaman" else "Peminjam"
                        val contactSuffix = if (item.phone.isNotBlank()) " • ${item.phone}" else ""
                        Text(
                            text = "$roleLabel$contactSuffix",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge
                when {
                    item.isSettled -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF10B981).copy(alpha = 0.18f)) {
                            Text(
                                text = "LUNAS",
                                color = Color(0xFF047857),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    isOverdue -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEF4444).copy(alpha = 0.18f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Terlewat ${-daysDiff}h",
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    isDueToday -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF59E0B).copy(alpha = 0.18f)) {
                            Text(
                                text = "Tempo Hari Ini",
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    isDueSoon -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF97316).copy(alpha = 0.18f)) {
                            Text(
                                text = "H-$daysDiff lagi",
                                color = Color(0xFFC2410C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = DateUtils.formatShortDate(item.dueDateMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MIDDLE SECTION: Amounts & Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Pinjaman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyHelper.format(item.totalAmount, item.currencyCode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (item.isSettled) "Sudah Dilunasi" else "Sisa Pembayaran",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyHelper.format(item.remainingAmount, item.currencyCode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isSettled) Color(0xFF047857) else if (item.type == DebtType.PIUTANG) Color(0xFF1D4ED8) else RedExpense
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Settlement Progress Bar
            val progress = (paidAmount / item.totalAmount).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (item.isSettled) Color(0xFF10B981) else if (item.type == DebtType.PIUTANG) Color(0xFF2563EB) else RedExpense,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle info: Due date & Notes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jatuh Tempo: ${DateUtils.formatDate(item.dueDateMillis)}",
                    fontSize = 11.sp,
                    color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = if (item.isSettled) "100% Lunas" else "$pctSettled% Terbayar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Catatan: ${item.notes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ACTION BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus Catatan Pinjaman", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }

                if (!item.isSettled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRepay,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("repay_btn_${item.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cicil / Lunasi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * DIALOG: CATAT PEMBAYARAN / PELUNASAN TRANSAKSI
 */
@Composable
fun RepayDebtDialog(
    debt: DebtLoanEntity,
    accounts: List<AccountEntity>,
    activeCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, accountId: Long?) -> Unit
) {
    var paymentAmountText by remember {
        mutableStateOf(CurrencyHelper.formatAmountToThousands(debt.remainingAmount))
    }
    var selectedAccountId by remember {
        mutableStateOf(accounts.firstOrNull()?.id)
    }
    var syncWithAccount by remember { mutableStateOf(accounts.isNotEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Catat Pelunasan: ${debt.personName}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (debt.type == DebtType.PIUTANG) "Penerimaan pelunasan piutang dari ${debt.personName}" else "Pembayaran hutang kepada ${debt.personName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sisa Nominal:")
                        Text(
                            text = CurrencyHelper.format(debt.remainingAmount, debt.currencyCode),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Input Nominal Pembayaran
                OutlinedTextField(
                    value = paymentAmountText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        paymentAmountText = CurrencyHelper.formatThousands(digits)
                        error = null
                    },
                    label = { Text("Nominal Pembayaran ($activeCurrency)") },
                    placeholder = { Text("0") },
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
                    modifier = Modifier.fillMaxWidth().testTag("repay_amount_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Preset button: Lunasi Penuh
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            paymentAmountText = CurrencyHelper.formatAmountToThousands(debt.remainingAmount)
                        }
                    ) {
                        Text("Lunasi Semua (Full)")
                    }
                }

                // Account sync option
                if (accounts.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { syncWithAccount = !syncWithAccount }
                    ) {
                        Checkbox(
                            checked = syncWithAccount,
                            onCheckedChange = { syncWithAccount = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (debt.type == DebtType.PIUTANG) "Masuk ke saldo akun" else "Potong dari saldo akun",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (syncWithAccount) {
                        Spacer(modifier = Modifier.height(4.dp))
                        var isExpanded by remember { mutableStateOf(false) }
                        val currentAcc = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { isExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(currentAcc?.name ?: "Pilih Akun", fontSize = 12.sp)
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currencyCode)})") },
                                        onClick = {
                                            selectedAccountId = acc.id
                                            isExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = CurrencyHelper.parseThousands(paymentAmountText)
                    if (amt <= 0.0) {
                        error = "Nominal pembayaran tidak valid"
                        return@Button
                    }
                    onConfirm(amt, if (syncWithAccount) selectedAccountId else null)
                },
                modifier = Modifier.testTag("confirm_repay_button")
            ) {
                Text("Simpan Pembayaran")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * BOTTOM SHEET: TAMBAH CATATAN HUTANG / PIUTANG
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtLoanSheet(
    defaultType: DebtType,
    activeCurrency: String,
    onDismiss: () -> Unit,
    onSave: (personName: String, totalAmount: Double, type: DebtType, dueDateMillis: Long, notes: String, phone: String, currency: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(defaultType) }
    var personName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis() + 7 * 86400000L)
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDueDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 7 * 86400000L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
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
                Text(
                    text = if (type == DebtType.PIUTANG) "Catat Piutang Baru" else "Catat Hutang Baru",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup") }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Type Toggle: Piutang vs Hutang
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    color = if (type == DebtType.PIUTANG) Color(0xFF2563EB) else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { type = DebtType.PIUTANG }
                ) {
                    Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                        Text("Piutang (Hak Saya)", fontWeight = FontWeight.Bold, color = if (type == DebtType.PIUTANG) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    color = if (type == DebtType.HUTANG) RedExpense else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { type = DebtType.HUTANG }
                ) {
                    Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                        Text("Hutang (Kewajiban)", fontWeight = FontWeight.Bold, color = if (type == DebtType.HUTANG) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = personName,
                onValueChange = {
                    personName = it
                    errorMessage = null
                },
                label = {
                    Text(
                        if (type == DebtType.HUTANG) "Nama Pemberi Pinjaman / Kreditur"
                        else "Nama Peminjam / Pihak"
                    )
                },
                placeholder = { Text("cth: Budi Santoso, Toko Berkah") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("debt_person_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Nominal Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    amountText = CurrencyHelper.formatThousands(digits)
                    errorMessage = null
                },
                label = { Text("Nominal Pinjaman ($activeCurrency)") },
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
                modifier = Modifier.fillMaxWidth().testTag("debt_amount_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Due Date Picker Button
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Jatuh Tempo: ${DateUtils.formatFullDate(selectedDueDateMillis)}")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Nomor Telepon / WhatsApp (Opsional)") },
                placeholder = { Text("0812xxxxxx") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Keterangan Pinjaman (Opsional)") },
                placeholder = { Text("cth: Pinjam untuk perbaikan laptop") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val amount = CurrencyHelper.parseThousands(amountText)
                    if (personName.isBlank()) {
                        errorMessage = "Nama orang/pihak harus diisi"
                        return@Button
                    }
                    if (amount <= 0.0) {
                        errorMessage = "Nominal pinjaman tidak valid"
                        return@Button
                    }
                    onSave(personName.trim(), amount, type, selectedDueDateMillis, notes.trim(), phone.trim(), activeCurrency)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_debt_button")
            ) {
                Text("Simpan Pinjaman", fontWeight = FontWeight.Bold)
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
