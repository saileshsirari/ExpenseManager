package com.spendwise.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spendwise.app.navigation.Screen
import com.spendwise.feature.smsimport.ui.SmsImportViewModel

@Composable
fun SpendWiseApp() {

    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: SmsImportViewModel = hiltViewModel()

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startImportIfNeeded { context.contentResolver }
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Permission.route) { inclusive = true }
            }
        }
    }

    // ALWAYS render NavHost
    Scaffold(
        bottomBar = {
            if (hasPermission) {
                SpendWiseBottomBar(navController)
            }
        }
    ) { padding ->

        NavHost(
            navController = navController,
            startDestination = if (hasPermission) Screen.Dashboard.route else Screen.Permission.route,
            modifier = Modifier.padding(padding)
        ) {

            composable(Screen.Permission.route) {
                PermissionScreen(
                    onPermissionRequest = {
                        launcher.launch(Manifest.permission.READ_SMS)
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(navController, viewModel)
            }

            composable(Screen.Categories.route) { CategoriesScreen(viewModel) }
            composable(Screen.Merchants.route) { MerchantsScreen(viewModel) }
            composable(Screen.Calendar.route) { CalendarScreen(viewModel) }
            composable(Screen.Transactions.route) { AllTransactionsScreen(viewModel) }
            composable(Screen.AddExpense.route) {
                AddExpenseScreen(onDone = { navController.popBackStack() })
            }
        }
    }

    // If permission already granted, start import automatically once
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.startImportIfNeeded { context.contentResolver }
        }
    }
}





