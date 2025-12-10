package com.spendwise.domain.com.spendwise.feature.smsimport.repo

import com.spendwise.core.linked.LinkedTransactionRepository
import com.spendwise.core.model.TransactionCoreModel
import com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper.toDomain
import com.spendwise.feature.smsimport.data.SmsDao

class LinkedTransactionRepositoryImpl(
    private val dao: SmsDao
) : LinkedTransactionRepository {

    override suspend fun findCandidates(
        amount: Double,
        from: Long,
        to: Long,
        excludeId: Long
    ): List<TransactionCoreModel> {
        return dao.findByAmountAndDateRange(amount, from, to, excludeId).map { it.toDomain() }
    }

    override suspend fun updateLink(
        id: Long,
        linkId: String?,
        linkType: String?,
        confidence: Int,
        isNetZero: Boolean
    ) {
        dao.updateLink(id, linkId, linkType, confidence, isNetZero)
    }
}
