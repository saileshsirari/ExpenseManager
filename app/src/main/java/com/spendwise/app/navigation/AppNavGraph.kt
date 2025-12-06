
package com.spendwise.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.spendwise.app.ui.OnboardingScreen
import com.spendwise.app.ui.PermissionScreen
import com.spendwise.app.ui.DashboardScreen

object Routes { const val ONBOARD = "onboard"; const val PERMISSION = "permission"; const val DASHBOARD = "dashboard" }

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.ONBOARD) {
        composable(Routes.ONBOARD) { OnboardingScreen(onContinue = { navController.navigate(Routes.PERMISSION) }) }
        composable(Routes.PERMISSION) { PermissionScreen(onPermissionGranted = { navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARD) { inclusive = true } } }) }
        composable(Routes.DASHBOARD) { DashboardScreen() }
    }
}
