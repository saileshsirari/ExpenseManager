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
        "petrol pump" to "Petrol Pump",
        "hpcl" to "HPCL",
        "bpcl" to "BPCL",
        "ioc" to "Indian Oil",
        "hp petrol" to "HP Petrol",
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

    // Detect personal names like "To MANISH KUMAR"
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

        // ---------------------------------------------------------------
        // 1. User overrides ALWAYS win
        // ---------------------------------------------------------------
        overrideProvider("merchant:$sender")?.let { return it }

        // ---------------------------------------------------------------
        // 2. Detect personal UPI transfers: "To MANISH KUMAR"
        // ---------------------------------------------------------------
        val personMatch = personNameRegex.find(lowerBody)
        if (personMatch != null) {
            val rawName = personMatch.groupValues[1].trim()

            // Avoid false matches against merchant keywords
            if (!merchantMap.keys.any { rawName.lowercase().contains(it) }) {
                val normalized = normalizeName(rawName)
                overrideProvider("merchant:$normalized")?.let { return it }
                return normalized.uppercase()
            }
        }

        // ---------------------------------------------------------------
        // 3. If sender looks like known merchant sender
        // ---------------------------------------------------------------
        if (senderType == SenderType.MERCHANT) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }

        // ---------------------------------------------------------------
        // 4. Keyword lookup inside SMS body
        // ---------------------------------------------------------------
        merchantMap.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) {
                overrideProvider("merchant:$pretty")?.let { return it }
                return pretty
            }
        }

        // ---------------------------------------------------------------
        // 5. Razorpay merchant extraction
        // ---------------------------------------------------------------
        if (lowerBody.contains("razorpay")) {
            val cleaned = extractFirstWord(
                lowerBody.substring(lowerBody.indexOf("razorpay"))
                    .replace("razorpay", "")
                    .replace("via", "")
                    .replace("by", "")
                    .trim()
            )

            if (!cleaned.isNullOrBlank()) {
                val normalized = normalizeName(cleaned)
                overrideProvider("merchant:$normalized")?.let { return it }
                return normalized
            }
        }

        // ---------------------------------------------------------------
        // 6. UPI merchant pattern detection
        // ---------------------------------------------------------------
        val upiKeys = listOf("paid to", "sent to", "payment to", "to ")
        for (key in upiKeys) {
            val idx = lowerBody.indexOf(key)
            if (idx != -1) {
                val merchant = extractFirstWord(lowerBody.substring(idx + key.length).trim())
                if (!merchant.isNullOrBlank()) {
                    val normalized = normalizeName(merchant)
                    overrideProvider("merchant:$normalized")?.let { return it }
                    return normalized
                }
            }
        }

        // ---------------------------------------------------------------
        // 7. POS / Card merchant detection: "at <merchant>"
        // ---------------------------------------------------------------
        val atIdx = lowerBody.indexOf(" at ")
        if (atIdx != -1) {
            val merchant = extractUntilStopChar(lowerBody.substring(atIdx + 4))
            if (!merchant.isNullOrBlank()) {
                val normalized = normalizeName(merchant)
                overrideProvider("merchant:$normalized")?.let { return it }
                return normalized
            }
        }

        // ---------------------------------------------------------------
        // 8. Fallback: bank sender → show bank name
        // ---------------------------------------------------------------
        if (senderType == SenderType.BANK) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }

        return null
    }

    // Helpers

    private fun extractFirstWord(text: String): String? =
        text.split(" ", ",", ".", "(", "\n")
            .firstOrNull { it.isNotBlank() }
            ?.trim()

    private fun extractUntilStopChar(text: String): String =
        text.takeWhile { it !in listOf('.', ',', '(', '\n') }.trim()

    private fun normalizeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 ]"), "")
            .replaceFirstChar { it.uppercase() }

    private fun cleanSenderName(sender: String): String {
        val core = sender.split("-", " ").lastOrNull() ?: sender
        return core.replace(Regex("[^A-Za-z0-9 ]"), "").uppercase()
    }
}
