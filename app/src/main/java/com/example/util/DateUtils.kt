package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val localeId = Locale("id", "ID")

    private val dateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd MMM yyyy", localeId)
    }
    private val shortDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd MMM", localeId)
    }
    private val fullDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", localeId)
    }
    private val timeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm", localeId)
    }
    private val monthYearFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("MMMM yyyy", localeId)
    }

    private val calA = object : ThreadLocal<Calendar>() {
        override fun initialValue(): Calendar = Calendar.getInstance()
    }
    private val calB = object : ThreadLocal<Calendar>() {
        override fun initialValue(): Calendar = Calendar.getInstance()
    }
    private val dateScratch = object : ThreadLocal<Date>() {
        override fun initialValue(): Date = Date()
    }

    fun formatDate(millis: Long): String {
        val d = dateScratch.get()!!
        d.time = millis
        return dateFormatter.get()!!.format(d)
    }

    fun formatShortDate(millis: Long): String {
        val d = dateScratch.get()!!
        d.time = millis
        return shortDateFormatter.get()!!.format(d)
    }

    fun formatFullDate(millis: Long): String {
        val d = dateScratch.get()!!
        d.time = millis
        return fullDateFormatter.get()!!.format(d)
    }

    fun formatTime(millis: Long): String {
        val d = dateScratch.get()!!
        d.time = millis
        return timeFormatter.get()!!.format(d)
    }

    fun formatMonthYear(millis: Long): String {
        val d = dateScratch.get()!!
        d.time = millis
        return monthYearFormatter.get()!!.format(d)
    }

    fun isSameDay(m1: Long, m2: Long): Boolean {
        val c1 = calA.get()!!.apply { timeInMillis = m1 }
        val c2 = calB.get()!!.apply { timeInMillis = m2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun isCurrentMonth(millis: Long): Boolean {
        val now = calA.get()!!.apply { timeInMillis = System.currentTimeMillis() }
        val c = calB.get()!!.apply { timeInMillis = millis }
        return now.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == c.get(Calendar.MONTH)
    }

    fun isLastMonth(millis: Long): Boolean {
        val lastMonth = calA.get()!!.apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.MONTH, -1)
        }
        val c = calB.get()!!.apply { timeInMillis = millis }
        return lastMonth.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            lastMonth.get(Calendar.MONTH) == c.get(Calendar.MONTH)
    }

    fun isCurrentYear(millis: Long): Boolean {
        val now = calA.get()!!.apply { timeInMillis = System.currentTimeMillis() }
        val c = calB.get()!!.apply { timeInMillis = millis }
        return now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
    }

    fun getStartOfDay(millis: Long): Long {
        return calA.get()!!.apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getEndOfDay(millis: Long): Long {
        return calA.get()!!.apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun getLastMonthMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }.timeInMillis
    }
}
