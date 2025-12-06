
package com.spendwise.feature.smsimport

import java.util.regex.Pattern
import kotlin.math.abs

data class ParsedTx(val amount: Double, val currency: String, val type: String?, val merchant: String?)

object SmsParser {
    private val AMOUNT_REGEX = Pattern.compile("(?:(?:Rs\\.?|INR|₹)\\s?)([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
    private val AMOUNT_NUMERIC = Pattern.compile("\\b(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)\\b")

    private val DEBIT_WORDS = listOf("debited","payment","paid","purchase","spent","withdrawn","sent","successfully paid","txn")
    private val CREDIT_WORDS = listOf("credited","received","deposit","refund","cashback","credited to your account")
    private val UPI_WORDS = listOf("upi","vpa","paid to","paid via","via pay","via phonepe","gpay","google pay","phonepe","paytm")

    private val MERCHANTS = mapOf(
        "zomato" to "Zomato",
        "swiggy" to "Swiggy",
        "paytm" to "Paytm",
        "amazon" to "Amazon",
        "flipkart" to "Flipkart",
        "uber" to "Uber",
        "ola" to "Ola",
        "mcdonald" to "McDonald's"
    )

    fun parse(body: String): ParsedTx? {
        val amount = extractAmount(body) ?: return null
        val lower = body.lowercase()

        val type = when {
            DEBIT_WORDS.any { lower.contains(it) } -> "DEBIT"
            CREDIT_WORDS.any { lower.contains(it) } -> "CREDIT"
            UPI_WORDS.any { lower.contains(it) } -> "UPI"
            else -> null
        }

        val merchant = MERCHANTS.entries.firstOrNull { lower.contains(it.key) }?.value ?: detectMerchantByHeuristics(lower)
        return ParsedTx(amount = amount, currency = "INR", type = type, merchant = merchant)
    }

    private fun extractAmount(text: String): Double? {
        var m = AMOUNT_REGEX.matcher(text)
        if (m.find()) {
            val s = m.group(1).replace(",","")
            return s.toDoubleOrNull()
        }
        m = AMOUNT_NUMERIC.matcher(text)
        var best: String? = null
        while (m.find()) {
            val candidate = m.group(1)
            if (candidate.length >= 2 && candidate.length <= 12) {
                if (abs(candidate.toDoubleOrNull() ?: 0.0) > 1000 && candidate.length==4) continue
                best = candidate; break
            }
        }
        return best?.replace(",","")?.toDoubleOrNull()
    }

    private fun detectMerchantByHeuristics(lower: String): String? {
        val atIdx = lower.indexOf(" at ")
        if (atIdx != -1) {
            val sub = lower.substring(atIdx + 4).split(' ',',')[0]
            if (sub.length >= 3) return sub.replaceFirstChar { it.uppercase() }
        }
        val toIdx = lower.indexOf(" to ")
        if (toIdx != -1) {
            val sub = lower.substring(toIdx + 4).split(' ',',')[0]
            if (sub.length >= 3) return sub.replaceFirstChar { it.uppercase() }
        }
        return null
    }
}
