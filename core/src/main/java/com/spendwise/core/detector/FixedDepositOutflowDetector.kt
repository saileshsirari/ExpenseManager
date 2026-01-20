
package com.spendwise.core.detector

import com.spendwise.core.ml.SenderType

object FixedDepositOutflowDetector {

    private const val MIN_FD_AMOUNT = 10_000.0

    // 🔒 Very explicit FD keywords (NO generic words)
    private val FD_KEYWORDS = listOf(
        "to fd",
        "fd no",
        "fd no.",
        "fixed deposit",
        "term deposit",
        "fd booked",
        "fd created",
        "deposit booked",
        "booked fd",
        "opened fd",
        "create fd"
    )

    // 🔒 Strong exclusions
    private val EXCLUDE_KEYWORDS = listOf(
        "interest credited",
        "fd interest",
        "maturity amount",
        "fd matured",
        "premature",
        "closure",
        "closed fd"
    )

    /**
     * Detects FD creation / booking outflow.
     *
     * HARD RULES:
     * - BANK sender
     * - DEBIT only
     * - Large amount
     * - Explicit FD wording
     * - Not interest / maturity
     */
    fun isFixedDepositOutflow(
        senderType: SenderType,
        txType: String?,
        body: String,
        amount: Double
    ): Boolean {

        // 🔒 Direction guard
        if (txType != "DEBIT") return false

        // 🔒 Sender guard
        if (senderType != SenderType.BANK) return false

        // 🔒 Amount guard
        if (amount < MIN_FD_AMOUNT) return false

        val text = body.lowercase()

        // 🔒 Exclude interest / maturity / closure
        if (EXCLUDE_KEYWORDS.any { text.contains(it) }) return false

        // ✅ Explicit FD match
        return FD_KEYWORDS.any { text.contains(it) }
    }
}
