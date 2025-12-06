package com.spendwise.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.ui.SmsListItem
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MonthlyDashboardScreen(viewModel: SmsImportViewModel = hiltViewModel()) {

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.importAll{
                context.contentResolver
            }
        }
    }
    val allTransactions by viewModel.items.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    Log.d("SUMMARY", "YearMonth.now() = $month")

    LaunchedEffect(allTransactions) {
        Log.d("DASH", "Items in dashboard = ${allTransactions.size}")
    }
    val summary = viewModel.getMonthlySummary(allTransactions, month)

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        MonthSelector(month = month, onMonthChange = { month = it })

        Spacer(Modifier.height(16.dp))

        Text(
            "Total Spending: ₹${summary.total}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        Text("Category Breakdown", style = MaterialTheme.typography.titleMedium)
        CategoryPieChart(summary.categoryTotals, onSliceClick = { cat -> selectedCategory = cat })

        Spacer(Modifier.height(32.dp))

        Text("Daily Spending", style = MaterialTheme.typography.titleMedium)
        DailyBarChart(summary.dailyTotals, onBarClick = { day -> selectedDay = day })

        Spacer(Modifier.height(20.dp))

        val filtered = when {
            selectedCategory != null -> allTransactions.filter { it.type == selectedCategory }
            selectedDay != null -> allTransactions.filter {
                Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .dayOfMonth == selectedDay
            }
            else -> emptyList()
        }
        LazyColumn {
            items(filtered) { tx ->
                SmsListItem(tx)
            }
        }
    }
}
