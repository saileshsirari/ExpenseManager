
package com.spendwise.app.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.spendwise.app.ui.OnboardingScreen
import com.spendwise.app.ui.PermissionScreen
import com.spendwise.app.ui.dashboard.MonthlyDashboardScreen

object Routes { const val ONBOARD = "onboard"; const val PERMISSION = "permission"; const val DASHBOARD = "dashboard" }

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.ONBOARD) {
        composable(Routes.ONBOARD) { OnboardingScreen(onContinue = { navController.navigate(Routes.PERMISSION) }) }
        composable(Routes.PERMISSION) { PermissionScreen(onPermissionGranted = { navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARD) { inclusive = true } } }) }
        composable(Routes.DASHBOARD) { MonthlyDashboardScreen() }
    }
}
