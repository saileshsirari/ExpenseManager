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
        "payu"     to "PayU",
        "phonepe"  to "PhonePe",
        "gpay"     to "Google Pay",
        "google pay" to "Google Pay",
        "paytm"     to "Paytm"
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

        // Normalize sender for override lookup
        val normalizedSender = normalize(sender)

        // ---------------------------------------------------------------
        // 1. USER OVERRIDE (strongest signal)
        // ---------------------------------------------------------------
        overrideProvider("merchant:$normalizedSender")?.let { return it }

        // ---------------------------------------------------------------
        // 2. Detect P2P transfers: "To MANISH KUMAR"
        // ---------------------------------------------------------------
        val personMatch = personNameRegex.find(lowerBody)
        if (personMatch != null) {
            val rawName = personMatch.groupValues[1].trim()
            val normalizedPerson = normalize(rawName)

            // Avoid accidentally matching keywords like Pizza → MANISH PIZZA
            if (!merchantKeywords.any { rawName.lowercase().contains(it) }) {

                // Check override for this person
                overrideProvider("merchant:$normalizedPerson")?.let { return it }

                return rawName.uppercase()
            }
        }

        // ---------------------------------------------------------------
        // 3. Merchant sender (VM-SWIGGY, IM-AMAZONPAY)
        // ---------------------------------------------------------------
        if (senderType == SenderType.MERCHANT) {
            val cleaned = cleanSenderName(sender)
            val normalized = normalize(cleaned)

            overrideProvider("merchant:$normalized")?.let { return it }
            return cleaned
        }

        // ---------------------------------------------------------------
        // 4. Keyword lookup matches inside SMS body
        // ---------------------------------------------------------------
        merchantMap.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) {
                val normalizedKey = normalize(pretty)
                overrideProvider("merchant:$normalizedKey")?.let { return it }
                return pretty
            }
        }

        // ---------------------------------------------------------------
        // 5. Razorpay merchant extraction
        // ---------------------------------------------------------------
        if (lowerBody.contains("razorpay")) {
            val index = lowerBody.indexOf("razorpay")
            val tail = lowerBody.substring(index)
                .replace("razorpay", "")
                .replace("via", "")
                .replace("by", "")
                .trim()

            val merchant = extractFirstWord(tail)
            if (!merchant.isNullOrBlank()) {
                val normalized = normalize(merchant)
                overrideProvider("merchant:$normalized")?.let { return it }
                return normalizedName(merchant)
            }
        }

        // ---------------------------------------------------------------
        // 6. UPI merchant extraction
        // ---------------------------------------------------------------
        val upiKeys = listOf("paid to", "sent to", "payment to", "to ")

        for (key in upiKeys) {
            val idx = lowerBody.indexOf(key)
            if (idx != -1) {
                val tail = lowerBody.substring(idx + key.length).trim()
                val merchant = extractFirstWord(tail)

                if (!merchant.isNullOrBlank()) {
                    val normalized = normalize(merchant)
                    overrideProvider("merchant:$normalized")?.let { return it }
                    return normalizedName(merchant)
                }
            }
        }

        // ---------------------------------------------------------------
        // 7. POS / Card Swipes → "at <store>"
        // ---------------------------------------------------------------
        val atIdx = lowerBody.indexOf(" at ")
        if (atIdx != -1) {
            val merchant = extractUntilStopChar(lowerBody.substring(atIdx + 4))
            if (!merchant.isNullOrBlank()) {
                val normalized = normalize(merchant)
                overrideProvider("merchant:$normalized")?.let { return it }
                return normalizedName(merchant)
            }
        }

        // ---------------------------------------------------------------
        // 8. BANK SENDER fallback
        // ---------------------------------------------------------------
        if (senderType == SenderType.BANK) {
            val cleaned = cleanSenderName(sender)
            val normalized = normalize(cleaned)

            overrideProvider("merchant:$normalized")?.let { return it }
            return cleaned
        }

        return null
    }

    // ---------------------------------------------------------------
    // Helper functions
    // ---------------------------------------------------------------

    private fun normalize(input: String): String =
        input.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun normalizedName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 ]"), "")
            .replaceFirstChar { it.uppercase() }

    private fun extractFirstWord(text: String): String? =
        text.split(" ", ",", ".", "(", "\n")
            .firstOrNull { it.isNotBlank() }
            ?.trim()

    private fun extractUntilStopChar(text: String): String =
        text.takeWhile { it !in listOf('.', ',', '(', '\n') }.trim()

    private fun cleanSenderName(sender: String): String {
        val core = sender.split("-", " ").lastOrNull() ?: sender
        return core.replace(Regex("[^A-Za-z0-9 ]"), "").uppercase()
    }
}
