package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.UserPreferences
import com.example.data.local.RoomCacheStatus
import com.example.util.BiometricHelper
import com.example.util.CurrencyHelper
import com.example.util.DateUtils

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    isSyncing: Boolean = false,
    roomCacheStatus: RoomCacheStatus? = null,
    onSetCurrency: (String) -> Unit,
    onSetBiometric: (Boolean) -> Unit,
    onSetTwoFactor: (Boolean) -> Unit,
    onSetPin: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetCloudSync: (Boolean) -> Unit,
    onSetBillNotifications: (Boolean) -> Unit = {},
    onTriggerSync: () -> Unit,
    onConnectGoogleAccount: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onLockAppNow: () -> Unit,
    onNavigateCategories: () -> Unit,
    onNavigateAccounts: () -> Unit = {},
    onNavigateBills: () -> Unit = {},
    onExportReport: () -> Unit = {},
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var biometricWarningMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_lazy_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Header Title
        item {
            Text(
                text = "Pengaturan & Keamanan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Section 0: Akun Google & Database Google Sheets
        item {
            Text(
                text = "Akun & Database Google Spreadsheet",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (preferences.isLoggedIn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (preferences.userPhotoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = preferences.userPhotoUrl,
                                    contentDescription = "Foto profil ${preferences.userName}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val initial = preferences.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preferences.userName.ifBlank { "Pengguna Google" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = preferences.userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = com.example.data.GoogleDriveSheetsClient.spreadsheetTitleFor(preferences.userEmail),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (preferences.spreadsheetId.isNotBlank()) {
                                            "Database khusus akun ini • ID: ${preferences.spreadsheetId.take(14)}..."
                                        } else {
                                            "Menyiapkan database khusus akun di Google Drive..."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (preferences.spreadsheetUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preferences.spreadsheetUrl))
                                            context.startActivity(intent)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_spreadsheet_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buka Sheets", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { showLogoutConfirmDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("logout_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ganti / Keluar", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mode Lokal (Database Perangkat)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Data tersimpan terpisah di perangkat. Hubungkan akun Google untuk memakai database Google Drive & Sheets per akun.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onConnectGoogleAccount,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connect_google_from_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hubungkan Akun Google & Sheets")
                        }
                    }
                }
            }
        }

        // Section 1: Keamanan & Autentikasi Biometrik / PIN / 2FA
        item {
            Text(
                text = "Keamanan Data Finansial",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Biometric Authentication Switch
                    val biometricStatus = remember(context) { BiometricHelper.getBiometricStatusMessage(context) }
                    SettingsSwitchRow(
                        icon = Icons.Default.Fingerprint,
                        title = "Autentikasi Biometrik",
                        subtitle = "Buka data keuangan dengan sidik jari atau wajah • $biometricStatus",
                        checked = preferences.isBiometricEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (!BiometricHelper.isBiometricAvailable(context)) {
                                    biometricWarningMsg = BiometricHelper.getBiometricStatusMessage(context)
                                } else {
                                    val act = BiometricHelper.findFragmentActivity(context)
                                    if (act != null) {
                                        BiometricHelper.showBiometricPrompt(
                                            activity = act,
                                            title = "Aktifkan Autentikasi Biometrik",
                                            subtitle = "Verifikasi biometrik Anda untuk mengamankan akses DanaLoka",
                                            negativeButtonText = "Batal",
                                            onSuccess = { onSetBiometric(true) },
                                            onError = { err -> biometricWarningMsg = err }
                                        )
                                    } else {
                                        onSetBiometric(true)
                                    }
                                }
                            } else {
                                onSetBiometric(false)
                            }
                        },
                        tag = "biometric_switch"
                    )

                    // Two-Factor Authentication (2FA) Switch
                    SettingsSwitchRow(
                        icon = Icons.Default.Security,
                        title = "Autentikasi Dua Faktor (2FA)",
                        subtitle = "Mewajibkan kode verifikasi 6 digit tambahan untuk proteksi maksimal",
                        checked = preferences.isTwoFactorEnabled,
                        onCheckedChange = { onSetTwoFactor(it) },
                        tag = "two_factor_switch"
                    )

                    // PIN Code Setup Row
                    SettingsClickableRow(
                        icon = Icons.Default.Pin,
                        title = "PIN Keamanan Aplikasi",
                        subtitle = if (preferences.pinCode.isEmpty()) "PIN belum diatur (Default: 1234)" else "PIN 4 digit aktif",
                        onClick = { showPinDialog = true },
                        tag = "setup_pin_row"
                    )

                    // Lock Now button
                    Button(
                        onClick = onLockAppNow,
                        modifier = Modifier.fillMaxWidth().testTag("lock_app_now_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kunci Aplikasi Sekarang")
                    }
                }
            }
        }

        // Section 2: Sinkronisasi Cloud Otomatis & Offline Mode
        item {
            Text(
                text = "Sinkronisasi Cloud & Cadangan",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSwitchRow(
                        icon = Icons.Default.CloudSync,
                        title = "Sinkronisasi Otomatis Google Sheets",
                        subtitle = "Setiap perubahan transaksi & saldo langsung disinkronkan ke Google Drive saat online",
                        checked = preferences.cloudSyncEnabled,
                        onCheckedChange = { onSetCloudSync(it) },
                        tag = "cloud_sync_switch"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = preferences.lastSyncStatus.ifBlank { "Status Sinkronisasi" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (preferences.lastSyncTime > 0L) {
                                    "Terakhir: ${DateUtils.formatDate(preferences.lastSyncTime)}"
                                } else {
                                    "Belum pernah disinkronkan ke cloud"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = onTriggerSync,
                            enabled = preferences.isLoggedIn && !isSyncing,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("sync_now_btn")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Proses...", fontSize = 12.sp)
                            } else {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sinkronkan", fontSize = 12.sp)
                            }
                        }
                    }

                    if (roomCacheStatus != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_room_cache_status")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cache Offline Room DB",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${roomCacheStatus.cachedTransactionCount} transaksi tersimpan lokal • " +
                                            if (roomCacheStatus.pendingCloudSync && preferences.isLoggedIn) {
                                                "Menunggu sinkronisasi cloud"
                                            } else {
                                                "Siap dipakai offline"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Preferensi Finansial & Tampilan
        item {
            Text(
                text = "Preferensi & Personalisasi",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Currency Selection
                    val currentCurrencyInfo = CurrencyHelper.supportedCurrencies.find { it.code == preferences.currencyCode }
                    SettingsClickableRow(
                        icon = Icons.Default.MonetizationOn,
                        title = "Multi-Mata Uang Transaksi",
                        subtitle = "${currentCurrencyInfo?.name ?: "Rupiah"} (${preferences.currencyCode} - ${currentCurrencyInfo?.symbol})",
                        onClick = { showCurrencyDialog = true },
                        tag = "currency_selector_row"
                    )

                    // Custom Categories Management
                    SettingsClickableRow(
                        icon = Icons.Default.Category,
                        title = "Kategori Pengeluaran & Pemasukan",
                        subtitle = "Kustomisasi nama, warna, ikon, dan batas anggaran",
                        onClick = onNavigateCategories,
                        tag = "manage_categories_row"
                    )

                    // Manage Balance Accounts (Wallets & Banks)
                    SettingsClickableRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Kelola Akun Saldo (Dompet & Rekening)",
                        subtitle = "Atur dompet tunai, rekening bank, & e-wallet serta pantau sisa saldo",
                        onClick = onNavigateAccounts,
                        tag = "manage_accounts_row"
                    )

                    // Recurring Bill Reminders
                    SettingsClickableRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Pengingat Tagihan Berkala",
                        subtitle = "Kelola jadwal tagihan rutin & pantau tanggal jatuh tempo",
                        onClick = onNavigateBills,
                        tag = "manage_bills_row"
                    )

                    // Bill Reminder Notifications Switch
                    SettingsSwitchRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Notifikasi Tagihan Jatuh Tempo",
                        subtitle = "Kirim notifikasi H-${preferences.billReminderDaysBefore} sebelum tagihan jatuh tempo",
                        checked = preferences.billNotificationsEnabled,
                        onCheckedChange = onSetBillNotifications,
                        tag = "bill_notifications_switch"
                    )

                    // Dark Mode / Theme Selection
                    val themeLabel = when (preferences.themeMode) {
                        "DARK" -> "Mode Gelap (Malam)"
                        "LIGHT" -> "Mode Terang (Siang)"
                        else -> "Mengikuti Sistem Perangkat"
                    }
                    SettingsClickableRow(
                        icon = Icons.Default.DarkMode,
                        title = "Mode Gelap / Tema Tampilan",
                        subtitle = themeLabel,
                        onClick = { showThemeDialog = true },
                        tag = "theme_mode_row"
                    )
                }
            }
        }

        // Section 4: Manajemen & Pembersihan Data
        item {
            Text(
                text = "Manajemen & Pembersihan Data",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsClickableRow(
                        icon = Icons.Default.FileDownload,
                        title = "Ekspor Laporan Keuangan (PDF, Excel & CSV)",
                        subtitle = "Pilih periode dan format laporan (.pdf, .xlsx, .csv) untuk diunduh atau dibagikan",
                        onClick = onExportReport,
                        tag = "export_report_row"
                    )

                    SettingsClickableRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "Kosongkan Semua Data Transaksi",
                        subtitle = "Mereset riwayat transaksi, hutang, dan tagihan agar akun bersih 0 rupiah seperti baru install",
                        onClick = { showResetConfirmDialog = true },
                        tag = "clear_all_data_row"
                    )
                }
            }
        }

        // About Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("DanaLoka - Catatan Keuangan & Anggaran v1.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Aman • Offline-First • Terenkripsi • PDF, Excel & CSV",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // Reset Confirm Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Kosongkan Semua Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Semua riwayat transaksi, hutang-piutang, dan jadwal tagihan akan dihapus bersih (saldo menjadi 0). Kategori kustom Anda tetap aman tersimpan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ya, Kosongkan Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Currency Dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Pilih Mata Uang Utama") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrencyHelper.supportedCurrencies.forEach { curr ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSetCurrency(curr.code)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(curr.name, fontWeight = FontWeight.SemiBold)
                                Text("${curr.code} (${curr.symbol})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (preferences.currencyCode == curr.code) {
                                Text("Aktif", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Tutup") }
            }
        )
    }

    // PIN Dialog
    if (showPinDialog) {
        var inputPin by remember { mutableStateOf(preferences.pinCode) }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Atur PIN Keamanan (4 Digit)") },
            text = {
                Column {
                    Text("Masukkan 4 digit PIN yang akan digunakan untuk membuka aplikasi.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                inputPin = it
                                pinError = null
                            }
                        },
                        label = { Text("PIN 4 Digit") },
                        placeholder = { Text("1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pin_code_setup_input")
                    )
                    if (pinError != null) {
                        Text(pinError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPin.length == 4) {
                            onSetPin(inputPin)
                            showPinDialog = false
                        } else {
                            pinError = "PIN harus terdiri dari 4 digit angka"
                        }
                    }
                ) {
                    Text("Simpan PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Batal") }
            }
        )
    }

    // Theme Mode Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Pilih Tema Tampilan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SYSTEM" to "Mengikuti Sistem Perangkat",
                        "DARK" to "Mode Gelap (Malam)",
                        "LIGHT" to "Mode Terang (Siang)"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSetThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontWeight = if (preferences.themeMode == mode) FontWeight.Bold else FontWeight.Normal)
                            if (preferences.themeMode == mode) {
                                Text("Pilihan", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Tutup") }
            }
        )
    }

    // Logout Confirm Dialog
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Keluar atau Ganti Akun?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Data akun ${preferences.userEmail} tersimpan terpisah di Google Drive & perangkat. Anda bisa masuk dengan akun Google lain (dengan database baru/sendiri) atau kembali ke akun ini kapan saja.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Keluar & Ganti Akun")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Biometric Hardware/Enrollment Warning Dialog
    if (biometricWarningMsg != null) {
        AlertDialog(
            onDismissRequest = { biometricWarningMsg = null },
            title = { Text("Biometrik Belum Siap", fontWeight = FontWeight.Bold) },
            text = { Text(biometricWarningMsg.orEmpty()) },
            confirmButton = {
                Button(onClick = { biometricWarningMsg = null }) {
                    Text("Mengerti")
                }
            }
        )
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
