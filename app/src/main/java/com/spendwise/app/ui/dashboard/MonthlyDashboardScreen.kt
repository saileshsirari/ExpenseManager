package com.spendwise.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.spendwise.core.Logger as Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.ui.SmsListItem
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MonthlyDashboardScreen(
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val selectedExplanation by viewModel.selectedExplanation.collectAsState()
    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }

    if (showFixDialog != null) {
        FixMerchantDialog(
            tx = showFixDialog!!,
            onConfirm = { newName ->
                viewModel.fixMerchant(showFixDialog!!, newName)
                showFixDialog = null
            },
            onDismiss = { showFixDialog = null }
        )
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.importAll {
                context.contentResolver
            }
        }
    }
    val allTransactions by viewModel.items.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    Log.d("expense", "YearMonth.now() = $month")

    LaunchedEffect(allTransactions) {
        Log.d("expense", "Items in dashboard = ${allTransactions.size}")
    }
    val summary = viewModel.getMonthlySummary(allTransactions, month)
    LaunchedEffect(selectedCategory, selectedDay, month, allTransactions) {
        Log.d("expense", "Selected category = $selectedCategory, day = $selectedDay")

        val countMonth = allTransactions.count {
            YearMonth.from(
                Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            ) == month
        }
        Log.d("expense", "Transactions in month = $countMonth")
    }

    val filtered = when {
        // 1️⃣ Day filter takes highest priority (user taps day second)
        selectedDay != null -> {
            allTransactions.filter { tx ->
                val date = Instant.ofEpochMilli(tx.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                date.dayOfMonth == selectedDay &&
                        YearMonth.from(date) == month
            }
        }

        // 2️⃣ Category filter is secondary
        selectedCategory != null -> {
            allTransactions.filter { tx ->

                val date = Instant.ofEpochMilli(tx.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                tx.type.equals(selectedCategory, ignoreCase = true)
                        &&
                        YearMonth.from(date) == month
            }
        }

        // 3️⃣ No filter → show entire month
        else -> {
            allTransactions.filter { tx ->
                YearMonth.from(
                    Instant.ofEpochMilli(tx.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                ) == month
            }
        }
    }



    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // Month Selector
        item {
            MonthSelector(
                month = month,
                onMonthChange = {
                    month = it
                    selectedCategory = null
                    selectedDay = null
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        // Total Spending
        item {
            Text(
                "Total Spending: ₹${summary.total}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))
        }

        // Pie Chart Section
        item {
            Text("Category Breakdown", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            CategoryPieChart(
                data = summary.categoryTotals,
                onSliceClick = {
                    selectedCategory = it
                    selectedDay = null
                }
            )

            Spacer(Modifier.height(20.dp))
        }

        // Bar Chart Section
        item {
            Text("Daily Spending", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            DailyBarChart(
                data = summary.dailyTotals,
                onBarClick = {
                    selectedDay = it
                    selectedCategory = null  // clear category
                }

            )

            Spacer(Modifier.height(20.dp))
        }

        // Filtered results header
        if (filtered.isNotEmpty()) {
            item {
                Text(
                    text = "Filtered Results (${filtered.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // Scrolling list items
        items(filtered) { tx ->

            SmsListItem(tx, onClick = { clicked ->
                viewModel.onMessageClicked(clicked)
            }, onRequestMerchantFix = {
                showFixDialog = it

            }, onMarkNotExpense = { sms, isChecked ->
                viewModel.setIgnoredState(sms, isChecked)
            })

        }
    }
}

@Composable
fun FixMerchantDialog(
    tx: SmsEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(tx.merchant ?: tx.sender) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix Merchant") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Merchant Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


