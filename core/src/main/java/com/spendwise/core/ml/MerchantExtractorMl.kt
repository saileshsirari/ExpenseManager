package com.spendwise.core.ml

import com.spendwise.core.Logger as Log

object MerchantExtractorMl {

    private const val TAG = "MerchantDebug"

    // --------------------------------------------------------------------
    // SPECIAL MERCHANT SENDERS
    // --------------------------------------------------------------------
    private val merchantSenderMap = mapOf(
        "AIRTEL" to "Airtel",
        "AIRTELPAY" to "Airtel",
        "VI" to "Vodafone Idea",
        "VODAFONE" to "Vodafone Idea",
        "JIO" to "Jio",
        "RELIANCEJIO" to "Jio"
    )

    // --------------------------------------------------------------------
    // OTT dictionary
    // --------------------------------------------------------------------
    private val ottMerchants = mapOf(
        "netflix" to "Netflix",
        "mubi" to "Mubi",
        "youtube" to "YouTube",
        "spotify" to "Spotify",
        "amazon prime" to "Amazon Prime",
        "prime video" to "Prime Video",
        "hotstar" to "Hotstar",
        "disney+ hotstar" to "Hotstar",
        "sonyliv" to "SonyLiv",
        "apple.com/bill" to "Apple Services"
    )

    // --------------------------------------------------------------------
    // Internal transfer markers (ICICI/HDFC)
    // --------------------------------------------------------------------
    private val internalMarkers = listOf(
        "infobil", "infoach", "infoimps", "infortgs"
    )

    // --------------------------------------------------------------------
    // Merchant keyword dictionary
    // --------------------------------------------------------------------
    internal val merchantMap = mapOf(
        "zomato" to "Zomato",
        "swiggy" to "Swiggy",
        "uber" to "Uber",
        "ola" to "Ola",
        "irctc" to "IRCTC",
        "amazon" to "Amazon",
        "flipkart" to "Flipkart",
        "myntra" to "Myntra",
        "bigbasket" to "BigBasket",
        "rapido" to "Rapido",
        "blinkit" to "Blinkit",
        "zepto" to "Zepto",
        "bookmyshow" to "BookMyShow",
        "dominos" to "Domino's",
        "pizza hut" to "Pizza Hut",
        "hpcl" to "HPCL",
        "bpcl" to "BPCL",
        "ioc" to "Indian Oil",
        "reliance" to "Reliance",
        "razorpay" to "Razorpay",
        "cashfree" to "Cashfree",
        "payu" to "PayU",
        "phonepe" to "PhonePe",
        "google pay" to "Google Pay",
        "paytm" to "Paytm"
    )
    internal val merchantKeywords = merchantMap.keys

    // --------------------------------------------------------------------
    // UPI / Person Name Detector
    // --------------------------------------------------------------------
    private val personNameRegex = Regex(
        "(to|from)\\s+(mr\\.?|mrs\\.?|ms\\.?|shri)?\\s*([A-Za-z][A-Za-z ]{2,50})",
        RegexOption.IGNORE_CASE
    )

    private fun extractPersonName(body: String): String? {
        val m = personNameRegex.find(body.lowercase()) ?: return null
        val raw = m.groupValues[3]

        val cleaned = raw
            .replace(Regex("[^A-Za-z ]"), "")
            .trim()

        if (cleaned.split(" ").size > 3) return null // avoid long garbage matches

        return cleaned.uppercase()
    }

