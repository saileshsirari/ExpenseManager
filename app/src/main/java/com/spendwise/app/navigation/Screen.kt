package com.spendwise.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector? = null   // icon optional for non-tab screens
) {

    // ---- BOTTOM BAR SCREENS ----
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home)
    data object Categories : Screen("categories", "Categories", Icons.Filled.PieChart)
    data object Merchants : Screen("merchants", "Merchants", Icons.Filled.Store)
    data object Calendar : Screen("calendar", "Calendar", Icons.Filled.CalendarToday)
    data object Transactions : Screen("transactions", "Transactions", Icons.Filled.Checklist)

    // ---- FULL-SCREEN NAVIGATION (NO TAB ICONS) ----
    data object AddExpense : Screen("add_expense", "Add Expense")
}
