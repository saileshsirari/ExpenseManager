
package com.spendwise.feature.smsimport.ui

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.repo.SmsRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SmsImportViewModel @Inject constructor(private val repo: SmsRepositoryImpl): ViewModel() {
    private val _items = MutableStateFlow<List<SmsEntity>>(emptyList())
    val items: StateFlow<List<SmsEntity>> = _items
    private val _selectedExplanation = MutableStateFlow<MlReasonBundle?>(null)
    val selectedExplanation = _selectedExplanation

    fun importAll(resolverProvider: () -> android.content.ContentResolver) {
        viewModelScope.launch {
            repo.importAll(resolverProvider).collect {
                list -> _items.value = list
            }
        }
    }
    fun onMessageClicked(tx: SmsEntity) {
        viewModelScope.launch {
            val log = repo.getMlExplanationFor(tx)
            Log.d("expense",log.toString())
            Log.d("expense",tx.body)
            _selectedExplanation.value = log
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getMonthlySummary(
        items: List<SmsEntity>,
        month: YearMonth
    ): MonthlySummary {

        if (items.isEmpty()) return MonthlySummary()

        // Compute month range
        val zone = ZoneId.systemDefault()

        val startMillis = month
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val endMillis = month
            .atEndOfMonth()
            .atTime(LocalTime.MAX)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        Log.d("SMS", "Example SMS ts=${items.first().timestamp}")
        Log.d("SMS", "As date=" + Instant.ofEpochMilli(items.first().timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate())
        // Filter entries strictly within this month
        val monthly = items.filter { it.timestamp in startMillis..endMillis }

        if (monthly.isEmpty()) return MonthlySummary()

        // Daily totals (1..31)
        val dailyTotals = mutableMapOf<Int, Double>()

        val categoryTotals = mutableMapOf<String, Double>()

        monthly.forEach { tx ->
            val localDate = Instant.ofEpochMilli(tx.timestamp)
                .atZone(zone)
                .toLocalDate()

            // DAY OF MONTH
            val day = localDate.dayOfMonth
            dailyTotals[day] = (dailyTotals[day] ?: 0.0) + tx.amount

            // CATEGORY
            val cat = tx.type ?: "Other"
            categoryTotals[cat] = (categoryTotals[cat] ?: 0.0) + tx.amount
        }

        val totalSpent = categoryTotals.values.sum()

        return MonthlySummary(
            total = totalSpent,
            categoryTotals = categoryTotals,
            dailyTotals = dailyTotals
        )
    }


    data class MonthlySummary(
        val total: Double = 0.0,
        val categoryTotals: Map<String, Double> = emptyMap(),
        val dailyTotals: Map<Int, Double> = emptyMap()
    )


}
