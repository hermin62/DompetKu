package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String
)

object CurrencyHelper {

    val supportedCurrencies = listOf(
        CurrencyInfo("IDR", "Rupiah Indonesia", "Rp"),
        CurrencyInfo("USD", "US Dollar", "$"),
        CurrencyInfo("EUR", "Euro", "€"),
        CurrencyInfo("SGD", "Singapore Dollar", "S$"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM"),
        CurrencyInfo("JPY", "Japanese Yen", "¥")
    )

    private val idLocale = Locale("id", "ID")

    private val wholeNumberFormatter = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat {
            val symbols = DecimalFormatSymbols(idLocale).apply {
                groupingSeparator = '.'
                decimalSeparator = ','
            }
            return DecimalFormat("#,##0", symbols)
        }
    }

    private val decimalNumberFormatter = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat {
            val symbols = DecimalFormatSymbols(idLocale).apply {
                groupingSeparator = '.'
                decimalSeparator = ','
            }
            return DecimalFormat("#,##0.##", symbols)
        }
    }

    private val thousandsFormatter = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat {
            val symbols = DecimalFormatSymbols(idLocale).apply {
                groupingSeparator = '.'
            }
            return DecimalFormat("#,###", symbols)
        }
    }

    fun getSymbol(currencyCode: String): String {
        return when (currencyCode.uppercase()) {
            "IDR" -> "Rp"
            "USD" -> "$"
            "EUR" -> "€"
            "SGD" -> "S$"
            "MYR" -> "RM"
            "JPY" -> "¥"
            else -> "Rp"
        }
    }

    /**
     * Format a Double value for display, e.g. Rp 50.000 or Rp 1.500.000
     */
    fun format(amount: Double, currencyCode: String = "IDR"): String {
        val symbol = getSymbol(currencyCode)
        val safeAmount = if (amount.isFinite()) amount else 0.0
        val isWholeNumber = safeAmount % 1.0 == 0.0
        val formatter = if (isWholeNumber) wholeNumberFormatter.get()!! else decimalNumberFormatter.get()!!
        return "$symbol ${formatter.format(safeAmount)}"
    }

    /**
     * Format raw digit string with Indonesian thousands dots:
     * e.g. "50000" -> "50.000", "1000000" -> "1.000.000"
     */
    fun formatThousands(rawDigits: String): String {
        val clean = rawDigits.filter { it.isDigit() }
        if (clean.isEmpty()) return ""
        val num = clean.toLongOrNull() ?: return clean
        return thousandsFormatter.get()!!.format(num)
    }

    /**
     * Format a Double amount to thousands string for text fields,
     * e.g. 50000.0 -> "50.000"
     */
    fun formatAmountToThousands(amount: Double): String {
        if (amount <= 0.0 || !amount.isFinite()) return ""
        return formatThousands(amount.toLong().toString())
    }

    /**
     * Parse formatted text with dots (e.g. "50.000" or "1.500.000") into a Double.
     */
    fun parseThousands(formattedText: String): Double {
        val clean = formattedText.filter { it.isDigit() }
        return clean.toDoubleOrNull() ?: 0.0
    }
}
