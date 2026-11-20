package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.BillReminderEntity

data class DueBillAlert(
    val bill: BillReminderEntity,
    val daysUntilDue: Int
)

object BillNotificationHelper {
    const val CHANNEL_ID = "dompetku_bill_reminders"
    private const val PREFS_NAME = "dompetku_bill_notifications"
    private const val JOB_ID = 4109
    private const val ONE_DAY_MS = 86_400_000L

    fun calculateDaysUntilDue(
        dueDateMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val todayStart = DateUtils.getStartOfDay(nowMillis)
        val dueStart = DateUtils.getStartOfDay(dueDateMillis)
        return ((dueStart - todayStart) / ONE_DAY_MS).toInt()
    }

    fun findDueSoonBills(
        bills: List<BillReminderEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        reminderDaysBefore: Int = 3
    ): List<DueBillAlert> {
        return bills
            .filter { !it.isPaid }
            .map { bill ->
                DueBillAlert(
                    bill = bill,
                    daysUntilDue = calculateDaysUntilDue(bill.dueDateMillis, nowMillis)
                )
            }
            .filter { it.daysUntilDue <= reminderDaysBefore }
            .sortedBy { it.daysUntilDue }
    }

    fun formatDueBadgeLabel(daysUntilDue: Int): String {
        return when {
            daysUntilDue < 0 -> "Terlewat ${-daysUntilDue} hari"
            daysUntilDue == 0 -> "Jatuh tempo hari ini"
            daysUntilDue == 1 -> "Jatuh tempo besok"
            else -> "Jatuh tempo H-$daysUntilDue"
        }
    }

    fun formatRepeatLabel(repeatIntervalMonths: Int): String {
        return when (repeatIntervalMonths) {
            0 -> "Sekali bayar"
            1 -> "Setiap bulan"
            3 -> "Setiap 3 bulan"
            6 -> "Setiap 6 bulan"
            12 -> "Setiap tahun"
            else -> "Tiap $repeatIntervalMonths bulan"
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Pengingat Tagihan Berkala",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi saat tagihan rutin mendekati atau melewati tanggal jatuh tempo"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkAndNotifyUpcomingBills(
        context: Context,
        bills: List<BillReminderEntity>,
        activeCurrency: String,
        reminderDaysBefore: Int = 3,
        forceNotify: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        if (!hasNotificationPermission(context)) return 0
        ensureNotificationChannel(context)

        val dueAlerts = findDueSoonBills(
            bills = bills,
            nowMillis = nowMillis,
            reminderDaysBefore = reminderDaysBefore
        )
        if (dueAlerts.isEmpty()) return 0

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStart = DateUtils.getStartOfDay(nowMillis)
        val pendingAlerts = if (forceNotify) {
            dueAlerts
        } else {
            dueAlerts.filter { alert ->
                val lastNotifiedDay = prefs.getLong("notified_day_${alert.bill.id}", -1L)
                lastNotifiedDay != todayStart
            }
        }
        if (pendingAlerts.isEmpty()) return 0

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "bills")
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            4100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = NotificationManagerCompat.from(context)
        val editor = prefs.edit()
        var postedCount = 0

        pendingAlerts.forEach { alert ->
            val bill = alert.bill
            val dueStatus = formatDueBadgeLabel(alert.daysUntilDue)
            val amountStr = CurrencyHelper.format(bill.amount, bill.currencyCode.ifBlank { activeCurrency })
            val title = if (alert.daysUntilDue <= 0) {
                "Tagihan Segera Dibayar: ${bill.title}"
            } else {
                "Pengingat Tagihan: ${bill.title}"
            }
            val body = "$dueStatus (${DateUtils.formatDate(bill.dueDateMillis)}) • Nominal $amountStr."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            val notificationId = 7000 + (bill.id % 10000L).toInt()
            runCatching {
                manager.notify(notificationId, notification)
                editor.putLong("notified_day_${bill.id}", todayStart)
                postedCount++
            }
        }
        editor.apply()
        return postedCount
    }

    fun schedulePeriodicCheck(context: Context) {
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return
        val alreadyScheduled = jobScheduler.allPendingJobs.any { it.id == JOB_ID }
        if (alreadyScheduled) return

        val componentName = ComponentName(context, BillReminderJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID, componentName)
            .setPeriodic(12 * 60 * 60 * 1000L) // Every 12 hours
            .setPersisted(false)
            .build()
        runCatching { jobScheduler.schedule(jobInfo) }
    }
}
