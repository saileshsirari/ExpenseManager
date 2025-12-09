package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.spendwise.core.extensions.active
import com.spendwise.core.extensions.inMonth
import com.spendwise.core.extensions.inQuarter
import com.spendwise.core.extensions.inYear
import com.spendwise.core.extensions.localDate
import com.spendwise.core.extensions.nextQuarter
import com.spendwise.core.extensions.nextYear
import com.spendwise.core.extensions.ofType
import com.spendwise.core.extensions.previousQuarter
import com.spendwise.core.extensions.previousYear
import com.spendwise.core.extensions.toQuarterTitle
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.YearMonth

enum class DashboardMode { MONTH, QUARTER, YEAR }

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.items.collectAsState()

    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }
    var mode by remember { mutableStateOf(DashboardMode.MONTH) }
    var period by remember { mutableStateOf(YearMonth.now()) }

    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedMonthFilter by remember { mutableStateOf<Int?>(null) }

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

            // ------------------- MODE TABS -------------------
            item {
                Row {
                    ModeTab("Month", mode == DashboardMode.MONTH) {
                        mode = DashboardMode.MONTH
                        selectedDay = null
                        selectedMonthFilter = null
                        selectedType = null
                    }
                    ModeTab("Quarter", mode == DashboardMode.QUARTER) {
                        mode = DashboardMode.QUARTER
                        selectedDay = null
                        selectedMonthFilter = null
                        selectedType = null
                    }
                    ModeTab("Year", mode == DashboardMode.YEAR) {
                        mode = DashboardMode.YEAR
                        selectedDay = null
                        selectedMonthFilter = null
                        selectedType = null
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ------------------- PERIOD HEADER -------------------
            item {
                PeriodHeader(
                    mode = mode,
                    period = period,
                    onPeriodChange = { newPeriod ->
                        period = newPeriod
                        selectedDay = null
                        selectedMonthFilter = null
                        selectedType = null
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            // ------------------- MODE CONTENT -------------------
            item {
                Crossfade(
                    targetState = Triple(mode, period, allTransactions),
                    label = "dashboard-mode-crossfade"
                ) { (currentMode, currentPeriod, txList) ->

                    val base = txList.active()

                    // ------------------- RANGE FILTER -------------------
                    val periodTx = when (currentMode) {
                        DashboardMode.MONTH -> base.inMonth(currentPeriod)
                        DashboardMode.QUARTER -> base.inQuarter(currentPeriod)
                        DashboardMode.YEAR -> base.inYear(currentPeriod.year)
                    }

                    // ------------------- TOTALS -------------------
                    val totalDebit = periodTx.filter { it.type.equals("DEBIT", true) }.sumOf { it.amount }
                    val totalCredit = periodTx.filter { it.type.equals("CREDIT", true) }.sumOf { it.amount }

                    val debitCreditTotals = mapOf(
                        "DEBIT" to totalDebit,
                        "CREDIT" to totalCredit
                    ).filter { it.value > 0.0 }

                    // ------------------- BAR DATA -------------------
                    val barData: Map<Int, Double> = when (currentMode) {
                        DashboardMode.MONTH ->
                            periodTx.groupBy { it.localDate().dayOfMonth }
                                .mapValues { it.value.sumOf { tx -> tx.amount } }

                        DashboardMode.QUARTER, DashboardMode.YEAR ->
                            periodTx.groupBy { it.localDate().monthValue }
                                .mapValues { it.value.sumOf { tx -> tx.amount } }
                    }

                    // ------------------- FILTERING LOGIC -------------------
                    val filteredByDate = when {
                        currentMode == DashboardMode.MONTH && selectedDay != null ->
                            periodTx.filter { it.localDate().dayOfMonth == selectedDay }

                        currentMode != DashboardMode.MONTH && selectedMonthFilter != null ->
                            periodTx.filter { it.localDate().monthValue == selectedMonthFilter }

                        else -> periodTx
                    }

                    val finalList = filteredByDate.ofType(selectedType)

                    // ALWAYS show all results — not recent
                    val listToShow = finalList

                    Column {

                        // ------------------- TOTALS UI -------------------
                        Text("Total Debit: ₹${totalDebit.toInt()}", style = MaterialTheme.typography.bodyLarge)
                        Text("Total Credit: ₹${totalCredit.toInt()}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(20.dp))

                        // ------------------- PIE CHART -------------------
                        Text("Debit vs Credit", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        CategoryPieChart(
                            data = debitCreditTotals,
                            onSliceClick = { clicked ->
                                selectedType =
                                    if (selectedType == clicked) null else clicked
                                selectedDay = null
                                selectedMonthFilter = null
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        // ------------------- BAR CHART -------------------
                        val barTitle = when (currentMode) {
                            DashboardMode.MONTH -> "Daily Spending"
                            else -> "Spending by Month"
                        }

                        Text(barTitle, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        DailyBarChart(
                            data = barData,
                            onBarClick = { index ->
                                when (currentMode) {
                                    DashboardMode.MONTH ->
                                        selectedDay = if (selectedDay == index) null else index

                                    DashboardMode.QUARTER, DashboardMode.YEAR ->
                                        selectedMonthFilter = if (selectedMonthFilter == index) null else index
                                }
                                selectedType = null
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        // ------------------- HEADER TEXT -------------------
                        val headerText = when {
                            currentMode == DashboardMode.MONTH && selectedDay != null ->
                                "Transactions on $selectedDay"

                            currentMode != DashboardMode.MONTH && selectedMonthFilter != null -> {
                                val m = java.time.Month.of(selectedMonthFilter!!)
                                "Transactions in ${m.name.lowercase().replaceFirstChar { it.uppercase() }}"
                            }

                            selectedType != null ->
                                "$selectedType Transactions"

                            currentMode == DashboardMode.MONTH ->
                                "All Transactions This Month"

                            currentMode == DashboardMode.QUARTER ->
                                "All Transactions This Quarter"

                            currentMode == DashboardMode.YEAR ->
                                "All Transactions This Year"

                            else -> "Transactions"
                        }

                        Text(headerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))

                        // ------------------- LIST -------------------
                        listToShow.forEach { tx ->
                            SmsListItem(
                                sms = tx,
                                onClick = { viewModel.onMessageClicked(it) },
                                onRequestMerchantFix = { showFixDialog = it },
                                onMarkNotExpense = { sms, checked ->
                                    viewModel.setIgnoredState(sms, checked)
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun PeriodHeader(
    mode: DashboardMode,
    period: YearMonth,
    onPeriodChange: (YearMonth) -> Unit
) {

    val title = when (mode) {
        DashboardMode.MONTH -> period.format(
            java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")
        )
        DashboardMode.QUARTER -> period.toQuarterTitle()
        DashboardMode.YEAR -> period.year.toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        // ---- Title ----
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // ---- Prev / Next Controls ----
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {

            // PREVIOUS
            Text(
                "<",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable {
                    val newPeriod = when (mode) {
                        DashboardMode.MONTH -> period.minusMonths(1)
                        DashboardMode.QUARTER -> period.previousQuarter()
                        DashboardMode.YEAR -> period.previousYear()
                    }
                    onPeriodChange(newPeriod)
                }
            )

            // NEXT
            Text(
                ">",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable {
                    val newPeriod = when (mode) {
                        DashboardMode.MONTH -> period.plusMonths(1)
                        DashboardMode.QUARTER -> period.nextQuarter()
                        DashboardMode.YEAR -> period.nextYear()
                    }
                    onPeriodChange(newPeriod)
                }
            )
        }
    }
}

