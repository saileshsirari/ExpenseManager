package com.spendwise.core.ml

object IntentClassifierMl {
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
    fun classify(senderType: SenderType, body: String): IntentType {
        val b = body.lowercase()
// If contains ignore keyword → not an actual txn
        if (ignoreKeywords.any { b.contains(it) }) {
            return IntentType.IGNORE
        }
        // Hard rejects: clearly non-transactional
        val reminderKeywords = listOf(
            "bill", "due on", "due date", "last date",
            "payment reminder", "pay now", "ignore if paid", "overdue"
        )
        if (reminderKeywords.any { b.contains(it) }) {
            return IntentType.REMINDER
        }

        if (b.contains("otp") || b.contains("one time password")) {
            return IntentType.PROMO
        }

        if (b.contains("balance is") || b.contains("available balance")) {
            return IntentType.BALANCE
        }

        if (b.contains("initiated") || b.contains("request received")
            || b.contains("scheduled") || b.contains("processing")
        ) {
            return IntentType.PENDING
        }

        // Core txn detection
        val isDebit = listOf(
            "debited", "debit of", "withdrawn", "spent",
            "payment of", "paid towards", "pos transaction", "atm wdl", "atm withdrawal"
        ).any { b.contains(it) }

        val isCredit = listOf(
            "credited", "credit of", "received into", "received in your",
            "salary credited", "refund of"
        ).any { b.contains(it) }

        // Wallet / UPI transactions often look like debit
        val hasUpi = b.contains("upi") || b.contains("@upi")

        if (isDebit || (hasUpi && senderType != SenderType.PROMOTIONAL)) {
            return IntentType.DEBIT
        }

        if (isCredit) {
            return IntentType.CREDIT
        }

        // Refunds (no amount parse yet)
        if (b.contains("refund")) {
            return IntentType.REFUND
        }

        if (senderType == SenderType.PROMOTIONAL) {
            return IntentType.PROMO
        }

        return IntentType.UNKNOWN
    }
}
