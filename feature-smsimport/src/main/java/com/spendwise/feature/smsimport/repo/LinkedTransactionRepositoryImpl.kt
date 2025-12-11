package com.spendwise.domain.com.spendwise.feature.smsimport.repo

import com.spendwise.core.linked.LinkedTransactionRepository
import com.spendwise.core.model.TransactionCoreModel
import com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper.toDomain
import com.spendwise.feature.smsimport.data.SmsDao

class LinkedTransactionRepositoryImpl(
    private val dao: SmsDao
) : LinkedTransactionRepository {

    // -------------------------------------------------------------------------
    // SAME-DAY CANDIDATE SEARCH
    // -------------------------------------------------------------------------
    override suspend fun findCandidates(
        amount: Double,
        from: Long,
        to: Long,
        excludeId: Long
    ): List<TransactionCoreModel> {
        return dao.findByAmountAndDateRange(amount, from, to, excludeId)
            .map { it.toDomain() }
    }

    // -------------------------------------------------------------------------
    // UPDATE LINK FIELDS
    // -------------------------------------------------------------------------
    override suspend fun updateLink(
        id: Long,
        linkId: String?,
        linkType: String?,
        confidence: Int,
        isNetZero: Boolean
    ) {
        dao.updateLink(id, linkId, linkType, confidence, isNetZero)
    }

    // -------------------------------------------------------------------------
    // RETURN ALL LINKED TRX (any tx with linkId != null)
    // -------------------------------------------------------------------------
    override suspend fun getAllLinked(): List<TransactionCoreModel> {
        return dao.getAllOnce()
            .filter { !it.linkId.isNullOrBlank() }
            .map { it.toDomain() }
    }

    // -------------------------------------------------------------------------
    // RETURN ALL HISTORICAL PATTERNS (merchant + phrase)
    // -------------------------------------------------------------------------
    override suspend fun getAllLinkedPatterns(): Set<String> {
        val linked = dao.getAllOnce().filter { !it.linkId.isNullOrBlank() }

        val patterns = linked.map { tx ->
            val merchant = normalize(tx.merchant ?: tx.sender ?: "")
            val phrase = extractPhrase(tx.body ?: "")
            "$merchant|$phrase"
        }.toSet()

        return patterns
    }

    // -------------------------------------------------------------------------
    // RETURN ONLY DEBIT PATTERNS (For more accurate missing-credit inference)
    // -------------------------------------------------------------------------
    override suspend fun getAllLinkedDebitPatterns(): Set<String> {
        val linkedDebits = dao.getAllOnce()
            .filter { !it.linkId.isNullOrBlank() && it.type.equals("DEBIT", true) }

        val patterns = linkedDebits.map { tx ->
            val merchant = normalize(tx.merchant ?: tx.sender ?: "")
            val phrase = extractPhrase(tx.body ?: "")
            "$merchant|$phrase"
        }.toSet()

        return patterns
    }

    // -------------------------------------------------------------------------
    // HELPERS — MUST MATCH DETECTOR'S NORMALIZATION & PHRASE LOGIC
    // -------------------------------------------------------------------------
    private fun normalize(name: String): String =
        name.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun extractPhrase(body: String): String {
        val b = body.lowercase()

        return when {
            b.contains("transferred to") || b.contains("transfer to") || b.contains("transferred") ->
                "transferred_to"

            b.contains("deposit by transfer") ||
                    b.contains("deposit by") ||
                    b.contains("deposit") ||
                    b.contains("credited by transfer") ->
                "deposit_from"

            b.contains("credited") ||
                    b.contains("credit of") ||
                    b.contains("credited to") ->
                "credited_to"

            b.contains("debited") ||
                    b.contains("deducted") ||
                    b.contains("deducted from") ->
                "debited_from"

            b.contains("paid") && b.contains("via") ->
                "paid_via"

            b.contains("payzapp") || b.contains("wallet") ->
                "wallet_deduction"

            else -> "other"
        }
    }
}
