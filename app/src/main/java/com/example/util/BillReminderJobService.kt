package com.example.util

import android.app.job.JobParameters
import android.app.job.JobService
import com.example.data.LocalBackupStorage
import com.example.data.PreferencesManager

class BillReminderJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val prefsManager = PreferencesManager(applicationContext)
        val prefs = prefsManager.userPreferences.value
        val accountKey = prefs.activeAccountKey

        if (accountKey.isNotBlank() && prefs.billNotificationsEnabled) {
            val snapshot = LocalBackupStorage(applicationContext).loadSnapshot(accountKey)
            if (snapshot != null && snapshot.bills.isNotEmpty()) {
                BillNotificationHelper.checkAndNotifyUpcomingBills(
                    context = applicationContext,
                    bills = snapshot.bills,
                    activeCurrency = snapshot.currencyCode.ifBlank { prefs.currencyCode },
                    reminderDaysBefore = prefs.billReminderDaysBefore
                )
            }
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}
