package com.example.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.data.BillReminderEntity

object CalendarHelper {

    fun addBillToCalendar(context: Context, bill: BillReminderEntity) {
        try {
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, bill.dueDateMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, bill.dueDateMillis + 3600000L) // 1 hour
                .putExtra(CalendarContract.Events.TITLE, "Bayar Tagihan: ${bill.title}")
                .putExtra(CalendarContract.Events.DESCRIPTION, "Nominal: ${CurrencyHelper.format(bill.amount, bill.currencyCode)}\nCatatan: ${bill.notes}\nPengingat otomatis dari aplikasi DanaLoka.")
                .putExtra(CalendarContract.Events.EVENT_LOCATION, "DanaLoka - Catatan Keuangan & Anggaran")
                .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
