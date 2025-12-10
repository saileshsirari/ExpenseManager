package com.spendwise.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spendwise.app.navigation.Screen
import com.spendwise.feature.smsimport.ui.SmsImportViewModel

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SpendWiseApp() {

    val context = LocalContext.current
    val navController = rememberNavController()

    // Correct global-scoped ViewModel (activity scope)
    val viewModel: SmsImportViewModel = hiltViewModel()

    var hasRequested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Trigger ViewModel import exactly once
            viewModel.importAll { context.contentResolver }
        }
        hasRequested = true
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    // Run import exactly once per app launch
    LaunchedEffect(Unit) {
        if (hasPermission) {
            viewModel.importAll { context.contentResolver }
        } else {
            launcher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        bottomBar = { SpendWiseBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {

            composable(Screen.Dashboard.route) {
                DashboardScreen(navController, viewModel)
            }

            composable(Screen.Categories.route) {
                CategoriesScreen(viewModel)
            }

            composable(Screen.Merchants.route) {
                MerchantsScreen(viewModel)
            }

            composable(Screen.Calendar.route) {
                CalendarScreen(viewModel)
            }

            composable(Screen.Transactions.route) {
                AllTransactionsScreen(viewModel)
            }

            composable(Screen.AddExpense.route) {
                AddExpenseScreen(
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}

