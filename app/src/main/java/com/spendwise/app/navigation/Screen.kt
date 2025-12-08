package com.spendwise.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Store

sealed class Screen(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home)
    data object Categories : Screen("categories", "Categories", Icons.Filled.PieChart)
    data object Merchants : Screen("merchants", "Merchants", Icons.Filled.Store)
    data object Calendar : Screen("calendar", "Calendar", Icons.Filled.CalendarToday)
    data object Transactions : Screen("transactions", "Transactions", Icons.Filled.Checklist)
}