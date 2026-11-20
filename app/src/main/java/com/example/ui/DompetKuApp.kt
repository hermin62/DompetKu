package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.data.local.RoomCacheRepository
import com.example.data.local.RoomCacheStatus
import com.example.ui.components.AppSecurityLockScreen
import com.example.ui.components.ExportReportDialog
import com.example.ui.screens.*
import com.example.ui.viewmodel.WeeklyInsightViewModel
import com.example.util.BillNotificationHelper
import com.example.util.BiometricHelper
import com.example.util.ExportFormat
import com.example.util.ExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DompetKuApp(
    repository: FinanceRepository = remember { FinanceRepository() },
    prefsManager: PreferencesManager? = null
) {
    val context = LocalContext.current
    val resolvedPrefsManager = prefsManager ?: remember { PreferencesManager(context.applicationContext) }
    val preferences by resolvedPrefsManager.userPreferences.collectAsStateWithLifecycle()
    val activeAccountKey = preferences.activeAccountKey

    val localBackup = remember { LocalBackupStorage(context.applicationContext) }
    val roomCache = remember { RoomCacheRepository.fromContext(context.applicationContext) }
    val syncCoordinator = remember { CloudSyncCoordinator() }
    val activity = remember(context) { BiometricHelper.findFragmentActivity(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val roomCacheStatus by remember(roomCache, activeAccountKey) {
        roomCache.observeCacheStatus(activeAccountKey.ifBlank { "local_offline" })
    }.collectAsStateWithLifecycle(initialValue = RoomCacheStatus())

    val transactions by repository.transactions.collectAsStateWithLifecycle()
    val categories by repository.categories.collectAsStateWithLifecycle()
    val accounts by repository.accounts.collectAsStateWithLifecycle()
    val debts by repository.debts.collectAsStateWithLifecycle()
    val bills by repository.bills.collectAsStateWithLifecycle()
    val activeCurrency by repository.activeCurrency.collectAsStateWithLifecycle()
    val overallMonthlyBudget by repository.overallMonthlyBudget.collectAsStateWithLifecycle()
    val dataRevision by repository.dataRevision.collectAsStateWithLifecycle()

    val weeklyInsightViewModel: WeeklyInsightViewModel = viewModel()
    val selectedInsightPeriod by weeklyInsightViewModel.selectedPeriod.collectAsStateWithLifecycle()
    val weeklyInsightState by weeklyInsightViewModel.weeklyState.collectAsStateWithLifecycle()
    val monthlyInsightState by weeklyInsightViewModel.monthlyState.collectAsStateWithLifecycle()
    val weeklyMetrics = remember(
        activeAccountKey,
        transactions,
        categories,
        activeCurrency,
        overallMonthlyBudget
    ) {
        WeeklySummaryCalculator.calculate(
            accountKey = activeAccountKey,
            transactions = transactions,
            categories = categories,
            currencyCode = activeCurrency,
            overallMonthlyBudget = overallMonthlyBudget
        )
    }
    val monthlyMetrics = remember(
        activeAccountKey,
        transactions,
        categories,
        activeCurrency,
        overallMonthlyBudget
    ) {
        WeeklySummaryCalculator.calculateMonthly(
            accountKey = activeAccountKey,
            transactions = transactions,
            categories = categories,
            currencyCode = activeCurrency,
            overallMonthlyBudget = overallMonthlyBudget
        )
    }
    val activeInsightMetrics = if (selectedInsightPeriod == InsightPeriod.MONTHLY) {
        monthlyMetrics
    } else {
        weeklyMetrics
    }
    val activeInsightState = if (selectedInsightPeriod == InsightPeriod.MONTHLY) {
        monthlyInsightState
    } else {
        weeklyInsightState
    }

    var currentScreen by remember { mutableIntStateOf(0) }
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var selectedTransactionForEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var initialDateForNewTransaction by remember { mutableStateOf(System.currentTimeMillis()) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fileToSave = pendingSaveFile
        pendingSaveFile = null
        if (uri != null && fileToSave != null) {
            val saved = ExportHelper.saveFileToUri(context, fileToSave, uri)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (saved) "Laporan berhasil diunduh & disimpan"
                    else "Gagal menyimpan laporan ke perangkat"
                )
            }
        }
    }

    var cachedAccessToken by remember { mutableStateOf<String?>(null) }
    var cachedTokenAccountKey by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var initializedAccountKey by remember { mutableStateOf("") }
    var showConnectGoogleSheet by remember { mutableStateOf(false) }

    var isAppLocked by remember {
        mutableStateOf(
            preferences.isBiometricEnabled ||
                preferences.pinCode.isNotEmpty() ||
                preferences.isTwoFactorEnabled
        )
    }

    val manualSyncConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val act = activity
        val currentKey = preferences.activeAccountKey
        if (result.resultCode == Activity.RESULT_OK && act != null && currentKey.isNotBlank()) {
            val token = GoogleAuthHelper.extractAccessTokenFromIntent(act, result.data)
            if (!token.isNullOrBlank()) {
                cachedAccessToken = token
                cachedTokenAccountKey = currentKey
                scope.launch {
                    isSyncing = true
                    val outcome = syncCoordinator.pushSnapshot(
                        accessToken = token,
                        repository = repository,
                        prefsManager = resolvedPrefsManager,
                        localBackup = localBackup
                    )
                    if (outcome is SyncOutcome.Success) {
                        withContext(Dispatchers.IO) {
                            roomCache.markCloudSynced(currentKey)
                        }
                    }
                    isSyncing = false
                    val msg = when (outcome) {
                        is SyncOutcome.Success -> outcome.message
                        is SyncOutcome.Unauthorized -> outcome.message
                        is SyncOutcome.Error -> outcome.message
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    // Restore account-specific Room DB cache first (offline-first), then migrate/sync cloud lazily
    LaunchedEffect(activeAccountKey) {
        if (activeAccountKey.isNotBlank() && initializedAccountKey != activeAccountKey) {
            val roomSaved = withContext(Dispatchers.IO) {
                roomCache.loadSnapshot(activeAccountKey)
            }?.takeIf {
                it.ownerEmail.isBlank() ||
                    activeAccountKey == "local_offline" ||
                    it.ownerEmail.equals(activeAccountKey, ignoreCase = true)
            }

            if (roomSaved != null) {
                repository.restoreSnapshot(roomSaved)
                resolvedPrefsManager.setCurrency(roomSaved.currencyCode)
                resolvedPrefsManager.setOverallMonthlyBudget(roomSaved.overallMonthlyBudget)
            } else {
                val legacySaved = localBackup.loadSnapshot(activeAccountKey)
                    ?.takeIf {
                        it.ownerEmail.isBlank() ||
                            activeAccountKey == "local_offline" ||
                            it.ownerEmail.equals(activeAccountKey, ignoreCase = true)
                    }
                if (legacySaved != null) {
                    repository.restoreSnapshot(legacySaved)
                    resolvedPrefsManager.setCurrency(legacySaved.currencyCode)
                    resolvedPrefsManager.setOverallMonthlyBudget(legacySaved.overallMonthlyBudget)
                    withContext(Dispatchers.IO) {
                        roomCache.saveSnapshot(activeAccountKey, legacySaved, pendingCloudSync = false)
                    }
                } else {
                    repository.resetToStarterData()
                    val ownerEmail = if (preferences.isLoggedIn) preferences.userEmail else ""
                    val starterSnapshot = repository.exportSnapshot(ownerEmail = ownerEmail)
                    withContext(Dispatchers.IO) {
                        roomCache.saveSnapshot(activeAccountKey, starterSnapshot, pendingCloudSync = false)
                    }
                }
            }
            initializedAccountKey = activeAccountKey

            // Optional cloud reconcile on startup (never blocks offline Room workspace)
            if (preferences.isLoggedIn && preferences.cloudSyncEnabled && activity != null) {
                val hasPendingLocalEdits = withContext(Dispatchers.IO) {
                    roomCache.isPendingCloudSync(activeAccountKey)
                }
                val token = if (cachedTokenAccountKey == activeAccountKey && !cachedAccessToken.isNullOrBlank()) {
                    cachedAccessToken
                } else {
                    when (val auth = GoogleAuthHelper.authorizeDriveAndSheets(activity)) {
                        is GoogleScopeAuthResult.Authorized -> auth.accessToken.also {
                            cachedAccessToken = it
                            cachedTokenAccountKey = activeAccountKey
                        }
                        else -> null
                    }
                }
                if (!token.isNullOrBlank()) {
                    isSyncing = true
                    var outcome = if (hasPendingLocalEdits) {
                        syncCoordinator.pushSnapshot(
                            accessToken = token,
                            repository = repository,
                            prefsManager = resolvedPrefsManager,
                            localBackup = localBackup
                        )
                    } else {
                        syncCoordinator.syncOnStartup(
                            accessToken = token,
                            repository = repository,
                            prefsManager = resolvedPrefsManager,
                            localBackup = localBackup
                        )
                    }
                    if (outcome is SyncOutcome.Unauthorized) {
                        GoogleAuthHelper.invalidateAccessToken(activity, token)
                        cachedAccessToken = null
                        cachedTokenAccountKey = ""
                        if (preferences.activeAccountKey == activeAccountKey) {
                            when (val retryAuth = GoogleAuthHelper.authorizeDriveAndSheets(activity)) {
                                is GoogleScopeAuthResult.Authorized -> {
                                    cachedAccessToken = retryAuth.accessToken
                                    cachedTokenAccountKey = activeAccountKey
                                    outcome = if (hasPendingLocalEdits) {
                                        syncCoordinator.pushSnapshot(
                                            accessToken = retryAuth.accessToken,
                                            repository = repository,
                                            prefsManager = resolvedPrefsManager,
                                            localBackup = localBackup
                                        )
                                    } else {
                                        syncCoordinator.syncOnStartup(
                                            accessToken = retryAuth.accessToken,
                                            repository = repository,
                                            prefsManager = resolvedPrefsManager,
                                            localBackup = localBackup
                                        )
                                    }
                                    if (outcome is SyncOutcome.Unauthorized) {
                                        GoogleAuthHelper.invalidateAccessToken(activity, retryAuth.accessToken)
                                        cachedAccessToken = null
                                        cachedTokenAccountKey = ""
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                    if (outcome is SyncOutcome.Success && preferences.activeAccountKey == activeAccountKey) {
                        val ownerEmail = if (preferences.isLoggedIn) preferences.userEmail else ""
                        val syncedSnapshot = repository.exportSnapshot(ownerEmail = ownerEmail)
                        withContext(Dispatchers.IO) {
                            roomCache.saveSnapshot(activeAccountKey, syncedSnapshot, pendingCloudSync = false)
                        }
                    }
                    isSyncing = false
                }
            }
        } else if (activeAccountKey.isBlank()) {
            initializedAccountKey = ""
            cachedAccessToken = null
            cachedTokenAccountKey = ""
        }
    }

    // Immediate offline-first Room persistence + relaxed cloud push debounce scoped to activeAccountKey
    LaunchedEffect(dataRevision, activeAccountKey) {
        if (dataRevision > 0L && activeAccountKey.isNotBlank() && initializedAccountKey == activeAccountKey) {
            val targetKey = activeAccountKey
            val ownerEmail = if (preferences.isLoggedIn) preferences.userEmail else ""
            delay(250L)

            if (preferences.activeAccountKey != targetKey) return@LaunchedEffect

            val snapshot = repository.exportSnapshot(ownerEmail = ownerEmail)
            val needsCloudSync = preferences.isLoggedIn && preferences.cloudSyncEnabled
            withContext(Dispatchers.IO) {
                roomCache.saveSnapshot(targetKey, snapshot, pendingCloudSync = needsCloudSync)
                localBackup.saveSnapshot(targetKey, snapshot)
            }

            if (needsCloudSync) {
                // Relaxed debounce to avoid continuous real-time cloud dependency
                delay(2250L)
                if (preferences.activeAccountKey != targetKey) return@LaunchedEffect

                val token = if (cachedTokenAccountKey == targetKey && !cachedAccessToken.isNullOrBlank()) {
                    cachedAccessToken
                } else {
                    activity?.let { act ->
                        when (val auth = GoogleAuthHelper.authorizeDriveAndSheets(act)) {
                            is GoogleScopeAuthResult.Authorized -> auth.accessToken.also {
                                cachedAccessToken = it
                                cachedTokenAccountKey = targetKey
                            }
                            else -> null
                        }
                    }
                }
                if (!token.isNullOrBlank() && preferences.activeAccountKey == targetKey) {
                    isSyncing = true
                    var outcome = syncCoordinator.pushSnapshot(
                        accessToken = token,
                        repository = repository,
                        prefsManager = resolvedPrefsManager,
                        localBackup = localBackup
                    )
                    if (outcome is SyncOutcome.Unauthorized) {
                        activity?.let { act -> GoogleAuthHelper.invalidateAccessToken(act, token) }
                        cachedAccessToken = null
                        cachedTokenAccountKey = ""
                        if (activity != null && preferences.activeAccountKey == targetKey) {
                            when (val retryAuth = GoogleAuthHelper.authorizeDriveAndSheets(activity)) {
                                is GoogleScopeAuthResult.Authorized -> {
                                    cachedAccessToken = retryAuth.accessToken
                                    cachedTokenAccountKey = targetKey
                                    outcome = syncCoordinator.pushSnapshot(
                                        accessToken = retryAuth.accessToken,
                                        repository = repository,
                                        prefsManager = resolvedPrefsManager,
                                        localBackup = localBackup
                                    )
                                    if (outcome is SyncOutcome.Unauthorized) {
                                        GoogleAuthHelper.invalidateAccessToken(activity, retryAuth.accessToken)
                                        cachedAccessToken = null
                                        cachedTokenAccountKey = ""
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                    if (outcome is SyncOutcome.Success && preferences.activeAccountKey == targetKey) {
                        withContext(Dispatchers.IO) {
                            roomCache.markCloudSynced(targetKey)
                        }
                    }
                    isSyncing = false
                }
            }
        }
    }

    val isWorkspaceReady = (preferences.isLoggedIn || preferences.isOfflineMode) &&
        !showConnectGoogleSheet &&
        !isAppLocked &&
        activeAccountKey.isNotBlank() &&
        initializedAccountKey == activeAccountKey

    LaunchedEffect(
        isWorkspaceReady,
        activeAccountKey,
        selectedInsightPeriod,
        activeInsightMetrics.dataSignature
    ) {
        if (isWorkspaceReady) {
            weeklyInsightViewModel.analyzePeriodFinances(
                accountKey = activeAccountKey,
                metrics = activeInsightMetrics
            )
        }
    }

    LaunchedEffect(isWorkspaceReady, bills, preferences.billNotificationsEnabled, preferences.billReminderDaysBefore) {
        if (isWorkspaceReady) {
            BillNotificationHelper.schedulePeriodicCheck(context.applicationContext)
            if (preferences.billNotificationsEnabled && bills.isNotEmpty()) {
                BillNotificationHelper.checkAndNotifyUpcomingBills(
                    context = context.applicationContext,
                    bills = bills,
                    activeCurrency = activeCurrency,
                    reminderDaysBefore = preferences.billReminderDaysBefore
                )
            }
        }
    }

    fun triggerManualCloudSync() {
        val act = activity ?: return
        val currentKey = preferences.activeAccountKey
        if (currentKey.isBlank()) return

        scope.launch {
            isSyncing = true
            val existingToken = if (cachedTokenAccountKey == currentKey) cachedAccessToken else null
            if (!existingToken.isNullOrBlank()) {
                val outcome = syncCoordinator.pushSnapshot(
                    accessToken = existingToken,
                    repository = repository,
                    prefsManager = resolvedPrefsManager,
                    localBackup = localBackup
                )
                if (outcome !is SyncOutcome.Unauthorized) {
                    if (outcome is SyncOutcome.Success) {
                        withContext(Dispatchers.IO) {
                            roomCache.markCloudSynced(currentKey)
                        }
                    }
                    isSyncing = false
                    val msg = when (outcome) {
                        is SyncOutcome.Success -> outcome.message
                        is SyncOutcome.Error -> outcome.message
                        is SyncOutcome.Unauthorized -> outcome.message
                    }
                    snackbarHostState.showSnackbar(msg)
                    return@launch
                }
                GoogleAuthHelper.invalidateAccessToken(act, existingToken)
                cachedAccessToken = null
                cachedTokenAccountKey = ""
            }

            when (val auth = GoogleAuthHelper.authorizeDriveAndSheets(act)) {
                is GoogleScopeAuthResult.Authorized -> {
                    cachedAccessToken = auth.accessToken
                    cachedTokenAccountKey = currentKey
                    var outcome = syncCoordinator.pushSnapshot(
                        accessToken = auth.accessToken,
                        repository = repository,
                        prefsManager = resolvedPrefsManager,
                        localBackup = localBackup
                    )
                    if (outcome is SyncOutcome.Unauthorized) {
                        GoogleAuthHelper.invalidateAccessToken(act, auth.accessToken)
                        cachedAccessToken = null
                        cachedTokenAccountKey = ""
                        when (val refreshedAuth = GoogleAuthHelper.authorizeDriveAndSheets(act)) {
                            is GoogleScopeAuthResult.Authorized -> {
                                cachedAccessToken = refreshedAuth.accessToken
                                cachedTokenAccountKey = currentKey
                                outcome = syncCoordinator.pushSnapshot(
                                    accessToken = refreshedAuth.accessToken,
                                    repository = repository,
                                    prefsManager = resolvedPrefsManager,
                                    localBackup = localBackup
                                )
                            }
                            is GoogleScopeAuthResult.NeedsUserConsent -> {
                                isSyncing = false
                                manualSyncConsentLauncher.launch(
                                    IntentSenderRequest.Builder(refreshedAuth.pendingIntent).build()
                                )
                                return@launch
                            }
                            is GoogleScopeAuthResult.Error -> {
                                isSyncing = false
                                snackbarHostState.showSnackbar(refreshedAuth.message)
                                return@launch
                            }
                        }
                    }
                    if (outcome is SyncOutcome.Success) {
                        withContext(Dispatchers.IO) {
                            roomCache.markCloudSynced(currentKey)
                        }
                    }
                    isSyncing = false
                    val msg = when (outcome) {
                        is SyncOutcome.Success -> outcome.message
                        is SyncOutcome.Unauthorized -> outcome.message
                        is SyncOutcome.Error -> outcome.message
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is GoogleScopeAuthResult.NeedsUserConsent -> {
                    isSyncing = false
                    manualSyncConsentLauncher.launch(
                        IntentSenderRequest.Builder(auth.pendingIntent).build()
                    )
                }
                is GoogleScopeAuthResult.Error -> {
                    isSyncing = false
                    snackbarHostState.showSnackbar(auth.message)
                }
            }
        }
    }

    // Gate 1: Login Page (first launch, after sign out, or when upgrading from local mode)
    if ((!preferences.isLoggedIn && !preferences.isOfflineMode) || showConnectGoogleSheet) {
        val isUpgradeFromOffline = showConnectGoogleSheet && preferences.isOfflineMode
        BackHandler(enabled = isUpgradeFromOffline) {
            showConnectGoogleSheet = false
        }

        var loginError by remember { mutableStateOf<String?>(null) }
        var loginStatus by remember { mutableStateOf<String?>(null) }
        var isCompletingLogin by remember { mutableStateOf(false) }

        LoginScreen(
            isSyncingExternal = isCompletingLogin,
            statusMessageExternal = loginStatus,
            errorMessageExternal = loginError,
            onGoogleAuthorized = { token, identity ->
                isCompletingLogin = true
                loginError = null
                loginStatus = "Menyiapkan database akun di Google Drive..."

                val outcome = syncCoordinator.completeSignInAndRestore(
                    accessToken = token,
                    initialIdentity = identity,
                    wasOfflineUpgrade = isUpgradeFromOffline,
                    repository = repository,
                    prefsManager = resolvedPrefsManager,
                    localBackup = localBackup
                )

                isCompletingLogin = false
                loginStatus = null

                val signedInKey = resolvedPrefsManager.userPreferences.value.activeAccountKey
                if (signedInKey.isNotBlank()) {
                    cachedAccessToken = token
                    cachedTokenAccountKey = signedInKey
                    initializedAccountKey = signedInKey
                }

                when (outcome) {
                    is SyncOutcome.Success -> {
                        if (signedInKey.isNotBlank()) {
                            val email = resolvedPrefsManager.userPreferences.value.userEmail
                            val syncedSnapshot = repository.exportSnapshot(ownerEmail = email)
                            withContext(Dispatchers.IO) {
                                roomCache.saveSnapshot(signedInKey, syncedSnapshot, pendingCloudSync = false)
                            }
                        }
                        showConnectGoogleSheet = false
                        currentScreen = 0
                        scope.launch {
                            snackbarHostState.showSnackbar(outcome.message)
                        }
                    }
                    is SyncOutcome.Unauthorized -> {
                        activity?.let { act -> GoogleAuthHelper.invalidateAccessToken(act, token) }
                        cachedAccessToken = null
                        cachedTokenAccountKey = ""
                        loginError = outcome.message
                    }
                    is SyncOutcome.Error -> {
                        if (resolvedPrefsManager.userPreferences.value.isLoggedIn) {
                            showConnectGoogleSheet = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Masuk berhasil (sinkronisasi cloud tertunda: ${outcome.message})")
                            }
                        } else {
                            loginError = outcome.message
                        }
                    }
                }
            },
            onContinueOffline = {
                if (isUpgradeFromOffline) {
                    showConnectGoogleSheet = false
                } else {
                    resolvedPrefsManager.setOfflineMode(true)
                    scope.launch {
                        val roomSaved = withContext(Dispatchers.IO) {
                            roomCache.loadSnapshot("local_offline")
                        }
                        val localSaved = roomSaved ?: localBackup.loadSnapshot("local_offline")
                        if (localSaved != null) {
                            repository.restoreSnapshot(localSaved)
                            if (roomSaved == null) {
                                withContext(Dispatchers.IO) {
                                    roomCache.saveSnapshot("local_offline", localSaved, pendingCloudSync = false)
                                }
                            }
                        } else {
                            repository.resetToStarterData()
                            val starter = repository.exportSnapshot(ownerEmail = "")
                            withContext(Dispatchers.IO) {
                                roomCache.saveSnapshot("local_offline", starter, pendingCloudSync = false)
                            }
                        }
                        initializedAccountKey = "local_offline"
                    }
                }
            }
        )
        return
    }

    // Gate 2: Biometric / PIN / 2FA Security Lock Screen
    if (isAppLocked) {
        AppSecurityLockScreen(
            correctPin = preferences.pinCode,
            isBiometricEnabled = preferences.isBiometricEnabled,
            isTwoFactorEnabled = preferences.isTwoFactorEnabled,
            expectedTwoFactorCode = preferences.twoFactorCode,
            onUnlocked = { isAppLocked = false }
        )
        return
    }

    // Intercept back button to return to Beranda
    BackHandler(enabled = currentScreen != 0) {
        currentScreen = 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("dompetku_bottom_nav")) {
                NavigationBarItem(
                    selected = currentScreen == 0,
                    onClick = { currentScreen = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Beranda") },
                    label = {
                        Text(
                            text = "Beranda",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (currentScreen == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("nav_home")
                )
                NavigationBarItem(
                    selected = currentScreen == 1,
                    onClick = { currentScreen = 1 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Riwayat") },
                    label = {
                        Text(
                            text = "Riwayat",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (currentScreen == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("nav_history")
                )
                NavigationBarItem(
                    selected = currentScreen == 2,
                    onClick = { currentScreen = 2 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Akun Saldo") },
                    label = {
                        Text(
                            text = "Akun Saldo",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (currentScreen == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("nav_accounts")
                )
                NavigationBarItem(
                    selected = currentScreen == 4,
                    onClick = { currentScreen = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                    label = {
                        Text(
                            text = "Pengaturan",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (currentScreen == 4) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)

        when (currentScreen) {
            0 -> {
                DashboardScreen(
                    transactions = transactions,
                    categories = categories,
                    accounts = accounts,
                    debts = debts,
                    bills = bills,
                    activeCurrency = activeCurrency,
                    overallMonthlyBudget = overallMonthlyBudget,
                    onAddTransaction = {
                        selectedTransactionForEdit = null
                        initialDateForNewTransaction = System.currentTimeMillis()
                        showAddTransactionSheet = true
                    },
                    onNavigateToTransactions = { currentScreen = 1 },
                    onNavigateToCategories = { currentScreen = 3 },
                    onNavigateToAccounts = { currentScreen = 2 },
                    onNavigateToDebts = { currentScreen = 5 },
                    onNavigateToBills = { currentScreen = 7 },
                    onTransactionClick = { tx ->
                        selectedTransactionForEdit = tx
                        showAddTransactionSheet = true
                    },
                    onExportReport = { showExportDialog = true },
                    weeklyMetrics = activeInsightMetrics,
                    weeklyInsightState = activeInsightState,
                    selectedInsightPeriod = selectedInsightPeriod,
                    onSelectInsightPeriod = { period ->
                        weeklyInsightViewModel.setSelectedPeriod(period)
                    },
                    onRefreshWeeklyInsight = {
                        weeklyInsightViewModel.analyzePeriodFinances(
                            accountKey = activeAccountKey,
                            metrics = activeInsightMetrics,
                            forceRefresh = true
                        )
                    },
                    modifier = screenModifier
                )
            }
            1 -> {
                TransactionsScreen(
                    transactions = transactions,
                    categories = categories,
                    accounts = accounts,
                    activeCurrency = activeCurrency,
                    onAddTransaction = {
                        selectedTransactionForEdit = null
                        initialDateForNewTransaction = System.currentTimeMillis()
                        showAddTransactionSheet = true
                    },
                    onTransactionClick = { tx ->
                        selectedTransactionForEdit = tx
                        showAddTransactionSheet = true
                    },
                    onClearAllTransactions = { repository.clearAllTransactions() },
                    onAddTransactionWithDate = { dateMillis ->
                        selectedTransactionForEdit = null
                        initialDateForNewTransaction = dateMillis
                        showAddTransactionSheet = true
                    },
                    onExportClick = { showExportDialog = true },
                    modifier = screenModifier
                )
            }
            2 -> {
                AccountManagementScreen(
                    accounts = accounts,
                    activeCurrency = activeCurrency,
                    onAddAccount = { repository.addAccount(it) },
                    onUpdateAccount = { repository.updateAccount(it) },
                    onDeleteAccount = { repository.deleteAccount(it) },
                    onAdjustBalance = { accId, newBal -> repository.setAccountBalance(accId, newBal) },
                    onTransfer = { fromId, toId, amt, notes -> repository.transferFunds(fromId, toId, amt, notes) },
                    modifier = screenModifier
                )
            }
            3 -> {
                CategoryManagementScreen(
                    categories = categories,
                    transactions = transactions,
                    activeCurrency = activeCurrency,
                    overallMonthlyBudget = overallMonthlyBudget,
                    onAddCategory = { repository.addCategory(it) },
                    onUpdateCategory = { repository.updateCategory(it) },
                    onDeleteCategory = { repository.deleteCategory(it) },
                    onSwitchGroup = { repository.switchCategoryGroup(it) },
                    onSetOverallBudget = { total, strategy -> repository.setOverallMonthlyBudget(total, strategy) },
                    onUpdateCategoryBudget = { catId, budget -> repository.updateCategoryBudget(catId, budget) },
                    onBack = { currentScreen = 0 },
                    modifier = screenModifier
                )
            }
            4 -> {
                SettingsScreen(
                    preferences = preferences,
                    isSyncing = isSyncing,
                    roomCacheStatus = roomCacheStatus,
                    onSetCurrency = {
                        resolvedPrefsManager.setCurrency(it)
                        repository.setActiveCurrency(it)
                    },
                    onSetBiometric = { resolvedPrefsManager.setBiometricEnabled(it) },
                    onSetTwoFactor = { resolvedPrefsManager.setTwoFactorEnabled(it) },
                    onSetPin = { resolvedPrefsManager.setPinCode(it) },
                    onSetThemeMode = { resolvedPrefsManager.setThemeMode(it) },
                    onSetCloudSync = { resolvedPrefsManager.setCloudSyncEnabled(it) },
                    onSetBillNotifications = { resolvedPrefsManager.setBillNotificationsEnabled(it) },
                    onTriggerSync = { triggerManualCloudSync() },
                    onConnectGoogleAccount = { showConnectGoogleSheet = true },
                    onSignOut = {
                        val keyToFlush = preferences.activeAccountKey
                        val emailToFlush = if (preferences.isLoggedIn) preferences.userEmail else ""
                        val snapshotToFlush = if (keyToFlush.isNotBlank()) {
                            repository.exportSnapshot(ownerEmail = emailToFlush)
                        } else null
                        if (keyToFlush.isNotBlank() && snapshotToFlush != null) {
                            localBackup.saveSnapshot(keyToFlush, snapshotToFlush)
                        }
                        scope.launch {
                            if (keyToFlush.isNotBlank() && snapshotToFlush != null) {
                                withContext(Dispatchers.IO) {
                                    roomCache.saveSnapshot(
                                        keyToFlush,
                                        snapshotToFlush,
                                        pendingCloudSync = false
                                    )
                                }
                            }
                            GoogleAuthHelper.clearCredentialState(context)
                            cachedAccessToken = null
                            cachedTokenAccountKey = ""
                            initializedAccountKey = ""
                            weeklyInsightViewModel.resetForAccountChange()
                            isAppLocked = false
                            currentScreen = 0
                            resolvedPrefsManager.clearAuthSession()
                            repository.resetToStarterData()
                        }
                    },
                    onLockAppNow = { isAppLocked = true },
                    onNavigateCategories = { currentScreen = 3 },
                    onNavigateAccounts = { currentScreen = 2 },
                    onNavigateBills = { currentScreen = 7 },
                    onExportReport = { showExportDialog = true },
                    onClearAllData = { repository.clearAllTransactions() },
                    modifier = screenModifier
                )
            }
            5 -> {
                DebtLoanScreen(
                    debts = debts,
                    accounts = accounts,
                    activeCurrency = activeCurrency,
                    onAddDebt = { person, amt, type, due, notes, phone, curr ->
                        repository.addDebt(
                            DebtLoanEntity(
                                personName = person,
                                totalAmount = amt,
                                remainingAmount = amt,
                                type = type,
                                dueDateMillis = due,
                                notes = notes,
                                phone = phone,
                                currencyCode = curr
                            )
                        )
                    },
                    onRepayDebt = { debtId, amt, accId -> repository.repayDebt(debtId, amt, accId) },
                    onDeleteDebt = { repository.deleteDebt(it) },
                    onBack = { currentScreen = 0 },
                    modifier = screenModifier
                )
            }
            6 -> {
                FinancialCalendarScreen(
                    transactions = transactions,
                    categories = categories,
                    activeCurrency = activeCurrency,
                    onAddTransactionWithDate = { dateMillis ->
                        selectedTransactionForEdit = null
                        initialDateForNewTransaction = dateMillis
                        showAddTransactionSheet = true
                    },
                    onTransactionClick = { tx ->
                        selectedTransactionForEdit = tx
                        showAddTransactionSheet = true
                    },
                    modifier = screenModifier
                )
            }
            7 -> {
                BillReminderScreen(
                    bills = bills,
                    categories = categories,
                    accounts = accounts,
                    activeCurrency = activeCurrency,
                    reminderDaysBefore = preferences.billReminderDaysBefore,
                    notificationsEnabled = preferences.billNotificationsEnabled,
                    onAddBill = { title, amt, catId, due, repeat, notes, curr ->
                        val newBill = BillReminderEntity(
                            id = System.currentTimeMillis(),
                            title = title,
                            amount = amt,
                            categoryId = catId,
                            dueDateMillis = due,
                            repeatIntervalMonths = repeat,
                            notes = notes,
                            currencyCode = curr,
                            isPaid = false
                        )
                        repository.addBill(newBill)
                    },
                    onPayBill = { billId, accountId ->
                        repository.payBill(billId, accountId)
                    },
                    onDeleteBill = { billId ->
                        repository.deleteBill(billId)
                    },
                    onToggleNotifications = { enabled ->
                        resolvedPrefsManager.setBillNotificationsEnabled(enabled)
                    },
                    onSendTestReminder = {
                        val posted = BillNotificationHelper.checkAndNotifyUpcomingBills(
                            context = context.applicationContext,
                            bills = bills,
                            activeCurrency = activeCurrency,
                            reminderDaysBefore = preferences.billReminderDaysBefore,
                            forceNotify = true
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (posted > 0) "Mengirim $posted pengingat tagihan jatuh tempo"
                                else if (!BillNotificationHelper.hasNotificationPermission(context)) "Izin notifikasi belum diaktifkan"
                                else "Tidak ada tagihan yang mendekati jatuh tempo (H-${preferences.billReminderDaysBefore})"
                            )
                        }
                    },
                    onBack = { currentScreen = 0 },
                    modifier = screenModifier
                )
            }
        }
    }

    if (showAddTransactionSheet) {
        AddEditTransactionSheet(
            categories = categories,
            accounts = accounts,
            activeCurrency = activeCurrency,
            existingTransaction = selectedTransactionForEdit,
            initialDateMillis = initialDateForNewTransaction,
            onDismiss = {
                showAddTransactionSheet = false
                selectedTransactionForEdit = null
            },
            onSave = { title, amount, type, categoryId, dateMillis, notes, currency, accountId, receiptUri, toAccountId ->
                if (selectedTransactionForEdit == null) {
                    repository.addTransaction(
                        TransactionEntity(
                            id = System.currentTimeMillis(),
                            title = title,
                            amount = amount,
                            type = type,
                            categoryId = categoryId,
                            dateMillis = dateMillis,
                            notes = notes,
                            currencyCode = currency,
                            accountId = accountId,
                            receiptUri = receiptUri,
                            toAccountId = toAccountId
                        )
                    )
                } else {
                    repository.updateTransaction(
                        selectedTransactionForEdit!!.copy(
                            title = title,
                            amount = amount,
                            type = type,
                            categoryId = categoryId,
                            dateMillis = dateMillis,
                            notes = notes,
                            currencyCode = currency,
                            accountId = accountId,
                            receiptUri = receiptUri,
                            toAccountId = toAccountId
                        )
                    )
                }
                showAddTransactionSheet = false
                selectedTransactionForEdit = null
            },
            onDelete = { tx ->
                repository.deleteTransaction(tx)
                showAddTransactionSheet = false
                selectedTransactionForEdit = null
            }
        )
    }

    if (showExportDialog) {
        ExportReportDialog(
            allTransactions = transactions,
            activeCurrency = activeCurrency,
            onDismiss = { showExportDialog = false },
            onExport = { format, saveToDevice, periodLabel, filteredTransactions ->
                val generatedFile = when (format) {
                    ExportFormat.PDF -> ExportHelper.generatePdfReport(
                        context = context,
                        periodName = periodLabel,
                        transactions = filteredTransactions,
                        categories = categories,
                        currencyCode = activeCurrency,
                        accounts = accounts
                    )
                    ExportFormat.EXCEL -> ExportHelper.generateExcelReport(
                        context = context,
                        periodName = periodLabel,
                        transactions = filteredTransactions,
                        categories = categories,
                        currencyCode = activeCurrency,
                        accounts = accounts
                    )
                    ExportFormat.CSV -> ExportHelper.generateCsvReport(
                        context = context,
                        periodName = periodLabel,
                        transactions = filteredTransactions,
                        categories = categories,
                        currencyCode = activeCurrency,
                        accounts = accounts
                    )
                }

                if (generatedFile != null) {
                    if (saveToDevice) {
                        pendingSaveFile = generatedFile
                        saveDocumentLauncher.launch(
                            ExportHelper.suggestedFileName(format, periodLabel)
                        )
                    } else {
                        ExportHelper.shareFile(
                            context = context,
                            file = generatedFile,
                            mimeType = format.mimeType,
                            title = "Ekspor Laporan ${format.shortLabel} DompetKu - $periodLabel"
                        )
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Gagal membuat file laporan ${format.shortLabel}")
                    }
                }
            }
        )
    }
}
