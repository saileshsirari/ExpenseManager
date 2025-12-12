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
import com.spendwise.core.Logger as Log

enum class DashboardMode { MONTH, QUARTER, YEAR }

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    // Source of truth
    val allTransactions by viewModel.items.collectAsState()

    // UI state
    var sortConfig by remember { mutableStateOf(SortConfig()) }
    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }
    var mode by remember { mutableStateOf(DashboardMode.MONTH) }
    var period by remember { mutableStateOf(YearMonth.now()) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedMonthFilter by remember { mutableStateOf<Int?>(null) }
    var showInternalTransfers by remember { mutableStateOf(false) }
    var showTransfersOnly by remember { mutableStateOf(false) }

    // Light debug: proves recomposition frequency without being spammy
    Log.d("RECOMPOSE", "DashboardScreen composed (transactions=${allTransactions.size})")

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

    // === Heavy computations are hoisted and remembered so they run only when inputs change ===
    // Base active list
    val baseList = remember(allTransactions) { allTransactions.active() }

    // Period-filtered list (recomputes only when mode/period/baseList change)
    val periodTx = remember(baseList, mode, period) {
        when (mode) {
            DashboardMode.MONTH -> baseList.inMonth(period)
            DashboardMode.QUARTER -> baseList.inQuarter(period)
            DashboardMode.YEAR -> baseList.inYear(period.year)
        }
    }

    // totalsList excludes internal / possible transfers and net-zero
    val totalsList = remember(periodTx) {
        periodTx.filter { tx ->
            !tx.isNetZero &&
                    tx.linkType != "INTERNAL_TRANSFER" &&
                    tx.linkType != "POSSIBLE_TRANSFER"
        }
    }

    // debit/credit totals
    val totalDebit = remember(totalsList) { totalsList.filter { it.type.equals("DEBIT", true) }.sumOf { it.amount } }
    val totalCredit = remember(totalsList) { totalsList.filter { it.type.equals("CREDIT", true) }.sumOf { it.amount } }

    val debitCreditTotals = remember(totalDebit, totalCredit) {
        mapOf("DEBIT" to totalDebit, "CREDIT" to totalCredit).filter { it.value > 0.0 }
    }

    // Bar chart data
    val barData = remember(periodTx, mode) {
        when (mode) {
            DashboardMode.MONTH ->
                periodTx.groupBy { it.localDate().dayOfMonth }
                    .mapValues { it.value.sumOf { tx -> tx.amount } }

            DashboardMode.QUARTER, DashboardMode.YEAR ->
                periodTx.groupBy { it.localDate().monthValue }
                    .mapValues { it.value.sumOf { tx -> tx.amount } }
        }
    }

    // Date filter
    val dateFiltered = remember(periodTx, mode, selectedDay, selectedMonthFilter) {
        when {
            mode == DashboardMode.MONTH && selectedDay != null ->
                periodTx.filter { it.localDate().dayOfMonth == selectedDay }
            mode != DashboardMode.MONTH && selectedMonthFilter != null ->
                periodTx.filter { it.localDate().monthValue == selectedMonthFilter }
            else -> periodTx
        }
    }

    // Type filter
    val typeFiltered = remember(dateFiltered, selectedType) { dateFiltered.ofType(selectedType) }

    // Show/hide internal transfers for list
    val finalList = remember(typeFiltered, showInternalTransfers) {
        if (showInternalTransfers) typeFiltered
        else typeFiltered.filter { it.linkType != "INTERNAL_TRANSFER" && it.linkType != "POSSIBLE_TRANSFER" }
    }

    // Sorting - run only when finalList or sortConfig change
    val sortedList = remember(finalList, sortConfig) {
        finalList.sortedWith(
            compareBy<SmsEntity> { tx ->
                when (sortConfig.primary) {
                    SortField.DATE -> tx.timestamp
                    SortField.AMOUNT -> tx.amount
                }
            }.let { comp ->
                if (sortConfig.primaryOrder == SortOrder.ASC) comp else comp.reversed()
            }
                .thenComparator { a, b ->
                    val secA: Comparable<*> = when (sortConfig.secondary) {
                        SortField.DATE -> a.timestamp
                        SortField.AMOUNT -> a.amount
                    }
                    val secB: Comparable<*> = when (sortConfig.secondary) {
                        SortField.DATE -> b.timestamp
                        SortField.AMOUNT -> b.amount
                    }
                    val result = compareValues(secA, secB)
                    if (sortConfig.secondaryOrder == SortOrder.ASC) result else -result
                }
        )
    }

    // === UI: single LazyColumn with header items + list items ===
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
            item {
                Row {
                    ModeTab("Month", mode == DashboardMode.MONTH) {
                        mode = DashboardMode.MONTH
                        selectedDay = null; selectedType = null; selectedMonthFilter = null
                    }
                    ModeTab("Quarter", mode == DashboardMode.QUARTER) {
                        mode = DashboardMode.QUARTER
                        selectedDay = null; selectedType = null; selectedMonthFilter = null
                    }
                    ModeTab("Year", mode == DashboardMode.YEAR) {
                        mode = DashboardMode.YEAR
                        selectedDay = null; selectedType = null; selectedMonthFilter = null
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ------------------- PERIOD HEADER -------------------
            item {
                PeriodHeader(mode = mode, period = period) { newPeriod ->
                    period = newPeriod
                    selectedDay = null; selectedType = null; selectedMonthFilter = null
                }
                Spacer(Modifier.height(16.dp))
            }

            // ------------------- TOTALS / CHARTS / FILTERS (header section) -------------------
            item {
                // Keep Crossfade only around small UI differences (optional)
                Crossfade(targetState = mode) { currentMode ->
                    Column {
                        // Totals
                        Text("Total Debit: ₹${totalDebit.toInt()}", style = MaterialTheme.typography.bodyLarge)
                        Text("Total Credit: ₹${totalCredit.toInt()}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(20.dp))

                        // PIE
                        Text("Debit vs Credit", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        CategoryPieChart(
                            data = debitCreditTotals,
                            onSliceClick = { clicked ->
                                selectedType = if (selectedType == clicked) null else clicked
                                selectedDay = null
                                selectedMonthFilter = null
                                showTransfersOnly = false
                            }
                        )
                        Spacer(Modifier.height(20.dp))

                        // BAR CHART
                        val barTitle = if (currentMode == DashboardMode.MONTH) "Daily Spending" else "Spending by Month"
                        Text(barTitle, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        DailyBarChart(
                            data = barData,
                            onBarClick = { idx ->
                                when (currentMode) {
                                    DashboardMode.MONTH -> selectedDay = if (selectedDay == idx) null else idx
                                    DashboardMode.QUARTER, DashboardMode.YEAR ->
                                        selectedMonthFilter = if (selectedMonthFilter == idx) null else idx
                                }
                                selectedType = null
                                showTransfersOnly = false
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        // SORT + TRANSFER TOGGLE
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SortHeader(sortConfig) { sortConfig = it }

                            Text(
                                text = if (showTransfersOnly) "Transfers ✓" else "Transfers",
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable {
                                        showTransfersOnly = !showTransfersOnly
                                        selectedType = null
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
                                .clickable { showInternalTransfers = !showInternalTransfers }
                        ) {
                            Checkbox(checked = showInternalTransfers, onCheckedChange = { showInternalTransfers = it })
                            Text(text = "Show internal transfers", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Separator
            item { Spacer(Modifier.height(12.dp)) }

            // ------------------- TRANSACTION LIST -------------------
            // Use LazyColumn.items for real laziness; keys to avoid full recomposition
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

enum class SortField { DATE, AMOUNT }
enum class SortOrder { ASC, DESC }

data class SortConfig(
    val primary: SortField = SortField.DATE,
    val primaryOrder: SortOrder = SortOrder.DESC,
    val secondary: SortField = SortField.AMOUNT,
    val secondaryOrder: SortOrder = SortOrder.ASC
)
