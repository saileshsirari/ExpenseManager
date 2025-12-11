package com.spendwise.core.ml

object MerchantExtractorMl {

    // --------------------------------------------------------------------
    // SPECIAL MERCHANT SENDERS (fixes Airtel issue)
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
        "google *youtube" to "YouTube",
        "spotify" to "Spotify",
        "amazon prime" to "Amazon Prime",
        "prime video" to "Prime Video",
        "hotstar" to "Hotstar",
        "disney+ hotstar" to "Hotstar",
        "sonyliv" to "SonyLiv",
        "apple.com/bill" to "Apple Services"
    )

    // your original merchant map
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
        "gpay" to "Google Pay",
        "google pay" to "Google Pay",
        "paytm" to "Paytm"
    )
    internal val merchantKeywords = merchantMap.keys

    // --------------------------------------------------------------------
    // ROBUST PERSON NAME DETECTOR (NEW)
    // SBI, HDFC, ICICI internal transfers, UPI transfers
    // --------------------------------------------------------------------
    private val personPatterns = listOf(
        Regex(
            "to\\s+(mr\\.?|mrs\\.?|ms\\.?|shri)?\\s*([A-Za-z][A-Za-z ]{2,50})",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "transfer(?:red)?\\s+to\\s+(mr\\.?|mrs\\.?|ms\\.?|shri)?\\s*([A-Za-z][A-Za-z ]{2,50})",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "deposit by transfer from\\s+(mr\\.?|mrs\\.?|ms\\.?|shri)?\\s*([A-Za-z][A-Za-z ]{2,50})",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "credited(?:.*)from\\s+(mr\\.?|mrs\\.?|ms\\.?|shri)?\\s*([A-Za-z][A-Za-z ]{2,50})",
            RegexOption.IGNORE_CASE
        )
    )

    private fun extractPersonName(body: String): String? {
        val lower = body.lowercase()

        for (pattern in personPatterns) {
            val m = pattern.find(lower) ?: continue

            val raw = m.groupValues.last().trim()

            val cleaned = raw
                .replace("mr.", "", true)
                .replace("mr", "", true)
                .replace("mrs.", "", true)
                .replace("mrs", "", true)
                .replace("ms.", "", true)
                .replace("ms", "", true)
                .replace("shri", "", true)
                .trim()

            val parts = cleaned
                .split(" ")
                .map { it.replace("[^A-Za-z]".toRegex(), "") }
                .filter { it.length > 1 }

            if (parts.isEmpty()) return null

            return parts.joinToString(" ") { it.uppercase() }
        }

        return null
    }

    // --------------------------------------------------------------------
    // POS Detector
    // --------------------------------------------------------------------
    private val posAtPattern =
        Regex("""\bat\s+([A-Za-z0-9&\-\.\s]{2,50})""", RegexOption.IGNORE_CASE)

    private val cityTokens = listOf(
        "india", "bangalore", "bengaluru", "mumbai", "delhi",
        "chennai", "hyderabad", "pune", "kolkata", "ncr", "noida", "gurgaon"
    )

    // --------------------------------------------------------------------
    // MAIN LOGIC
    // --------------------------------------------------------------------
    suspend fun extract(
        senderType: SenderType,
        sender: String,
        body: String,
        overrideProvider: suspend (String) -> String?
    ): String? {

        val lowerBody = body.lowercase()
        val upperSender = sender.uppercase()

        // 1) User override
        overrideProvider("merchant:$sender")?.let { return it }

        // 2) SPECIAL MERCHANT SENDERS
        merchantSenderMap.forEach { (key, pretty) ->
            if (upperSender.contains(key)) {
                overrideProvider("merchant:$pretty")?.let { return it }
                return pretty
            }
        }

        // 3) OTT (Netflix, Prime etc.)
        ottMerchants.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) return pretty
        }

        // 4) PERSON NAME DETECTOR (NEW & IMPORTANT)
        extractPersonName(body)?.let { person ->
            val norm = normalize(person)
            overrideProvider("merchant:$norm")?.let { return it }
            return norm
        }

        // 5) Strong POS detection "at XYZ store"
        val posMatch = posAtPattern.find(lowerBody)
        if (posMatch != null) {
            var candidate = posMatch.groupValues[1].trim()
            val words = candidate.split(" ").filter { it.isNotBlank() }.toMutableList()

            while (words.size > 1 && cityTokens.contains(words.last().lowercase())) {
                words.removeAt(words.lastIndex)
            }

            val norm = normalize(words.joinToString(" "))
            return norm
        }

        // 6) Keyword merchant detection
        merchantMap.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) return pretty
        }

        // 7) Razorpay special-case
        if (lowerBody.contains("razorpay")) return "Razorpay"

        // 8) BANK fallback
        if (senderType == SenderType.BANK) {
            return cleanSenderName(sender)
        }

        return null
    }

    // --------------------------------------------------------------------
    // HELPERS
    // --------------------------------------------------------------------
    fun normalize(name: String): String =
        name.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")

    private fun cleanSenderName(sender: String): String {
        val parts = sender.split("-", " ", "_")
            .filter { it.isNotBlank() }

        // Example: JM-HDFCBK-S
        // parts = ["JM", "HDFCBK", "S"]

        // Prefer the middle segment when available
        val merchantPart = when {
            parts.size >= 2 -> parts[parts.size - 2]  // middle
            else -> parts.last()
        }

        return merchantPart
            .replace(Regex("[^A-Za-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .uppercase()
    }

}