    // --------------------------------------------------------------------
    // MAIN EXTRACTION LOGIC
    // --------------------------------------------------------------------
    suspend fun extract(
        senderType: SenderType,
        sender: String,
        body: String,
        overrideProvider: suspend (String) -> String?
    ): String? {

        val lower = body.lowercase()
        Log.d(TAG, "---- EXTRACT MERCHANT ----")
        Log.d(TAG, "Sender=$sender")
        Log.d(TAG, "Body=$body")

        // USER OVERRIDE 1: full sender
        overrideProvider("merchant:$sender")?.let { return it }

        // USER OVERRIDE 2: normalized sender
        overrideProvider("merchant:${normalize(sender)}")?.let { return it }

        // --------------------------------------------------------------------
        // INTERNAL TRANSFER MARKERS (ICICI/HDFC)
        // --------------------------------------------------------------------
        internalMarkers.forEach { marker ->
            if (lower.contains(marker)) {
                val name = marker.removePrefix("info").uppercase() + " TRANSFER"
                Log.d(TAG, "Internal transfer detected → $name")
                return name
            }
        }

        // --------------------------------------------------------------------
        // CREDIT CARD PAYMENT RECEIVED (CREDIT)
        // --------------------------------------------------------------------
        if (
            lower.contains("credit card") &&
            lower.contains("payment") &&
            lower.contains("received")
        ) {
            val issuer = detectIssuer(sender, body)
            Log.d(TAG, "Credit card payment detected → $issuer")
            return issuer
        }

        // --------------------------------------------------------------------
        // CREDIT CARD BILL PAYMENT (DEBIT)
        // "Acct debited... Amazon Pay credited"
        // Should return issuer, not Amazon
        // --------------------------------------------------------------------
        if (
            lower.contains("credit card") &&
            (lower.contains("payment") || lower.contains("bill"))
        ) {
            val issuer = detectIssuer(sender, body)
            Log.d(TAG, "Credit card bill-payment detected → $issuer")
            return issuer
        }

        // --------------------------------------------------------------------
        // SPECIAL MERCHANT SENDERS (Airtel/Jio)
        // --------------------------------------------------------------------
        merchantSenderMap.forEach { (key, pretty) ->
            if (sender.uppercase().contains(key)) {
                Log.d(TAG, "Special sender hit → $pretty")
                return pretty
            }
        }

        // --------------------------------------------------------------------
        // OTT merchants (Netflix, Prime…)
        // --------------------------------------------------------------------
        ottMerchants.forEach { (token, pretty) ->
            if (lower.contains(token)) {
                Log.d(TAG, "OTT detected → $pretty")
                return pretty
            }
        }

        // --------------------------------------------------------------------
        // PERSON NAME DETECTOR (UPI)
        // --------------------------------------------------------------------
        val person = extractPersonName(body)
        if (person != null) {
            Log.d(TAG, "Person-detected merchant → $person")
            return person
        }

        // --------------------------------------------------------------------
        // POS detector (HDFC retail POS)
        // --------------------------------------------------------------------
        val posMatch = Regex("at ([A-Za-z0-9 ][A-Za-z0-9 &.-]{2,40})").find(lower)
        if (posMatch != null) {
            val norm = normalize(posMatch.groupValues[1])
            Log.d(TAG, "POS merchant → $norm")
            return norm
        }

        // --------------------------------------------------------------------
        // STANDARD KEYWORD MERCHANTS
        // --------------------------------------------------------------------
        merchantMap.forEach { (token, pretty) ->
            if (lower.contains(token)) {
                Log.d(TAG, "Keyword merchant → $pretty")
                return pretty
            }
        }

        // --------------------------------------------------------------------
        // FALLBACK BANK SENDER CLEANING
        // --------------------------------------------------------------------
        if (senderType == SenderType.BANK) {
            val cleaned = cleanSenderName(sender)
            if (cleaned.length > 2) {
                Log.d(TAG, "BANK fallback merchant → $cleaned")
                return cleaned
            }
        }

        Log.d(TAG, "Merchant not identified.")
        return null
    }

    // --------------------------------------------------------------------
    // HELPERS
    // --------------------------------------------------------------------
    private fun detectIssuer(sender: String, body: String): String {
        val t = (sender + body).lowercase()
        return when {
            "icici" in t -> "ICICI Credit Card"
            "hdfc" in t -> "HDFC Credit Card"
            "sbi" in t -> "SBI Credit Card"
            "axis" in t -> "Axis Credit Card"
            "kotak" in t -> "Kotak Credit Card"
            else -> "Credit Card"
        }
    }

    fun normalize(name: String): String =
        name.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun cleanSenderName(sender: String): String {
        val parts = sender.split("-", " ", "_")
            .filter { it.isNotBlank() }

        val chosen = when {
            parts.size >= 2 -> parts[parts.size - 2]
            else -> parts.last()
        }

        return chosen.replace("[^A-Za-z0-9 ]".toRegex(), "")
            .uppercase()
    }
}
