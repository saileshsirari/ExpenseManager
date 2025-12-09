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
fun CalendarScreen(
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.items.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val monthTx = allTransactions
        .active()
        .inMonth(month)

    val dailyTotals = remember(monthTx) {
        monthTx.groupBy { tx ->
            Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .dayOfMonth
        }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val filteredTx = remember(monthTx, selectedDay) {
        if (selectedDay == null) emptyList()
        else monthTx.filter { tx ->
            val date = Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            date.dayOfMonth == selectedDay
        }
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
                    selectedDay = null
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            Text(
                "Calendar & Daily Trends",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            Text("Daily Spending", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            DailyBarChart(
                data = dailyTotals,
                onBarClick = { day -> selectedDay = day }
            )

            Spacer(Modifier.height(16.dp))
        }

        if (selectedDay != null) {
            item {
                Text(
                    "Transactions on $selectedDay",
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
}