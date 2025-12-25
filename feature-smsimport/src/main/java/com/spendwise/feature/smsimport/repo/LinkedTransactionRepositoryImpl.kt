package com.spendwise.domain.com.spendwise.feature.smsimport.repo

import com.spendwise.core.linked.LinkedTransactionRepository
import com.spendwise.core.model.TransactionCoreModel
import com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper.toDomain
import com.spendwise.feature.smsimport.data.LinkedPatternEntity
import com.spendwise.feature.smsimport.data.SmsDao

class LinkedTransactionRepositoryImpl(
    private val dao: SmsDao
) : LinkedTransactionRepository {

    // ---------------------------------------------------------
    // SAME-DAY CANDIDATE SEARCH
    // ---------------------------------------------------------
    override suspend fun findCandidates(
        amount: Double,
        from: Long,
        to: Long,
        excludeId: Long
    ): List<TransactionCoreModel> {
        return dao.findByAmountAndDateRange(amount, from, to, excludeId)
            .map { it.toDomain() }
    }

    // ---------------------------------------------------------
    // APPLY LINK TO A TX
    // ---------------------------------------------------------
    override suspend fun updateLink(
        id: Long,
        linkId: String?,
        linkType: String?,
        confidence: Int,
        isNetZero: Boolean
    ) {
        dao.updateLink(id, linkId, linkType, confidence, isNetZero)


    }

    // ---------------------------------------------------------
    // GET ALL ALREADY-LINKED TRANSACTIONS
    // ---------------------------------------------------------
    override suspend fun getAllLinked(): List<TransactionCoreModel> {
        return dao.getAllLinked().map { it.toDomain() }
    }

    // ---------------------------------------------------------
    // SAVE A NEW LEARNED LINKED PATTERN
    // ---------------------------------------------------------
    override suspend fun saveLinkedPattern(pattern: String) {
        dao.insertPattern(LinkedPatternEntity(pattern))
    }

    // ---------------------------------------------------------
    // READ ALL STORED PATTERNS
    // ---------------------------------------------------------
    override suspend fun getAllLinkedPatterns(): Set<String> {
        return dao.getAllLinkedPatterns().toSet()
    }

    // Only debit-side patterns such as:
    // "saileshsirari|transferred_to", "wallet|debited_from"
    override suspend fun getAllLinkedDebitPatterns(): Set<String> {
        return dao.getAllLinkedDebitPatterns().toSet()
    }

    // ---------------------------------------------------------
    // CREDIT-SIDE PATTERNS (Required by Detector)
    // ---------------------------------------------------------
    override suspend fun getAllLinkedCreditPatterns(): Set<String> {
        return dao.getAllLinkedCreditPatterns().toSet()
    }
}
