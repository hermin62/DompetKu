package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserPreferences(
    val currencyCode: String = "IDR",
    val isBiometricEnabled: Boolean = false,
    val isTwoFactorEnabled: Boolean = false,
    val pinCode: String = "",
    val twoFactorCode: String = "284915",
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val cloudSyncEnabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val hideBalance: Boolean = false,
    val overallMonthlyBudget: Double = 0.0,
    val isLoggedIn: Boolean = false,
    val isOfflineMode: Boolean = false,
    val userEmail: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val spreadsheetId: String = "",
    val spreadsheetUrl: String = "",
    val lastSyncStatus: String = "",
    val billNotificationsEnabled: Boolean = true,
    val billReminderDaysBefore: Int = 3
) {
    val activeAccountKey: String
        get() = when {
            isLoggedIn && userEmail.isNotBlank() -> userEmail.trim().lowercase()
            isOfflineMode -> "local_offline"
            else -> ""
        }
}

class PreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    private val sessionPrefs: SharedPreferences =
        appContext.getSharedPreferences("dompetku_prefs", Context.MODE_PRIVATE)

    private fun sanitizeKey(accountKey: String): String {
        return accountKey
            .trim()
            .lowercase()
            .ifBlank { "signed_out" }
            .replace(Regex("[^a-z0-9._-]"), "_")
    }

    private fun accountPrefsFor(accountKey: String): SharedPreferences {
        return appContext.getSharedPreferences(
            "dompetku_account_${sanitizeKey(accountKey)}",
            Context.MODE_PRIVATE
        )
    }

    private fun currentAccountKey(): String {
        val isLoggedIn = sessionPrefs.getBoolean("is_logged_in", false)
        val isOffline = sessionPrefs.getBoolean("is_offline_mode", false)
        val email = sessionPrefs.getString("user_email", "")?.trim()?.lowercase().orEmpty()
        return when {
            isLoggedIn && email.isNotBlank() -> email
            isOffline -> "local_offline"
            else -> ""
        }
    }

    private val _userPreferences = MutableStateFlow(loadPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        val isLoggedIn = sessionPrefs.getBoolean("is_logged_in", false)
        val isOfflineMode = sessionPrefs.getBoolean("is_offline_mode", false)
        val userEmail = sessionPrefs.getString("user_email", "") ?: ""
        val userName = sessionPrefs.getString("user_name", "") ?: ""
        val userPhotoUrl = sessionPrefs.getString("user_photo_url", "") ?: ""

        val key = when {
            isLoggedIn && userEmail.isNotBlank() -> userEmail.trim().lowercase()
            isOfflineMode -> "local_offline"
            else -> ""
        }

        if (key.isBlank()) {
            return UserPreferences(
                isLoggedIn = false,
                isOfflineMode = false,
                themeMode = sessionPrefs.getString("last_theme_mode", "SYSTEM") ?: "SYSTEM"
            )
        }

        val accPrefs = accountPrefsFor(key)
        return UserPreferences(
            currencyCode = accPrefs.getString("currency_code", "IDR") ?: "IDR",
            isBiometricEnabled = accPrefs.getBoolean("biometric_enabled", false),
            isTwoFactorEnabled = accPrefs.getBoolean("two_factor_enabled", false),
            pinCode = accPrefs.getString("pin_code", "") ?: "",
            twoFactorCode = accPrefs.getString("two_factor_code", "284915") ?: "284915",
            themeMode = accPrefs.getString(
                "theme_mode",
                sessionPrefs.getString("last_theme_mode", "SYSTEM") ?: "SYSTEM"
            ) ?: "SYSTEM",
            cloudSyncEnabled = accPrefs.getBoolean("cloud_sync_enabled", true),
            lastSyncTime = accPrefs.getLong("last_sync_time", 0L),
            hideBalance = accPrefs.getBoolean("hide_balance", false),
            overallMonthlyBudget = java.lang.Double.longBitsToDouble(
                accPrefs.getLong("overall_monthly_budget", java.lang.Double.doubleToLongBits(0.0))
            ),
            isLoggedIn = isLoggedIn,
            isOfflineMode = isOfflineMode,
            userEmail = userEmail,
            userName = userName,
            userPhotoUrl = userPhotoUrl,
            spreadsheetId = accPrefs.getString("spreadsheet_id", "") ?: "",
            spreadsheetUrl = accPrefs.getString("spreadsheet_url", "") ?: "",
            lastSyncStatus = accPrefs.getString("last_sync_status", "") ?: "",
            billNotificationsEnabled = accPrefs.getBoolean("bill_notifications_enabled", true),
            billReminderDaysBefore = accPrefs.getInt("bill_reminder_days_before", 3)
        )
    }

    private fun activeAccountPrefs(): SharedPreferences {
        return accountPrefsFor(currentAccountKey())
    }

    fun setCurrency(currencyCode: String) {
        activeAccountPrefs().edit().putString("currency_code", currencyCode).apply()
        _userPreferences.value = _userPreferences.value.copy(currencyCode = currencyCode)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        activeAccountPrefs().edit().putBoolean("biometric_enabled", enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(isBiometricEnabled = enabled)
    }

    fun setTwoFactorEnabled(enabled: Boolean) {
        activeAccountPrefs().edit().putBoolean("two_factor_enabled", enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(isTwoFactorEnabled = enabled)
    }

    fun setPinCode(pin: String) {
        activeAccountPrefs().edit().putString("pin_code", pin).apply()
        _userPreferences.value = _userPreferences.value.copy(pinCode = pin)
    }

    fun setThemeMode(mode: String) {
        sessionPrefs.edit().putString("last_theme_mode", mode).apply()
        if (currentAccountKey().isNotBlank()) {
            activeAccountPrefs().edit().putString("theme_mode", mode).apply()
        }
        _userPreferences.value = _userPreferences.value.copy(themeMode = mode)
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        activeAccountPrefs().edit().putBoolean("cloud_sync_enabled", enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(cloudSyncEnabled = enabled)
    }

    fun updateLastSyncTime(status: String = "Tersinkron ke Google Spreadsheet") {
        val now = System.currentTimeMillis()
        activeAccountPrefs().edit()
            .putLong("last_sync_time", now)
            .putString("last_sync_status", status)
            .apply()
        _userPreferences.value = _userPreferences.value.copy(
            lastSyncTime = now,
            lastSyncStatus = status
        )
    }

    fun setLastSyncStatus(status: String) {
        activeAccountPrefs().edit().putString("last_sync_status", status).apply()
        _userPreferences.value = _userPreferences.value.copy(lastSyncStatus = status)
    }

    fun setHideBalance(hide: Boolean) {
        activeAccountPrefs().edit().putBoolean("hide_balance", hide).apply()
        _userPreferences.value = _userPreferences.value.copy(hideBalance = hide)
    }

    fun setOverallMonthlyBudget(budget: Double) {
        activeAccountPrefs().edit()
            .putLong("overall_monthly_budget", java.lang.Double.doubleToLongBits(budget))
            .apply()
        _userPreferences.value = _userPreferences.value.copy(overallMonthlyBudget = budget)
    }

    fun setBillNotificationsEnabled(enabled: Boolean) {
        activeAccountPrefs().edit().putBoolean("bill_notifications_enabled", enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(billNotificationsEnabled = enabled)
    }

    fun setBillReminderDaysBefore(days: Int) {
        val clamped = days.coerceIn(1, 14)
        activeAccountPrefs().edit().putInt("bill_reminder_days_before", clamped).apply()
        _userPreferences.value = _userPreferences.value.copy(billReminderDaysBefore = clamped)
    }

    fun saveGoogleSession(email: String, name: String, photoUrl: String) {
        val normalizedEmail = email.trim().lowercase()
        sessionPrefs.edit()
            .putBoolean("is_logged_in", true)
            .putBoolean("is_offline_mode", false)
            .putString("user_email", normalizedEmail)
            .putString("user_name", name)
            .putString("user_photo_url", photoUrl)
            .commit()
        _userPreferences.value = loadPreferences()
    }

    fun setOfflineMode(enabled: Boolean) {
        sessionPrefs.edit()
            .putBoolean("is_offline_mode", enabled)
            .putBoolean("is_logged_in", false)
            .remove("user_email")
            .remove("user_name")
            .remove("user_photo_url")
            .commit()
        _userPreferences.value = loadPreferences()
    }

    fun setSpreadsheetInfo(id: String, url: String) {
        activeAccountPrefs().edit()
            .putString("spreadsheet_id", id)
            .putString("spreadsheet_url", url)
            .apply()
        _userPreferences.value = _userPreferences.value.copy(
            spreadsheetId = id,
            spreadsheetUrl = url
        )
    }

    fun clearAuthSession() {
        sessionPrefs.edit()
            .putBoolean("is_logged_in", false)
            .putBoolean("is_offline_mode", false)
            .remove("user_email")
            .remove("user_name")
            .remove("user_photo_url")
            .commit()
        _userPreferences.value = loadPreferences()
    }
}
