package com.spendwise.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.spendwise.app.navigation.Screen

@Composable
fun SpendWiseBottomBar(navController: NavController) {
    val items = listOf(
        Screen.Dashboard,
        Screen.Categories,
        Screen.Merchants,
        Screen.Calendar,
        Screen.Transactions
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.filter {    it.icon!=null }.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            // avoid building huge back stack
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = screen.icon!!,
                        contentDescription = screen.label
                    )
                },
                label = { Text(screen.label) }
            )
        }
    }
}
