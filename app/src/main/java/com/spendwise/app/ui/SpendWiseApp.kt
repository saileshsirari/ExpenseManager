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
    val viewModel: SmsImportViewModel = hiltViewModel()  // <-- activity scoped

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.importAll { context.contentResolver }
        }
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    // Import SMS ONCE when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.importAll { context.contentResolver }
        } else {
            launcher.launch(Manifest.permission.READ_SMS)
        }
    }

    // ----- UI NAVIGATION -----
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { SpendWiseBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController,viewModel) }
            composable(Screen.Categories.route) { CategoriesScreen(viewModel) }
            composable(Screen.Merchants.route) { MerchantsScreen(viewModel) }
            composable(Screen.Calendar.route) { CalendarScreen(viewModel) }
            composable(Screen.Transactions.route) { AllTransactionsScreen(viewModel) }
            composable(Screen.AddExpense.route) {
                AddExpenseScreen(
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}
