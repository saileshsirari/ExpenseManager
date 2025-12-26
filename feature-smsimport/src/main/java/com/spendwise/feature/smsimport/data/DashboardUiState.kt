package com.spendwise.domain.com.spendwise.feature.smsimport.data

import com.spendwise.feature.smsimport.data.SmsEntity
import java.time.YearMonth

data class DashboardUiState(
    val mode: DashboardMode = DashboardMode.MONTH,
    val period: YearMonth = YearMonth.now(),

    val selectedType: String? = null,
    val selectedDay: Int? = null,
    val selectedMonth: Int? = null,
    val showInternalTransfers: Boolean = false,
    val showIgnored: Boolean = false,

    val sortConfig: SortConfig = SortConfig(),

    val finalList: List<SmsEntity> = emptyList(),
    val sortedList: List<SmsEntity> = emptyList(),

    val totalsDebit: Double = 0.0,
    val totalsCredit: Double = 0.0,
    val debitCreditTotals: Map<String, Double> = emptyMap(),

    val barData: Map<Int, Double> = emptyMap(),
    val isLoading: Boolean = true
)
