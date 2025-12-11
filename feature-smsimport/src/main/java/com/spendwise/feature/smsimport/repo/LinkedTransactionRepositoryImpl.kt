package com.spendwise.domain.com.spendwise.feature.smsimport.repo

import android.util.Log
import com.spendwise.core.linked.LinkedTransactionRepository
import com.spendwise.core.model.TransactionCoreModel
import com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper.toDomain
import com.spendwise.feature.smsimport.data.SmsDao
import com.spendwise.feature.smsimport.data.SmsEntity

class LinkedTransactionRepositoryImpl(
    private val dao: SmsDao
) : LinkedTransactionRepository {

    private val TAG = "LinkedRepo"

    // --------------------------------------------------------------------
    // NORMAL CANDIDATE SEARCH (existing logic)
    // --------------------------------------------------------------------
    override suspend fun findCandidates(
        amount: Double,
        from: Long,
        to: Long,
        excludeId: Long
    ): List<TransactionCoreModel> {
        return dao.findByAmountAndDateRange(amount, from, to, excludeId)
            .map { it.toDomain() }
    }

    // --------------------------------------------------------------------
    // UPDATE LINK FIELDS (existing)
    // --------------------------------------------------------------------
    override suspend fun updateLink(
        id: Long,
        linkId: String?,
        linkType: String?,
        confidence: Int,
        isNetZero: Boolean
    ) {
        dao.updateLink(id, linkId, linkType, confidence, isNetZero)
    }

    // ====================================================================
    // NEW LOGIC: PATTERN LEARNING FOR MISSING-CREDIT DETECTION
    // ====================================================================

    /** Cache for speed */
    private var cachedPatterns: Set<String>? = null

    /**
     * Return a set of pattern keys learned from all already-linked transfers.
     * Detector uses this to infer missing credit/debit pairs.
     */
    override suspend fun getAllLinkedPatterns(): Set<String> {

        cachedPatterns?.let { return it }

        val linked = dao.getAllLinked()   // requires DAO update
        val patterns = linked.mapNotNull { buildPattern(it) }.toSet()

        Log.d(TAG, "Loaded ${patterns.size} linked transfer patterns")
        cachedPatterns = patterns

        return patterns
    }

    // --------------------------------------------------------------------
    // PATTERN BUILDER for each linked SMS
    // --------------------------------------------------------------------
    private fun buildPattern(sms: SmsEntity): String? {

        val merchantNorm = normalize(sms.merchant)
        val bank = extractBank(sms.sender)
        val phrase = extractPhrase(sms.body)

        if (merchantNorm == null || phrase == null) return null

        return "$bank|$merchantNorm|$phrase"
    }

    // --------------------------------------------------------------------
    // HELPERS (same as detector logic)
    // --------------------------------------------------------------------
    private fun normalize(s: String?): String? {
        if (s.isNullOrBlank()) return null
        return s.lowercase().replace("[^a-z]".toRegex(), "")
    }

    private fun extractBank(sender: String?): String =
        sender?.substringBefore("-")?.uppercase() ?: "BANK"

    private fun extractPhrase(body: String?): String? {
        if (body.isNullOrBlank()) return null

        val b = body.lowercase()
        return when {
            b.contains("transferred to") -> "transferred_to"
            b.contains("deposit by transfer from") -> "deposit_from"
            b.contains("credit by transfer") -> "credit_transfer"
            else -> null
        }
    }
}
