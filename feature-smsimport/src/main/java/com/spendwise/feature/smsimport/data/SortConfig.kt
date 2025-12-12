package com.spendwise.domain.com.spendwise.feature.smsimport.data

enum class SortField { DATE, AMOUNT }
enum class SortOrder { ASC, DESC }
enum class DashboardMode { MONTH, QUARTER, YEAR }
data class SortConfig(
    val primary: SortField = SortField.DATE,
    val primaryOrder: SortOrder = SortOrder.DESC,
    val secondary: SortField = SortField.AMOUNT,
    val secondaryOrder: SortOrder = SortOrder.ASC
)