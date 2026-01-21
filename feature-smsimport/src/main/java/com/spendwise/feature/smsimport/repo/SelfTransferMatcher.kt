package com.spendwise.domain.com.spendwise.feature.smsimport.repo

import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.feature.smsimport.data.SmsEntity

object SelfTransferMatcher {

    private val TRANSFER_KEYWORDS = listOf(
        "transferred",
        "credited",
        "deposit by transfer",
        "upi",
        "imps",
        "neft"
    )

    private val EXCLUDE_KEYWORDS = listOf(
        "interest",
        "emi",
        "loan",
        "salary",
        "refund",
        "cashback",
        "reward",
        "reversal",
        "fd"
    )

    fun matches(
        tx: SmsEntity,
        personName: String,
        senderBank: String
    ): Boolean {

        val extractedPerson =
            MerchantExtractorMl.extractPersonName(tx.body)
                ?: return false

        if (extractedPerson != personName) return false
        if (!tx.sender.contains(senderBank, ignoreCase = true)) return false
        if (tx.type != "DEBIT") return false

        val body = tx.body.lowercase()

        if (EXCLUDE_KEYWORDS.any { body.contains(it) }) return false
        if (!TRANSFER_KEYWORDS.any { body.contains(it) }) return false

        return true
    }
}
