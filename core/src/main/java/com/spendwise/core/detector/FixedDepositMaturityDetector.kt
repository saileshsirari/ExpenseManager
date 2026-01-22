
package com.spendwise.core.detector

import com.spendwise.core.ml.SenderType

object FixedDepositMaturityDetector {

    private val MATURITY_KEYWORDS = listOf(
        "fd matured",
        "maturity amount",
        "fd maturity",
        "term deposit matured",
        "fixed deposit matured",
        "deposit matured",
        "fd closed",
        "premature closure",
        "prematurely closed"
    )

    /**
     * Detects FD maturity / closure CREDIT.
     *
     * Rules:
     * - BANK sender
     * - CREDIT only
     * - Explicit maturity / closure wording
     */
    fun isFdMaturityCredit(
        senderType: SenderType,
        txType: String?,
        body: String
    ): Boolean {

        if (txType != "CREDIT") return false
        if (senderType != SenderType.BANK) return false

        val text = body.lowercase()
        return MATURITY_KEYWORDS.any { text.contains(it) }
    }
}
