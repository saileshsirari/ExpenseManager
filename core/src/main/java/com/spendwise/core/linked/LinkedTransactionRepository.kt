package com.spendwise.core.linked

import com.spendwise.core.model.TransactionCoreModel

interface LinkedTransactionRepository {

    suspend fun findCandidates(
        amount: Double,
        from: Long,
        to: Long,
        excludeId: Long
    ): List<TransactionCoreModel>

    suspend fun updateLink(
        id: Long,
        linkId: String?,
        linkType: String?,
        confidence: Int,
        isNetZero: Boolean
    )

    /**
     * Returns ALL SMS entries that already have a linkId.
     * Detector uses this to detect repeated transfer patterns
     * and infer missing credit/debit SMS.
     */
    suspend fun getAllLinked(): List<TransactionCoreModel>

    /**
     * Returns all learned pattern signatures.
     * Used for missing-credit inference.
     */
    suspend fun getAllLinkedPatterns(): Set<String>

    suspend fun getAllLinkedDebitPatterns(): Set<String>

}
