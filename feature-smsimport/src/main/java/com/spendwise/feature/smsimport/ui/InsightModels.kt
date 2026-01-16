package com.spendwise.domain.com.spendwise.feature.smsimport.ui

import com.spendwise.domain.com.spendwise.feature.smsimport.data.CategoryTotal

sealed class InsightBlock {
    data class CategoryList(
        val items: List<CategoryTotal>,
        val isLocked: Boolean
    ) : InsightBlock()

    data class Comparison(
        val title: String,
        val value: String?,
        val delta: String?,
        val isLocked: Boolean
    ) : InsightBlock()

    data class WalletInsight(
        val summary: String?,
        val isLocked: Boolean
    ) : InsightBlock()
}

