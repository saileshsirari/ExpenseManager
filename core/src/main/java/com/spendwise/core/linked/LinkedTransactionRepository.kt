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

    suspend fun getAllLinked(): List<TransactionCoreModel>

    suspend fun getAllLinkedPatterns(): Set<String>

    suspend fun getAllLinkedDebitPatterns(): Set<String>

    suspend fun getAllLinkedCreditPatterns(): Set<String>

    suspend fun saveLinkedPattern(pattern: String)

    suspend fun updateIgnore(id: Long, isIgnored: Boolean, reason: String)
}
