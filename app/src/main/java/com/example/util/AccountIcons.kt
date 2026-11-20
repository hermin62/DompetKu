package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.AccountNature
import com.example.data.AccountType

object AccountIcons {
    val iconList = listOf(
        "payments" to Icons.Default.Payments,
        "account_balance" to Icons.Default.AccountBalance,
        "account_balance_wallet" to Icons.Default.AccountBalanceWallet,
        "phone_android" to Icons.Default.PhoneAndroid,
        "credit_card" to Icons.Default.CreditCard,
        "savings" to Icons.Default.Savings,
        "wallet" to Icons.Default.Wallet,
        "storefront" to Icons.Default.Storefront
    )

    fun getIcon(key: String): ImageVector {
        return iconList.find { it.first == key }?.second ?: Icons.Default.AccountBalanceWallet
    }

    fun getTypeName(type: AccountType): String {
        return when (type) {
            AccountType.CASH -> "Tunai"
            AccountType.BANK -> "Bank"
            AccountType.EWALLET -> "E-Wallet"
            AccountType.SAVINGS -> "Tabungan"
            AccountType.CREDIT_CARD -> "Kartu Kredit"
            AccountType.PAYLATER -> "Paylater"
            AccountType.OTHER -> "Lainnya"
        }
    }

    fun getNatureName(nature: AccountNature): String {
        return when (nature) {
            AccountNature.DEBIT -> "Saldo Debit"
            AccountNature.CREDIT -> "Saldo Kredit"
        }
    }

    val defaultColors = listOf(
        "#10B981", // Emerald
        "#0D9488", // Teal
        "#0284C7", // Sky Blue
        "#3B82F6", // Blue
        "#8B5CF6", // Purple
        "#EC4899", // Pink
        "#F59E0B", // Amber
        "#EF4444"  // Red
    )
}
