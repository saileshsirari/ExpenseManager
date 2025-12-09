package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
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
import com.spendwise.core.extensions.active
import com.spendwise.core.extensions.inMonth
import com.spendwise.core.extensions.inQuarter
import com.spendwise.core.extensions.inYear
import com.spendwise.core.extensions.localDate
import com.spendwise.core.extensions.ofType
import com.spendwise.core.extensions.toQuarterName
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
    var month by remember { mutableStateOf(YearMonth.now()) }

    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // ---------------- FIX DIALOG ----------------
    showFixDialog?.let { tx ->
        FixMerchantDialog(
            tx = tx,
            onConfirm = { name ->
                viewModel.fixMerchant(tx, name)
                showFixDialog = null
            },
            onDismiss = { showFixDialog = null }
        )
    }

    // ---------------- APPLY MODE FILTERS ----------------
    val baseList = allTransactions.active()

    val rangeTx = when (mode) {
        DashboardMode.MONTH -> baseList.inMonth(month)
        DashboardMode.QUARTER -> baseList.inQuarter(month)
        DashboardMode.YEAR -> baseList.inYear(month.year)
    }

    // ---------------- COMPUTE TOTALS ----------------
    val totalDebit = rangeTx.filter { it.type.equals("DEBIT", true) }.sumOf { it.amount }
    val totalCredit = rangeTx.filter { it.type.equals("CREDIT", true) }.sumOf { it.amount }

    val debitCreditTotals = mapOf(
        "DEBIT" to totalDebit,
        "CREDIT" to totalCredit
    ).filter { it.value > 0.0 }

    // ---------------- DAILY TOTALS (only relevant for Month/Quarter) ----------------
    val dailyTotals = remember(rangeTx) {
        rangeTx.groupBy { it.localDate().dayOfMonth }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    // ---------------- FILTERED LIST ----------------
    val dayTransactions =
        if (selectedDay == null) emptyList()
        else rangeTx.filter { tx -> tx.localDate().dayOfMonth == selectedDay }

    val typeTransactions = rangeTx.ofType(selectedType)

    val listToShow =
        when {
            selectedDay != null -> dayTransactions
            selectedType != null -> typeTransactions
            else -> rangeTx.take(12)
        }

    // ---------------- SCAFFOLD ----------------
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddExpense.route) }
            ) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { pad ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
        ) {

            // ---- MODE SELECTOR ----
            item {
                Row {
                    ModeTab("Month", mode == DashboardMode.MONTH) {
                        mode = DashboardMode.MONTH
                        selectedDay = null
                        selectedType = null
                    }
                    ModeTab("Quarter", mode == DashboardMode.QUARTER) {
                        mode = DashboardMode.QUARTER
                        selectedDay = null
                        selectedType = null
                    }
                    ModeTab("Year", mode == DashboardMode.YEAR) {
                        mode = DashboardMode.YEAR
                        selectedDay = null
                        selectedType = null
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- PERIOD HEADER ----
            item {
                val title = when (mode) {
                    DashboardMode.MONTH -> month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                    DashboardMode.QUARTER -> month.toQuarterName()
                    DashboardMode.YEAR -> month.year.toString()
                }

                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
            }

            // ---- TOTALS ----
            item {
                Text("Total Debit: ₹${totalDebit.toInt()}")
                Text("Total Credit: ₹${totalCredit.toInt()}")
                Spacer(Modifier.height(20.dp))
            }

            // ---- PIE CHART ----
            item {
                CategoryPieChart(
                    data = debitCreditTotals,
                    onSliceClick = { clicked ->
                        selectedType = if (selectedType == clicked) null else clicked
                        selectedDay = null
                    }
                )
                Spacer(Modifier.height(20.dp))
            }

            // ---- DAILY CHART (Hide for YEAR) ----
            if (mode != DashboardMode.YEAR) {
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
            }

            // ---- LIST HEADER ----
            item {
                val header = when {
                    selectedDay != null -> "Transactions on $selectedDay"
                    selectedType != null -> "$selectedType Transactions"
                    else -> "Recent Transactions"
                }

                Text(header,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
            }

            // ---- ITEMS ----
            items(listToShow) { tx ->
                SmsListItem(
                    sms = tx,
                    onClick = { viewModel.onMessageClicked(it) },
                    onRequestMerchantFix = { showFixDialog = it },
                    onMarkNotExpense = { sms, checked -> viewModel.setIgnoredState(sms, checked) }
                )
            }
        }
    }
}

