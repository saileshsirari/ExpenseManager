package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.spendwise.app.ui.dashboard.CategoryPieChart
import com.spendwise.app.ui.dashboard.DailyBarChart
import com.spendwise.app.ui.dashboard.FixMerchantDialog
import com.spendwise.app.ui.dashboard.MonthSelector
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardScreen(
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.items.collectAsState()
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
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedType by remember { mutableStateOf<String?>(null) }   // DEBIT / CREDIT
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // -----------------------------
    // MONTH FILTER
    // -----------------------------
    val monthTx = remember(allTransactions, month) {
        allTransactions.filter { tx ->
            val date = Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            YearMonth.from(date) == month
        }
    }

    // Totals
    val totalDebit = monthTx.filter { it.type.equals("DEBIT", true) }.sumOf { it.amount }
    val totalCredit = monthTx.filter { it.type.equals("CREDIT", true) }.sumOf { it.amount }

    val debitCreditTotals = mapOf(
        "DEBIT" to totalDebit,
        "CREDIT" to totalCredit
    ).filter { it.value > 0.0 }

    // Daily totals for bar chart
    val dailyTotals = remember(monthTx) {
        monthTx.groupBy { tx ->
            Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .dayOfMonth
        }.mapValues { (_, list) ->
            list.sumOf { it.amount }
        }
    }

    // -----------------------------
    // CLICK FILTER: DAY
    // -----------------------------
    val dayTransactions = remember(selectedDay, monthTx) {
        if (selectedDay == null) emptyList()
        else monthTx.filter { tx ->
            val date = Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            date.dayOfMonth == selectedDay
        }
    }

    // -----------------------------
    // CLICK FILTER: DEBIT / CREDIT
    // -----------------------------
    val typeFilteredTx = remember(monthTx, selectedType) {
        if (selectedType == null) monthTx
        else monthTx.filter { it.type.equals(selectedType, ignoreCase = true) }
    }

    // -----------------------------
    // FINAL LIST: Priority
    // 1) Day filter
    // 2) Type filter
    // 3) Recent (fallback)
    // -----------------------------
    val listToShow = when {
        selectedDay != null -> dayTransactions
        selectedType != null -> typeFilteredTx
        else -> monthTx.take(10)
    }

    // -----------------------------
    // UI
    // -----------------------------
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
                    selectedType = null
                    selectedDay = null
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        // Header + totals
        item {
            Text(
                "Dashboard",
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
                    selectedDay = null  // clear day filter
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
                    selectedType = null // clear type filter
                }
            )

            Spacer(Modifier.height(20.dp))
        }

        // List Header (Changes depending on filter)
        item {
            when {
                selectedDay != null -> {
                    Text(
                        "Transactions on $selectedDay ${month.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                selectedType != null -> {
                    Text(
                        "$selectedType Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                else -> {
                    Text(
                        "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Final list
        items(listToShow) { tx ->
            SmsListItem(
                sms = tx,
                onClick = { viewModel.onMessageClicked(it) },
                onRequestMerchantFix = {    showFixDialog = it },
                onMarkNotExpense = { viewModel.markNotExpense(it) }
            )
        }
    }
}
