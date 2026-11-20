package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CredentialSignInResult
import com.example.data.GoogleAuthHelper
import com.example.data.GoogleScopeAuthResult
import com.example.data.GoogleSignInIdentity
import com.example.ui.theme.TealContainerDark
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark
import com.example.util.BiometricHelper
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    isSyncingExternal: Boolean = false,
    statusMessageExternal: String? = null,
    errorMessageExternal: String? = null,
    onGoogleAuthorized: suspend (accessToken: String, identity: GoogleSignInIdentity?) -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { BiometricHelper.findFragmentActivity(context) }
    val scope = rememberCoroutineScope()

    var isAuthenticating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pendingIdentity by remember { mutableStateOf<GoogleSignInIdentity?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val act = activity
        if (result.resultCode == Activity.RESULT_OK && act != null) {
            val token = GoogleAuthHelper.extractAccessTokenFromIntent(act, result.data)
            if (!token.isNullOrBlank()) {
                scope.launch {
                    isAuthenticating = true
                    statusText = "Menghubungkan ke Google Drive & Spreadsheet..."
                    errorText = null
                    onGoogleAuthorized(token, pendingIdentity)
                    isAuthenticating = false
                    statusText = null
                }
            } else {
                isAuthenticating = false
                statusText = null
                errorText = "Gagal mendapatkan izin akses Google Drive & Spreadsheet."
            }
        } else {
            isAuthenticating = false
            statusText = null
            errorText = "Izin akses Google Drive & Google Sheets dibatalkan."
        }
    }

    fun startGoogleSignInFlow() {
        val act = activity
        if (act == null) {
            errorText = "Activity tidak tersedia untuk login Google."
            return
        }
        scope.launch {
            isAuthenticating = true
            errorText = null
            statusText = "Memilih akun Google..."

            when (val credResult = GoogleAuthHelper.signInWithCredentialManager(act)) {
                is CredentialSignInResult.Success -> {
                    pendingIdentity = credResult.identity
                    statusText = "Meminta izin Google Drive & Spreadsheet..."
                    when (val scopeRes = GoogleAuthHelper.authorizeDriveAndSheets(act)) {
                        is GoogleScopeAuthResult.Authorized -> {
                            statusText = "Memulihkan data akun ${credResult.identity.email}..."
                            onGoogleAuthorized(scopeRes.accessToken, credResult.identity)
                            isAuthenticating = false
                            statusText = null
                        }
                        is GoogleScopeAuthResult.NeedsUserConsent -> {
                            statusText = "Menunggu persetujuan Google Drive & Sheets..."
                            consentLauncher.launch(
                                IntentSenderRequest.Builder(scopeRes.pendingIntent).build()
                            )
                        }
                        is GoogleScopeAuthResult.Error -> {
                            isAuthenticating = false
                            statusText = null
                            errorText = scopeRes.message
                        }
                    }
                }
                is CredentialSignInResult.Cancelled -> {
                    isAuthenticating = false
                    statusText = null
                    errorText = credResult.message
                }
                is CredentialSignInResult.Error -> {
                    // Fallback: allow AuthorizationClient account+scope consent flow if no local CredentialManager cache exists
                    if (credResult.message.startsWith("NO_CREDENTIAL")) {
                        statusText = "Membuka otorisasi akun Google..."
                        pendingIdentity = null
                        when (val scopeRes = GoogleAuthHelper.authorizeDriveAndSheets(act)) {
                            is GoogleScopeAuthResult.Authorized -> {
                                statusText = "Memulihkan data dari Google Drive..."
                                onGoogleAuthorized(scopeRes.accessToken, null)
                                isAuthenticating = false
                                statusText = null
                            }
                            is GoogleScopeAuthResult.NeedsUserConsent -> {
                                consentLauncher.launch(
                                    IntentSenderRequest.Builder(scopeRes.pendingIntent).build()
                                )
                            }
                            is GoogleScopeAuthResult.Error -> {
                                isAuthenticating = false
                                statusText = null
                                errorText = "Belum ada akun Google di perangkat. Tambahkan akun Google di Pengaturan Android atau gunakan Mode Lokal."
                            }
                        }
                    } else {
                        isAuthenticating = false
                        statusText = null
                        errorText = credResult.message
                    }
                }
            }
        }
    }

    val isBusy = isAuthenticating || isSyncingExternal
    val activeStatus = statusMessageExternal ?: statusText
    val activeError = errorMessageExternal ?: errorText

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TealContainerDark,
                        TealPrimary,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.88f, size.height * 0.08f)
                )
                drawCircle(
                    color = TealPrimaryDark.copy(alpha = 0.10f),
                    radius = size.width * 0.30f,
                    center = Offset(size.width * 0.12f, size.height * 0.22f)
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("login_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Hero Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Logo DanaLoka",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "DanaLoka",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Catatan Keuangan & Anggaran",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.90f),
                    textAlign = TextAlign.Center
                )
            }

            // Main Auth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Masuk & Pulihkan Data",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Gunakan akun Google Anda agar data transaksi, saldo rekening, anggaran, dan hutang-piutang otomatis tersimpan dan kembali saat login lagi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }

                    // Feature Highlights
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoginFeatureItem(
                            icon = Icons.Default.TableChart,
                            title = "Database Google Drive & Sheets",
                            subtitle = "Data dicadangkan otomatis ke file DompetKu_Database di Google Drive pribadi Anda."
                        )
                        LoginFeatureItem(
                            icon = Icons.Default.Restore,
                            title = "Pemulihan Akun Otomatis",
                            subtitle = "Masuk kembali dengan akun Google yang sama untuk memulihkan seluruh catatan."
                        )
                        LoginFeatureItem(
                            icon = Icons.Default.Fingerprint,
                            title = "Biometrik & Laporan PDF",
                            subtitle = "Lindungi dompet dengan sidik jari/PIN serta ekspor laporan bulanan PDF."
                        )
                    }

                    // Error Alert
                    AnimatedVisibility(visible = !activeError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = activeError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Syncing Status
                    AnimatedVisibility(visible = isBusy) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = activeStatus ?: "Menghubungkan ke Google...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Primary Google Sign-In CTA
                    Button(
                        onClick = { startGoogleSignInFlow() },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("google_sign_in_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isBusy) "Memproses Akun Google..." else "Masuk dengan Google",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Secondary Local Mode CTA
                    OutlinedButton(
                        onClick = onContinueOffline,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("offline_mode_btn")
                    ) {
                        Text(
                            text = "Lanjutkan Tanpa Akun (Mode Lokal)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Footer Note
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Spreadsheet hanya diakses oleh DanaLoka di Google Drive Anda",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LoginFeatureItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
