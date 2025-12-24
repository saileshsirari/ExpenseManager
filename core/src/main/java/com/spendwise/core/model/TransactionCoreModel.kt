
package com.spendwise.core.model

data class TransactionCoreModel(
    val id: Long,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val amount: Double,
    val merchant: String?,
    val type: String?,        // "debit" / "credit" / null
    val category: String?,
    val isIgnored: Boolean,
    val linkId: String?,
    val linkType: String?,
    val linkConfidence: Int,
    val isNetZero: Boolean
)

data class LinkScoreResult(
    val score: Int,
    val isNetZero: Boolean
)
