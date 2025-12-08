package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.spendwise.app.ui.dashboard.MonthSelector
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MerchantsScreen(
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.items.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var searchQuery by remember { mutableStateOf("") }

    data class MerchantSummary(
        val name: String,
        val total: Double,
        val count: Int
    )

    val monthTx = remember(allTransactions, month) {
        allTransactions.filter { tx ->
            val date = Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            YearMonth.from(date) == month
        }
    }

    val merchants = remember(monthTx) {
        monthTx
            .filter { it.type.equals("DEBIT", true) }
            .groupBy { it.merchant ?: it.sender }
            .map { (merchant, list) ->
                MerchantSummary(
                    name = merchant,
                    total = list.sumOf { it.amount },
                    count = list.size
                )
            }
            .sortedByDescending { it.total }
    }

    val filtered = remember(merchants, searchQuery) {
        if (searchQuery.isBlank()) merchants
        else merchants.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        MonthSelector(
            month = month,
            onMonthChange = { month = it }
        )
        Spacer(Modifier.height(16.dp))

        Text(
            "Top Merchants",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search merchants") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn {
            items(filtered) { m ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(m.name, fontWeight = FontWeight.SemiBold)
                    Text("Total: ₹${m.total.toInt()} (${m.count} txns)")
                }
                Divider()
            }
        }
    }
}