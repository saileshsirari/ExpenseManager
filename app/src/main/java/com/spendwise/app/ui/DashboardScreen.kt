package com.spendwise.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.spendwise.app.navigation.Screen
import com.spendwise.app.ui.dashboard.CategoryPieChart
import com.spendwise.app.ui.dashboard.DailyBarChart
import com.spendwise.app.ui.dashboard.FixMerchantDialog
import com.spendwise.core.extensions.nextQuarter
import com.spendwise.core.extensions.nextYear
import com.spendwise.core.extensions.previousQuarter
import com.spendwise.core.extensions.previousYear
import com.spendwise.core.extensions.toQuarterTitle
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode
import com.spendwise.domain.com.spendwise.feature.smsimport.data.FullScreenLoader
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortConfig
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortField
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortOrder
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.YearMonth
import com.spendwise.core.Logger as Log

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.importProgress.collectAsState()

    // Collect in a LaunchedEffect so it runs only once
    // ✔ Capture context in composable scope
    Log.e("UISTATE", "isLoading = ${uiState.isLoading}, progress.done = ${progress.done}")

    // ******************************
    //  IMPORT PROGRESS (1st priority)
    // ******************************
    if (!progress.done) {
        ImportProgressScreen(progress)
        return
    }

    // ***********************************
    //  LOADING STATE AFTER IMPORT FINISHES
    // ***********************************
    if (uiState.isLoading) {
        FullScreenLoader("Preparing your dashboard…")
        return
    }

    // ******************************
    //  MAIN DASHBOARD UI (ready)
    // ******************************

    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }
    Log.d("RECOMPOSE", "DashboardScreen composed (sorted=${uiState.sortedList.size})")

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
            FloatingActionButton(onClick = { navController.navigate(Screen.AddExpense.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            // MODE SELECTOR
            item {
                Row {
                    ModeTab(
                        label = "Month",
                        selected = uiState.mode == DashboardMode.MONTH
                    ) {
                        viewModel.setMode(DashboardMode.MONTH)
                        viewModel.setSelectedDay(null)
                        viewModel.setSelectedMonth(null)
                        viewModel.setSelectedType(null)
                    }

                    ModeTab(
                        label = "Quarter",
                        selected = uiState.mode == DashboardMode.QUARTER
                    ) {
                        viewModel.setMode(DashboardMode.QUARTER)
                        viewModel.setSelectedDay(null)
                        viewModel.setSelectedMonth(null)
                        viewModel.setSelectedType(null)
                    }

                    ModeTab(
                        label = "Year",
                        selected = uiState.mode == DashboardMode.YEAR
                    ) {
                        viewModel.setMode(DashboardMode.YEAR)
                        viewModel.setSelectedDay(null)
                        viewModel.setSelectedMonth(null)
                        viewModel.setSelectedType(null)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // PERIOD HEADER
            item {
                PeriodHeader(
                    mode = uiState.mode,
                    period = uiState.period,
                    onPeriodChange = { newPeriod ->
                        viewModel.setPeriod(newPeriod)
                        viewModel.setSelectedDay(null)
                        viewModel.setSelectedMonth(null)
                        viewModel.setSelectedType(null)
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            // HEADER SUMMARY + CHARTS + SORT + FILTERS
            item {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // Totals
                    Text("Total Debit: ₹${uiState.totalsDebit.toInt()}", style = MaterialTheme.typography.bodyLarge)
                    Text("Total Credit: ₹${uiState.totalsCredit.toInt()}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))

                    // Pie
                    Text("Debit vs Credit", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    CategoryPieChart(
                        data = uiState.debitCreditTotals,
                        onSliceClick = { clicked -> viewModel.setSelectedType(clicked) }
                    )
                    Spacer(Modifier.height(16.dp))

                    // Bar chart
                    Text("Spending", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    DailyBarChart(
                        data = uiState.barData,
                        onBarClick = { idx -> viewModel.setSelectedDay(idx) }
                    )
                    Spacer(Modifier.height(16.dp))

                    // Sorting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SortHeader(
                            sortConfig = uiState.sortConfig,
                            onSortChange = { viewModel.updateSort(it) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Internal transfers toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleInternalTransfers() }
                    ) {
                        Checkbox(
                            checked = uiState.showInternalTransfers,
                            onCheckedChange = { viewModel.toggleInternalTransfers() }
                        )
                        Text("Show internal transfers", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // Transactions list
            items(
                items = uiState.sortedList,
                key = { it.id }
            ) { tx ->
                SmsListItem(
                    sms = tx,
                    onClick = { viewModel.onMessageClicked(it) },
                    onRequestMerchantFix = { showFixDialog = it },
                    onMarkNotExpense = { item, checked ->
                        viewModel.setIgnoredState(item, checked)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}


// ------------------------------------------------------
// PERIOD HEADER
// ------------------------------------------------------
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
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("<", modifier = Modifier.clickable {
                val newPeriod = when (mode) {
                    DashboardMode.MONTH -> period.minusMonths(1)
                    DashboardMode.QUARTER -> period.previousQuarter()
                    DashboardMode.YEAR -> period.previousYear()
                }
                onPeriodChange(newPeriod)
            })

            Text(">", modifier = Modifier.clickable {
                val newPeriod = when (mode) {
                    DashboardMode.MONTH -> period.plusMonths(1)
                    DashboardMode.QUARTER -> period.nextQuarter()
                    DashboardMode.YEAR -> period.nextYear()
                }
                onPeriodChange(newPeriod)
            })
        }
    }
}


// ------------------------------------------------------
// SORT HEADER
// ------------------------------------------------------
@Composable
fun SortHeader(
    sortConfig: SortConfig,
    onSortChange: (SortConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        val dateArrow =
            if (sortConfig.primary == SortField.DATE)
                if (sortConfig.primaryOrder == SortOrder.ASC) "▲" else "▼"
            else ""

        Text(
            text = "Sort: Date $dateArrow",
            modifier = Modifier.clickable {
                onSortChange(
                    if (sortConfig.primary == SortField.DATE) {
                        sortConfig.copy(
                            primaryOrder = if (sortConfig.primaryOrder == SortOrder.ASC)
                                SortOrder.DESC else SortOrder.ASC
                        )
                    } else {
                        sortConfig.copy(
                            primary = SortField.DATE,
                            primaryOrder = SortOrder.DESC,
                            secondary = SortField.AMOUNT
                        )
                    }
                )
            },
            style = MaterialTheme.typography.labelLarge
        )

        val amountArrow =
            if (sortConfig.primary == SortField.AMOUNT)
                if (sortConfig.primaryOrder == SortOrder.ASC) "▲" else "▼"
            else ""

        Text(
            text = "Sort: Amount $amountArrow",
            modifier = Modifier.clickable {
                onSortChange(
                    if (sortConfig.primary == SortField.AMOUNT) {
                        sortConfig.copy(
                            primaryOrder = if (sortConfig.primaryOrder == SortOrder.ASC)
                                SortOrder.DESC else SortOrder.ASC
                        )
                    } else {
                        sortConfig.copy(
                            primary = SortField.AMOUNT,
                            primaryOrder = SortOrder.DESC,
                            secondary = SortField.DATE
                        )
                    }
                )
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}
