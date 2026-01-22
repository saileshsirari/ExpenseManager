package com.spendwise.core.com.spendwise.core.detector

import com.spendwise.core.ml.SenderType

object ClearingEntityInvestmentDetector {

    private const val MIN_INVESTMENT_AMOUNT = 5_000.0

    // 🔒 Explicit clearing / registrar entities
    private val CLEARING_ENTITIES = listOf(
        // ICCL / ICICI
        "mutual funds iccl",
        "iccl",
        "icici clearing",
        "indian clearing",

        // Registrars
        "cams",
        "kfintech",
        "karvy",

        // Zerodha
        "zerodha clearing",
        "zerodha broking",
        "zerodha mf",
        "coin by zerodha"
    )

    /**
     * Detects INVESTMENT OUTFLOW at clearing / registrar layer.
     *
     * HARD RULES:
     * - BANK sender only
     * - DEBIT only
     * - Amount threshold
     * - Explicit entity match
     * - No wallet / POS / card
     */
    fun isClearingEntityInvestment(
        senderType: SenderType,
        txType: String?,          // 👈 EXPLICIT
        body: String,
        amount: Double
    ): Boolean {

        // 🔒 Direction guard (MOST IMPORTANT)
        if (txType != "DEBIT") return false

        // 🔒 Sender guard
        if (senderType != SenderType.BANK) return false

        // 🔒 Amount guard
        if (amount < MIN_INVESTMENT_AMOUNT) return false

        val lower = body.lowercase()

        // 🔒 Strong exclusions
        if (lower.contains("wallet")) return false
        if (lower.contains("pos")) return false
        if (lower.contains("card")) return false
        if (lower.contains("refund")) return false
        if (lower.contains("reversal")) return false

        // ✅ Explicit clearing / registrar entity match
        return CLEARING_ENTITIES.any { entity ->
            lower.contains(entity)
        }
    }
}

