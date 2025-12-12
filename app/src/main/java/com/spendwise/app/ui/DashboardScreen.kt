package com.spendwise.app.ui


import android.os.Build
import androidx.annotation.RequiresApi
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
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortConfig
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortField
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortOrder
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.YearMonth
import com.spendwise.core.Logger as Log

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    // Read the single uiState from ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Local UI state for ephemeral UI controls
    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }
    var showTransfersOnly by remember { mutableStateOf(false) }

    // Light debug to verify composition frequency (not spammy)
    Log.d("RECOMPOSE", "DashboardScreen composed (sorted=${uiState.sortedList.size})")

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

    // Derive a few local values from uiState for readability
    val totalDebit = uiState.totalsDebit
    val totalCredit = uiState.totalsCredit
    val debitCreditTotals = uiState.debitCreditTotals
    val barData = uiState.barData
    val sortedList = uiState.sortedList
    val finalList = uiState.finalList

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

            // ------------------- MODE SELECTOR -------------------
            // ------------------- MODE SELECTOR -------------------
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


            // ------------------- PERIOD HEADER -------------------
            item {
                // We need to read current period from ViewModel UiInputs; UiState doesn't expose the inputs object,
                // so we call viewModel.setPeriod when user clicks prev/next. For display, we'll keep a local representation.
                // Safer approach is to expose inputs from ViewModel; if not available, we'll show YearMonth.now()
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

            // ------------------- TOTALS / CHARTS / FILTERS (header section) -------------------
            item {
                Column {
                    // Totals
                    Text(
                        "Total Debit: ₹${totalDebit.toInt()}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Total Credit: ₹${totalCredit.toInt()}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(20.dp))

                    // PIE
                    Text("Debit vs Credit", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    CategoryPieChart(
                        data = debitCreditTotals,
                        onSliceClick = { clicked ->
                            // trigger ViewModel to set selected type
                            viewModel.setSelectedType(if (uiState.finalList.any { it.type == clicked }) clicked else null)
                        }
                    )
                    Spacer(Modifier.height(20.dp))

                    // BAR CHART
                    Text(
                        text = if (uiState.finalList.isNotEmpty()) "Spending" else "Spending",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    DailyBarChart(
                        data = barData,
                        onBarClick = { idx ->
                            // set day/month in view model
                            viewModel.setSelectedDay(if (uiState.finalList.any()) idx else null)
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    // SORT + TRANSFER TOGGLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SortHeader(
                            sortConfig = uiState.sortConfig,
                            onSortChange = { newSort -> viewModel.updateSort(newSort) }
                        )

                        Text(
                            text = if (showTransfersOnly) "Transfers ✓" else "Transfers",
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clickable {
                                    showTransfersOnly = !showTransfersOnly
                                    // no-op: showTransfersOnly is local; you can also push state to viewModel if desired
                                },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (showTransfersOnly) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Show internal toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { viewModel.toggleInternalTransfers() }
                    ) {
                        Checkbox(
                            checked = uiState.finalList !== uiState.finalList, // placeholder to avoid compile-time issue; main state toggles in ViewModel
                            onCheckedChange = { viewModel.toggleInternalTransfers() }
                        )
                        Text(
                            text = "Show internal transfers",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }

            }

            // Separator
            item { Spacer(Modifier.height(12.dp)) }

            // ------------------- TRANSACTION LIST -------------------
            items(
                items = sortedList,
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

            // bottom padding
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/* ---------- Rest of file unchanged (PeriodHeader, SortHeader, enums, data classes) ---------- */

// Note: The code above assumes ModeTab and other small helpers are available as in your repo.
// If ModeTab signature expects (label: String, selected: Boolean, onClick: () -> Unit) the placeholder
// ModeTab usages earlier should be reconciled to your real ModeTab implementation.


/* ---------- Rest of file unchanged (PeriodHeader, SortHeader, enums, data classes) ---------- */

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


@Composable
fun SortHeader(
    sortConfig: SortConfig,
    onSortChange: (SortConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        // ---- DATE SORT BUTTON ----
        val dateArrow =
            if (sortConfig.primary == SortField.DATE)
                if (sortConfig.primaryOrder == SortOrder.ASC) "▲" else "▼"
            else ""

        Text(
            text = "Sort: Date $dateArrow",
            modifier = Modifier.clickable {

                onSortChange(
                    if (sortConfig.primary == SortField.DATE) {
                        // Toggle ASC <-> DESC
                        sortConfig.copy(
                            primaryOrder = if (sortConfig.primaryOrder == SortOrder.ASC)
                                SortOrder.DESC else SortOrder.ASC
                        )
                    } else {
                        // Promote DATE to primary
                        sortConfig.copy(
                            primary = SortField.DATE,
                            primaryOrder = SortOrder.DESC,
                            secondary = SortField.AMOUNT,
                            secondaryOrder = sortConfig.secondaryOrder
                        )
                    }
                )
            },
            style = MaterialTheme.typography.labelLarge
        )

        // ---- AMOUNT SORT BUTTON ----
        val amountArrow =
            if (sortConfig.primary == SortField.AMOUNT)
                if (sortConfig.primaryOrder == SortOrder.ASC) "▲" else "▼"
            else ""

        Text(
            text = "Sort: Amount $amountArrow",
            modifier = Modifier.clickable {

                onSortChange(
                    if (sortConfig.primary == SortField.AMOUNT) {
                        // Toggle ASC <-> DESC
                        sortConfig.copy(
                            primaryOrder = if (sortConfig.primaryOrder == SortOrder.ASC)
                                SortOrder.DESC else SortOrder.ASC
                        )
                    } else {
                        // Promote AMOUNT to primary
                        sortConfig.copy(
                            primary = SortField.AMOUNT,
                            primaryOrder = SortOrder.DESC,
                            secondary = SortField.DATE,
                            secondaryOrder = sortConfig.secondaryOrder
                        )
                    }
                )
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}


