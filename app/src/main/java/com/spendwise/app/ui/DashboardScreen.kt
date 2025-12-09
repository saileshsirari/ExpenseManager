package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.spendwise.app.navigation.Screen
import com.spendwise.app.ui.dashboard.CategoryPieChart
import com.spendwise.app.ui.dashboard.DailyBarChart
import com.spendwise.app.ui.dashboard.FixMerchantDialog
import com.spendwise.app.ui.dashboard.MonthSelector
import com.spendwise.core.extensions.active
import com.spendwise.core.extensions.inMonth
import com.spendwise.core.extensions.ofType
import com.spendwise.core.extensions.onDay
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.items.collectAsState()

    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // Fix Merchant Dialog
    showFixDialog?.let { tx ->
        FixMerchantDialog(
            tx = tx,
            onConfirm = { newName ->
                viewModel.fixMerchant(tx, newName)
                showFixDialog = null
            },
            onDismiss = { showFixDialog = null }
        )
    }

    // FILTERING LOGIC
    val monthTx = allTransactions
        .active()
        .inMonth(month)

    val totalDebit = monthTx.filter { it.type.equals("DEBIT", true) }.sumOf { it.amount }
    val totalCredit = monthTx.filter { it.type.equals("CREDIT", true) }.sumOf { it.amount }

    val debitCreditTotals = mapOf(
        "DEBIT" to totalDebit,
        "CREDIT" to totalCredit
    ).filter { it.value > 0.0 }

    val dailyTotals = remember(monthTx) {
        monthTx.groupBy { tx ->
            Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .dayOfMonth
        }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    // ❌ list items should NOT exclude ignored
    val monthTxAll = allTransactions.inMonth(month)

    val dayTransactions = monthTxAll.onDay(selectedDay ?: -1, month)

    val typeFiltered = monthTxAll.ofType(selectedType)

    val listToShow = when {
        selectedDay != null -> dayTransactions
        selectedType != null -> typeFiltered
        else -> monthTxAll.take(10)
    }

    // -------------------------------------------------------
    // SCAFFOLD WRAPPER
    // -------------------------------------------------------

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddExpense.route) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            // Month selector
            item {
                MonthSelector(
                    month = month,
                    onMonthChange = {
                        month = it
                        selectedType = null
                        selectedDay = null
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            // Totals
            item {
                Text("Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text("Total Debit: ₹${totalDebit.toInt()}")
                Text("Total Credit: ₹${totalCredit.toInt()}")
                Spacer(Modifier.height(16.dp))
            }

            // Pie Chart
            item {
                Text("Debit vs Credit", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                CategoryPieChart(
                    data = debitCreditTotals,
                    onSliceClick = { clicked ->
                        selectedType = if (selectedType == clicked) null else clicked
                        selectedDay = null
                    }
                )

                Spacer(Modifier.height(20.dp))
            }

            // Bar Chart
            item {
                Text("Daily Spending", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                DailyBarChart(
                    data = dailyTotals,
                    onBarClick = { day ->
                        selectedDay = if (selectedDay == day) null else day
                        selectedType = null
                    }
                )

                Spacer(Modifier.height(20.dp))
            }

            // List header
            item {
                when {
                    selectedDay != null -> Text(
                        "Transactions on $selectedDay ${
                            month.month.name.lowercase().replaceFirstChar { it.uppercase() }
                        }",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    selectedType != null -> Text(
                        "$selectedType Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    else -> Text(
                        "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // List items
            items(listToShow) { tx ->
                SmsListItem(
                    sms = tx,
                    onClick = { viewModel.onMessageClicked(it) },
                    onRequestMerchantFix = { showFixDialog = it },
                    onMarkNotExpense = { sms, isChecked ->
                        viewModel.setIgnoredState(sms, isChecked)
                    }
                )
            }
        }
    }
}
