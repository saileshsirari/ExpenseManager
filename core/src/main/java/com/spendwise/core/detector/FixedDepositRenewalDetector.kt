package com.spendwise.core.detector

import com.spendwise.core.ml.SenderType

object FixedDepositRenewalDetector {

    private val RENEWAL_KEYWORDS = listOf(
        "fd renewed",
        "auto renewed",
        "renewed fd",
        "reinvestment",
        "maturity reinvested"
    )

    fun isFdRenewal(
        senderType: SenderType,
        body: String
    ): Boolean {

        if (senderType != SenderType.BANK) return false
        val text = body.lowercase()
        return RENEWAL_KEYWORDS.any { text.contains(it) }
    }
}
