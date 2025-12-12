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
        "payment updated against your" // common Airtel/utility phrasing
    )

    fun classify(senderType: SenderType, body: String): IntentType {
        val b = body.lowercase()

        // IGNORE patterns → non-transactional
        if (ignoreKeywords.any { b.contains(it) }) {
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
        if (b.contains("balance is") || b.contains("available balance")) {
            return IntentType.BALANCE
        }

        // Pending transaction states
        if (b.contains("initiated") || b.contains("request received")
            || b.contains("scheduled") || b.contains("processing")
        ) {
            return IntentType.PENDING
        }

        // -------------------------------------------------------------------
        //  CREDIT CARD PAYMENT RECEIVED  (Fix for CC receipts)
        // -------------------------------------------------------------------
        if (creditCardReceiptKeywords.any { b.contains(it) }) {
            return IntentType.CREDIT
        }

        // Sometimes banks say:
        // “Payment received on your CC XXXX” or “Thank you! we received your payment”
        if ((b.contains("payment") && b.contains("received"))
            && (b.contains("credit card") || b.contains("card xx") || b.contains("cc"))
        ) {
            return IntentType.CREDIT
        }

        // ICICI/HDFC style:
        // “Payment of Rs XXXX has been received on your ICICI Bank Credit Card”
        if (b.contains("has been received") && b.contains("credit card")) {
            return IntentType.CREDIT
        }

        // -------------------------------------------------------------------
        // NORMAL DEBIT & CREDIT CLASSIFICATION
        // -------------------------------------------------------------------
        val isDebit = listOf(
            "debited", "debit of", "deducted", "deduct", "deduction",
            "withdrawn", "spent", "payment of", "paid towards",
            "pos transaction", "atm wdl", "atm withdrawal", "debited rs", "debited inr"
        ).any { b.contains(it) }

        val isCredit = listOf(
            "credited", "credit of", "received into", "received in your",
            "salary credited", "refund of"
        ).any { b.contains(it) }

        // Special guard: phrases that strongly indicate credit-card payment receipt
        if (b.contains("credit card") && b.contains("payment") && b.contains("received")) {
            return IntentType.CREDIT
        }

        val hasUpi = b.contains("upi") || b.contains("@upi")

        if (isDebit || (hasUpi && senderType != SenderType.PROMOTIONAL)) {
            return IntentType.DEBIT
        }

        if (isCredit) {
            return IntentType.CREDIT
        }

        if (b.contains("refund")) {
            return IntentType.REFUND
        }

        if (senderType == SenderType.PROMOTIONAL) {
            return IntentType.PROMO
        }

        return IntentType.UNKNOWN
    }
}
