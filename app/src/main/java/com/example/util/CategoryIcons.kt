package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIcons {
    private val iconMap = mapOf(
        "food" to Icons.Default.Restaurant,
        "restaurant" to Icons.Default.Fastfood,
        "transport" to Icons.Default.DirectionsBus,
        "commute" to Icons.Default.DirectionsCar,
        "shopping" to Icons.Default.ShoppingCart,
        "groceries" to Icons.Default.LocalGroceryStore,
        "housing" to Icons.Default.Home,
        "bills" to Icons.Default.Receipt,
        "health" to Icons.Default.LocalHospital,
        "entertainment" to Icons.Default.Movie,
        "education" to Icons.Default.School,
        "personal" to Icons.Default.Person,
        "family" to Icons.Default.People,
        "travel" to Icons.Default.Flight,
        "hotel" to Icons.Default.Hotel,
        "investment" to Icons.Default.TrendingUp,
        "savings" to Icons.Default.Savings,
        "salary" to Icons.Default.Payments,
        "business" to Icons.Default.Work,
        "bonus" to Icons.Default.CardGiftcard,
        "other" to Icons.Default.Category,
        "category" to Icons.Default.Category,
        "wallet" to Icons.Default.AccountBalanceWallet,
        "bank" to Icons.Default.AccountBalance
    )

    fun getIcon(key: String): ImageVector {
        val cleanKey = key.lowercase().trim()
        return iconMap[cleanKey] ?: Icons.Default.Category
    }

    val availableIcons = iconMap.keys.toList()
}
