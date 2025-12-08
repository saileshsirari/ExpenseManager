package com.spendwise.feature.smsimport

import java.util.regex.Pattern

object SmsParser {

    // Messages to reject BEFORE extracting amount
    private val REJECT_KEYWORDS = listOf(
        // Failures
        "failed", "fail", "declined", "unsuccessful", "not completed",
        "not processed", "could not be processed", "insufficient balance",
        "transaction could not", "reversal", "reversed", "reversal posted",
        "cancelled", "canceled",

        // Future refunds (not actual credit)
        "will be refunded", "will get refunded", "refund will be",
        "be refunded", "refunded in", "refund will be processed",
        "amount will be reversed", "will be reversed",

        // Bill reminders
        "due on", "bill due", "bill payment reminder", "last date",
        "due date", "expiry", "auto-debit", "auto debit", "autodebit",
        "payment reminder",

        // Non-transaction / promo
        "offer", "reward", "promo", "promotion", "bonus points",
        "cashback credited to wallet terms apply"
    )

    // Matches: Rs 1,234.56 / INR 234 / ₹500
    private val AMOUNT_REGEX = Pattern.compile(
        "(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    // Fallback numeric match: 1234, 1,234.00, 500.50
    private val AMOUNT_NUMERIC = Pattern.compile(
        "\\b(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)\\b"
    )

    /**
     * Extracts ONLY the transaction AMOUNT.
     *
     * Anything filtered here will not go into ML,
     * preventing irrelevant SMS from being classified.
     */
    fun parseAmount(body: String): Double? {
        val lower = body.lowercase()

        // 1️⃣ Reject messages that are not actual transactions
        if (REJECT_KEYWORDS.any { lower.contains(it) }) {
            return null
        }

        val text = body.replace("\n", " ")

        // 2️⃣ Strong match first — with INR / Rs / ₹
        val m1 = AMOUNT_REGEX.matcher(text)
        if (m1.find()) {
            return m1.group(1)
                .replace(",", "")
                .toDoubleOrNull()
        }

        // 3️⃣ Fallback numeric match — try to extract the most likely amount
        val m2 = AMOUNT_NUMERIC.matcher(text)
        var firstMatch: String? = null

        while (m2.find()) {
            val candidate = m2.group(1)

            // Clean commas
            val numeric = candidate.replace(",", "").toDoubleOrNull() ?: continue

            // Reject impossible values (OTP, txn IDs)
            if (numeric > 1_00_00_000) continue  // >1 crore = unlikely amount
            if (numeric < 1) continue           // Reject 0–1–2–3 values (OTP-like)

            // Keep first realistic amount
            firstMatch = candidate
            break
        }

        return firstMatch
            ?.replace(",", "")
            ?.toDoubleOrNull()
    }
}
