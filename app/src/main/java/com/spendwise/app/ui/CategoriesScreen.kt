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
import com.spendwise.app.ui.dashboard.MonthSelector
import com.spendwise.core.extensions.active
import com.spendwise.core.extensions.inMonth
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CategoriesScreen(
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.items.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val monthTx = allTransactions
        .active()
        .inMonth(month)

    val categoryTotals = remember(monthTx) {
        monthTx
            .filter { it.type.equals("DEBIT", true) } // categories make sense for debit
            .groupBy { it.category ?: "OTHER" }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val dailyTotalsForSelected = remember(monthTx, selectedCategory) {
        val target = if (selectedCategory == null) monthTx
        else monthTx.filter { it.category.equals(selectedCategory, ignoreCase = true) }

        target.groupBy { tx ->
            Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .dayOfMonth
        }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val filteredTx = remember(monthTx, selectedCategory) {
        if (selectedCategory == null) monthTx
        else monthTx.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        item {
            MonthSelector(
                month = month,
                onMonthChange = {
                    month = it
                    selectedCategory = null
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            Text("Category Breakdown", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            CategoryPieChart(
                data = categoryTotals,
                onSliceClick = { clicked ->
                    selectedCategory = if (selectedCategory == clicked) null else clicked
                }
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            Text(
                text = if (selectedCategory == null)
                    "Daily Spending (All Categories)"
                else
                    "Daily Spending for $selectedCategory",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))

            DailyBarChart(
                data = dailyTotalsForSelected,
                onBarClick = { /* could open day-details */ }
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            Text(
                text = if (selectedCategory == null)
                    "All Transactions (This Month)"
                else
                    "Transactions: $selectedCategory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
        }

        items(filteredTx) { tx ->
            SmsListItem(
                sms = tx,
                onClick = { viewModel.onMessageClicked(it) },
                onRequestMerchantFix = { viewModel.fixMerchant(it, it.merchant ?: "") },
                onMarkNotExpense = { sms, isChecked ->
                    viewModel.setIgnoredState(sms, isChecked)
                }
            )
        }
    }
}