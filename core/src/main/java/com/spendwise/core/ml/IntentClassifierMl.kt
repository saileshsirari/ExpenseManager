package com.spendwise.core.ml

object IntentClassifierMl {

    private val ignoreKeywords = listOf(
        "will be debited", "will be deducted", "payment due",
        "bill due", "due date", "upcoming", "alert",
        "auto debit", "auto-debit", "scheduled"
    )

    // Words indicating a credit card PAYMENT RECEIVED (credit)
    private val creditCardReceiptKeywords = listOf(
        "payment has been received",
        "has been received",
        "payment of rs",
        "payment received",
        "received on your credit card",
        "credited to card",
        "received towards your credit card",
        "payment posted",
        "bill paid successfully",
        "payment updated against your"
    )

    fun classify(senderType: SenderType, body: String): IntentType {
        val b = body.lowercase()

        // --------------------------------------------------
        // IGNORE patterns → non-transactional
        // --------------------------------------------------
        if (ignoreKeywords.any { b.contains(it) }) {
            return IntentType.IGNORE
        }

        // --------------------------------------------------
// CREDIT CARD STATEMENT / DUE (NOT A TRANSACTION)
// --------------------------------------------------
        if (
            b.contains("credit card") &&
            (
                    b.contains("statement") ||
                            b.contains("is due") ||
                            b.contains("due by") ||
                            b.contains("minimum due") ||
                            b.contains("total due")
                    )
        ) {
            return IntentType.IGNORE
        }


        // PAYMENT REMINDERS
        val reminderKeywords = listOf(
            "bill due", "bill payment due", "due on",
            "due date", "last date", "payment reminder",
            "ignore if paid", "overdue"
        )
        if (reminderKeywords.any { b.contains(it) }) {
            return IntentType.REMINDER
        }

        // OTP / security codes
        if (b.contains("otp") || b.contains("one time password")) {
            return IntentType.PROMO
        }

        // Balance inquiry
        if ((b.contains("balance is") || b.contains("available balance")) && !(
                    b.contains("paid") ||
                            b.contains("spent") ||
                            b.contains("debited") ||
                            b.contains("deducted")
                    )
        ) {
            return IntentType.BALANCE
        }

        // Pending transaction states
        if (
            b.contains("initiated") ||
            b.contains("request received") ||
            b.contains("scheduled") ||
            b.contains("processing")
        ) {
            return IntentType.PENDING
        }

        // --------------------------------------------------
        // CREDIT CARD PAYMENT RECEIVED
        // --------------------------------------------------
        if (creditCardReceiptKeywords.any { b.contains(it) }) {
            return IntentType.CREDIT
        }

        if (
            (b.contains("payment") && b.contains("received")) &&
            (b.contains("credit card") || b.contains("card xx") || b.contains("cc"))
        ) {
            return IntentType.CREDIT
        }

        if (b.contains("has been received") && b.contains("credit card")) {
            return IntentType.CREDIT
        }

        // --------------------------------------------------
        // NORMAL BANK DEBIT & CREDIT
        // --------------------------------------------------
        val isDebit = listOf(
            "debited", "debit of", "deducted", "deduct", "deduction",
            "withdrawn", "spent", "payment of", "paid towards",
            "pos transaction", "atm wdl", "atm withdrawal",
            "debited rs", "debited inr"
        ).any { b.contains(it) }

        val isCredit = listOf(
            "credited", "credit of", "received into", "received in your",
            "salary credited", "refund of"
        ).any { b.contains(it) }

        if (b.contains("credit card") && b.contains("payment") && b.contains("received")) {
            return IntentType.CREDIT
        }

        val hasUpi = b.contains("upi") || b.contains("@upi")

        if (isDebit) {
            return IntentType.DEBIT
        }

        if (isCredit) {
            return IntentType.CREDIT
        }

        // --------------------------------------------------
        // 🔑 WALLET / UPI / CONSUMER LANGUAGE DEBIT (PATCH)
        // --------------------------------------------------
        if (
            (b.contains(" paid ") || b.startsWith("paid ")) &&
            (b.contains("rs") || b.contains("inr"))
        ) {
            return IntentType.DEBIT
        }

        if (
            b.contains("you've paid") &&
            (b.contains("rs") || b.contains("inr"))
        ) {
            return IntentType.DEBIT
        }

        if (
            b.contains("sent to") &&
            (b.contains("rs") || b.contains("inr"))
        ) {
            return IntentType.DEBIT
        }

        if (
            hasUpi &&
            (b.contains("paid") || b.contains("sent")) &&
            (b.contains("rs") || b.contains("inr"))
        ) {
            return IntentType.DEBIT
        }

        // --------------------------------------------------
        // REFUND (late fallback)
        // --------------------------------------------------
        if (b.contains("refund")) {
            return IntentType.REFUND
        }

        if (senderType == SenderType.PROMOTIONAL) {
            return IntentType.PROMO
        }

        return IntentType.UNKNOWN
    }
}
