package com.spendwise.domain.com.spendwise.feature.smsimport.data

import com.spendwise.core.com.spendwise.core.AppDefaults
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.UiTxnRow
import java.time.YearMonth

data class DashboardUiState(
    val mode: DashboardMode = DashboardMode.MONTH,
    val period: YearMonth = YearMonth.now(),
    val rows : List<UiTxnRow> = emptyList(),
    val showGroupedMerchants: Boolean =true,
    val selectedType: String? = null,
    val selectedDay: Int? = null,
    val selectedMonth: Int? = null,
    val showIgnored: Boolean = false,

    val sortConfig: SortConfig = SortConfig(),

    val finalList: List<SmsEntity> = emptyList(),
    val sortedList: List<SmsEntity> = emptyList(),

    val totalsDebit: Double = 0.0,
    val totalsCredit: Double = 0.0,
    val debitCreditTotals: Map<String, Double> = emptyMap(),

    val barData: Map<Int, Double> = emptyMap(),
    val currencyCode: String = AppDefaults.DEFAULT_CURRENCY
)
fun DashboardUiState.periodLabel(): String =
    when (mode) {
        DashboardMode.MONTH ->
            period.month.name.lowercase().replaceFirstChar { it.uppercase() } +
                    " ${period.year}"

        DashboardMode.QUARTER ->
            "Q${((period.monthValue - 1) / 3) + 1} ${period.year}"

        DashboardMode.YEAR ->
            period.year.toString()
    }
