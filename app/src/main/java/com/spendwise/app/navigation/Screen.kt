package com.spendwise.app.navigation



sealed class Screen(val route: String) {

    // NEW
    object Permission : Screen("permission")
    object DashboardNew : Screen("dashboard_new")     // Redesigned dashboard
    object Insights : Screen("insights")              // Insights screen

    // Existing screens
    object Categories : Screen("categories")
    object Merchants : Screen("merchants")
    object Calendar : Screen("calendar")
    object Transactions : Screen("transactions")
    object AddExpense : Screen("add_expense")
}

