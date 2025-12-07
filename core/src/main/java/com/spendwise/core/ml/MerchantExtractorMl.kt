package com.spendwise.core.ml

object MerchantExtractorMl {

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

    private val personNameRegex = Regex(
        "\\bto ([A-Z][a-z]+(?: [A-Z][a-z]+)*)",
        RegexOption.IGNORE_CASE
    )

    suspend fun extract(
        senderType: SenderType,
        sender: String,
        body: String,
        overrideProvider: suspend (String) -> String?
    ): String? {

        val lowerBody = body.lowercase()

        // --------------------------------------------------------------------
        // 1. User override (strongest rule)
        // --------------------------------------------------------------------
        overrideProvider("merchant:$sender")?.let { return it }

        // --------------------------------------------------------------------
        // 2. Detect personal UPI transfers → "To MANISH KUMAR"
        // --------------------------------------------------------------------
        val personMatch = personNameRegex.find(lowerBody)
        if (personMatch != null) {
            val rawName = personMatch.groupValues[1].trim()

            if (!merchantMap.keys.any { rawName.lowercase().contains(it) }) {
                val norm = normalize(rawName)
                overrideProvider("merchant:$norm")?.let { return it }
                return norm.uppercase()
            }
        }

        // --------------------------------------------------------------------
        // 3. **Strong POS detection rule (NEW)**
        //    If SMS says "At <merchant>" → use it (even for BANK senders)
        // --------------------------------------------------------------------
        val atIdx = lowerBody.indexOf(" at ")
        if (atIdx != -1) {
            val merchantCandidate = extractUntilStopChar(lowerBody.substring(atIdx + 4))

            if (!merchantCandidate.isNullOrBlank()) {
                val norm = normalize(merchantCandidate)

                // Avoid accidental bank names
                val bankTokens = listOf("hdfc", "sbi", "icici", "axis", "kotak")
                if (!bankTokens.any { norm.contains(it) }) {
                    overrideProvider("merchant:$norm")?.let { return it }
                    return norm
                }
            }
        }

        // --------------------------------------------------------------------
        // 4. If sender resembles merchant sender (IM-AMAZONPAY)
        // --------------------------------------------------------------------
        if (senderType == SenderType.MERCHANT) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }

        // --------------------------------------------------------------------
        // 5. Keyword merchant detection inside SMS body
        // --------------------------------------------------------------------
        merchantMap.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) {
                overrideProvider("merchant:$pretty")?.let { return it }
                return pretty
            }
        }

        // --------------------------------------------------------------------
        // 6. Razorpay merchant extraction
        // --------------------------------------------------------------------
        if (lowerBody.contains("razorpay")) {
            val idx = lowerBody.indexOf("razorpay")
            val tail = lowerBody.substring(idx)
                .replace("razorpay", "")
                .replace("via", "")
                .replace("by", "")
                .trim()

            val merchant = extractFirstWord(tail)
            if (!merchant.isNullOrBlank()) {
                val norm = normalize(merchant)
                overrideProvider("merchant:$norm")?.let { return it }
                return norm
            }
        }

        // --------------------------------------------------------------------
        // 7. UPI merchant: "paid to X", "sent to X"
        // --------------------------------------------------------------------
        val upiKeys = listOf("paid to", "sent to", "payment to")
        for (key in upiKeys) {
            val idx = lowerBody.indexOf(key)
            if (idx != -1) {
                val merchant = extractFirstWord(lowerBody.substring(idx + key.length).trim())
                if (!merchant.isNullOrBlank()) {
                    val norm = normalize(merchant)
                    overrideProvider("merchant:$norm")?.let { return it }
                    return norm
                }
            }
        }

        // --------------------------------------------------------------------
        // 8. Fallback for BANK sender
        // --------------------------------------------------------------------
        if (senderType == SenderType.BANK) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }

        return null
    }


    // ============================== HELPERS ==============================

    private fun extractFirstWord(text: String): String? =
        text.split(" ", ",", ".", "(", "\n")
            .firstOrNull { it.isNotBlank() }
            ?.trim()

    private fun extractUntilStopChar(text: String): String =
        text.takeWhile { it !in listOf('.', ',', '(', '\n') }.trim()

    private fun normalize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 ]"), "")
            .replaceFirstChar { it.uppercase() }

    private fun cleanSenderName(sender: String): String {
        val core = sender.split("-", " ").lastOrNull() ?: sender
        return core.replace(Regex("[^A-Za-z0-9 ]"), "").uppercase()
    }
}
