package com.spendwise.core.ml

object MerchantExtractorMl {

    // --------------------------------------------------------------------
    // 0. OTT Dictionary (NEW)
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

    // --------------------------------------------------------------------
    // Your existing merchant map
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
        "gpay" to "Google Pay",
        "google pay" to "Google Pay",
        "paytm" to "Paytm"
    )

    internal val merchantKeywords = merchantMap.keys


    // --------------------------------------------------------------------
    // Person name matcher
    // --------------------------------------------------------------------
    private val personNameRegex = Regex(
        "\\bto ([A-Z][a-z]+(?: [A-Z][a-z]+)*)",
        RegexOption.IGNORE_CASE
    )

    // Strong HDFC/POS "at XYZ" detector (NEW)
    private val posAtPattern =
        Regex("""\bat\s+([A-Za-z0-9&\-\.\s]{2,50})""", RegexOption.IGNORE_CASE)

    private val cityTokens = listOf(
        "india","bangalore","bengaluru","mumbai","delhi",
        "chennai","hyderabad","pune","kolkata","ncr","noida","gurgaon"
    )

    // --------------------------------------------------------------------
    // MAIN EXTRACTION
    // --------------------------------------------------------------------
    suspend fun extract(
        senderType: SenderType,
        sender: String,
        body: String,
        overrideProvider: suspend (String) -> String?
    ): String? {

        val lowerBody = body.lowercase()
        val upperSender = sender.uppercase()

        // 1. User override
        overrideProvider("merchant:$sender")?.let { return it }


        // ----------------------------------------------------------------
        // 2. OTT merchants (NEW)
        // ----------------------------------------------------------------
        ottMerchants.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) {
                overrideProvider("merchant:$pretty")?.let { return it }
                return pretty
            }
        }


        // ----------------------------------------------------------------
        // 3. Person name UPI: "To Manish Kumar"
        // ----------------------------------------------------------------
        val personMatch = personNameRegex.find(lowerBody)
        if (personMatch != null) {
            val rawName = personMatch.groupValues[1].trim()

            if (!merchantMap.keys.any { rawName.lowercase().contains(it) }) {
                val norm = normalize(rawName)
                overrideProvider("merchant:$norm")?.let { return it }
                return norm.uppercase()
            }
        }


        // ----------------------------------------------------------------
        // 4. Strong POS detection: "at XYZ Store"
        // ----------------------------------------------------------------
        val posMatch = posAtPattern.find(lowerBody)
        if (posMatch != null) {
            var candidate = posMatch.groupValues[1].trim()

            // Remove city suffixes
            val words = candidate.split(" ").filter { it.isNotBlank() }.toMutableList()
            while (words.size > 1 && cityTokens.contains(words.last().lowercase())) {
                words.removeAt(words.lastIndex)
            }
            candidate = words.joinToString(" ")

            if (candidate.isNotBlank()) {
                val norm = normalize(candidate)
                overrideProvider("merchant:$norm")?.let { return it }
                return norm
            }
        }


        // ----------------------------------------------------------------
        // 5. Merchant sender "IM-AMAZONPAY" → AMAZONPAY
        // ----------------------------------------------------------------
        if (senderType == SenderType.MERCHANT) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }


        // ----------------------------------------------------------------
        // 6. Keyword search in body (your original logic)
        // ----------------------------------------------------------------
        merchantMap.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) {
                overrideProvider("merchant:$pretty")?.let { return it }
                return pretty
            }
        }


        // ----------------------------------------------------------------
        // 7. Razorpay refined extract (improved)
        // ----------------------------------------------------------------
        if (lowerBody.contains("razorpay")) {
            val raw = lowerBody.substringAfter("razorpay")
                .replace("via", "")
                .replace("by", "")
                .trim()

            extractFirstWord(raw)?.let { merchant ->
                val norm = normalize(merchant)
                overrideProvider("merchant:$norm")?.let { return it }
                return norm
            }
        }


        // ----------------------------------------------------------------
        // 8. UPI merchants: "paid to", "sent to", "payment to"
        // ----------------------------------------------------------------
        val upiKeys = listOf("paid to", "sent to", "payment to")
        for (key in upiKeys) {
            if (lowerBody.contains(key)) {
                val tail = lowerBody.substringAfter(key).trim()
                extractFirstWord(tail)?.let { merchant ->
                    val norm = normalize(merchant)
                    overrideProvider("merchant:$norm")?.let { return it }
                    return norm
                }
            }
        }


        // ----------------------------------------------------------------
        // 9. BANK fallback: return cleaned sender name
        // ----------------------------------------------------------------
        if (senderType == SenderType.BANK) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }

        return null
    }


    // ========================================================================
    // HELPERS
    // ========================================================================

    private fun extractFirstWord(text: String): String? =
        text.split(" ", ",", ".", "(", "\n")
            .firstOrNull { it.isNotBlank() }
            ?.trim()

    private fun normalize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { it.uppercase() }

    private fun cleanSenderName(sender: String): String {
        val core = sender.split("-", " ").lastOrNull() ?: sender
        return core.replace(Regex("[^A-Za-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .uppercase()
    }
}
