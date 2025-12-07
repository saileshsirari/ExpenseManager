package com.spendwise.feature.smsimport

import java.util.regex.Pattern

object SmsParser {

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
     * All other logic (type, merchant, category) is handled by the ML pipeline.
     */
    fun parseAmount(body: String): Double? {
        val text = body.replace("\n", " ")

        // 1. Strong match first — with INR / Rs / ₹
        val m1 = AMOUNT_REGEX.matcher(text)
        if (m1.find()) {
            return m1.group(1)
                .replace(",", "")
                .toDoubleOrNull()
        }

        // 2. Fallback numeric match — try to extract the most likely amount
        val m2 = AMOUNT_NUMERIC.matcher(text)
        var firstMatch: String? = null

        while (m2.find()) {
            val candidate = m2.group(1)

            // Clean commas
            val numeric = candidate.replace(",", "").toDoubleOrNull() ?: continue

            // Reject impossible values (OTP, txn IDs)
            if (numeric > 1_00_00_000) continue  // >1 crore = unlikely amount
            if (numeric < 1) continue           // Reject 0, 1, 2, 3 (OTP-like)

            // Keep the first realistic amount
            firstMatch = candidate
            break
        }

        return firstMatch
            ?.replace(",", "")
            ?.toDoubleOrNull()
    }
}
