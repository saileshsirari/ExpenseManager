package com.spendwise.core.ml

object IntentClassifierMl {

    // SMS patterns that are not real transactions
    private val ignoreKeywords = listOf(
        "will be debited",
        "will be deducted",
        "payment due",
        "bill due",
        "due date",
        "upcoming",
        "alert",
        "auto debit",
        "auto-debit",
        "scheduled"
    )

    private val reminderKeywords = listOf(
        "bill due",
        "payment due",
        "bill payment due",
        "due on",
        "due date",
        "last date",
        "payment reminder",
        "ignore if paid",
        "overdue"
    )

    fun classify(senderType: SenderType, body: String): IntentType {
        val b = body.lowercase()

        // -------------------------------------------------------
        // 1. Ignore patterns
        // -------------------------------------------------------
        if (ignoreKeywords.any { b.contains(it) }) return IntentType.IGNORE
        if (reminderKeywords.any { b.contains(it) }) return IntentType.REMINDER

        // OTP
        if (b.contains("otp") || b.contains("one time password")) {
            return IntentType.PROMO
        }

        // Balance info
        if (b.contains("balance is") || b.contains("available balance")) {
            return IntentType.BALANCE
        }

        // Pending / initiated / processing
        if (
            b.contains("initiated") ||
            b.contains("request received") ||
            b.contains("scheduled") ||
            b.contains("processing")
        ) {
            return IntentType.PENDING
        }

        // =======================================================
        // 2. SPECIAL SAFETY RULE FOR MERCHANT SENDERS
        // Airtel / Jio / Vi confirmations SHOULD NOT BE DEBIT
        // =======================================================
        if (senderType == SenderType.MERCHANT) {

            // Common bill-payment confirmations
            val merchantInfoKeywords = listOf(
                "payment received",
                "received the payment",
                "payment of rs",
                "payment is updated",
                "payment updated",
                "thank you",
                "e-receipt",
                "receipt"
            )

            if (merchantInfoKeywords.any { b.contains(it) }) {
                return IntentType.CREDIT   // or INFO; CREDIT works best in your pipeline
            }

            // Merchant messages cannot be debit unless explicit debit words appear
            val explicitDebitWords = listOf("debited", "deducted", "charged")
            if (!explicitDebitWords.any { b.contains(it) }) {
                return IntentType.UNKNOWN
            }
        }

        // =======================================================
        // 3. CORE DEBIT / CREDIT DETECTION
        // =======================================================
        val isDebit = listOf(
            "debited", "debit of", "deducted", "deduction",
            "withdrawn", "spent",
            "payment of", "paid towards", "pos transaction",
            "atm wdl", "atm withdrawal"
        ).any { b.contains(it) }

        val isCredit = listOf(
            "credited", "credit of",
            "received into", "received in your",
            "salary credited",
            "refund of"
        ).any { b.contains(it) }

        val hasUpi = b.contains("upi") || b.contains("@upi")

        // UPI typically → debit, but avoid merchant confirmations
        if (isDebit || (hasUpi && senderType != SenderType.PROMOTIONAL)) {
            return IntentType.DEBIT
        }

        if (isCredit) return IntentType.CREDIT

        if (b.contains("refund")) return IntentType.REFUND

        if (senderType == SenderType.PROMOTIONAL) {
            return IntentType.PROMO
        }

        return IntentType.UNKNOWN
    }
}
