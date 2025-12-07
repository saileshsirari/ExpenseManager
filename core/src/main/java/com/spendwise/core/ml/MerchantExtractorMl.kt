package com.spendwise.core.ml

object MerchantExtractorMl {

    private val merchantMap = mapOf(
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

        // new additions
        "razorpay" to "Razorpay",
        "cashfree" to "Cashfree",
        "payu" to "PayU",
        "phonepe" to "PhonePe",
        "gpay" to "Google Pay",
        "google pay" to "Google Pay",
        "paytm" to "Paytm"
    )

    /**
     * Merchant detection engine:
     * 1. Apply user overrides
     * 2. Sender-based merchant inference
     * 3. Known merchant keyword lookup
     * 4. UPI merchant patterns
     * 5. Razorpay merchant extraction
     * 6. Fallback rules
     */
    suspend fun extract(
        senderType: SenderType,
        sender: String,
        body: String,
        overrideProvider: suspend (String) -> String?
    ): String? {

        val lowerBody = body.lowercase()

        // ---------------------------------------------------------------
        // 1. OVERRIDE CHECK — strongest signal
        // ---------------------------------------------------------------
        // override for this specific SMS sender
        overrideProvider("merchant:$sender")?.let { return it }

        // override for mapped merchant (if found later)
        // (we check again at end)

        // ---------------------------------------------------------------
        // 2. If sender is merchant-like (ex: IM-AMAZONPAY) use sender
        // ---------------------------------------------------------------
        if (senderType == SenderType.MERCHANT) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }

        // ---------------------------------------------------------------
        // 3. Keyword lookup inside body (fast, reliable)
        // ---------------------------------------------------------------
        merchantMap.forEach { (token, pretty) ->
            if (lowerBody.contains(token)) {
                overrideProvider("merchant:$pretty")?.let { return it }
                return pretty
            }
        }

        // ---------------------------------------------------------------
        // 4. Razorpay merchant parsing
        // Most UPI merchants come via Razorpay:
        // "Payment to ABC STORE via Razorpay"
        // "Razorpay: XYZ Traders"
        // ---------------------------------------------------------------
        if (lowerBody.contains("razorpay")) {
            val idx = lowerBody.indexOf("razorpay")
            val tail = lowerBody.substring(idx)
                .replace("razorpay", "")
                .replace("via", "")
                .replace("by", "")
                .trim()

            val merchant = extractFirstWord(tail)
            if (!merchant.isNullOrBlank()) {
                val normalized = normalizeName(merchant)
                overrideProvider("merchant:$normalized")?.let { return it }
                return normalized
            }
        }

        // ---------------------------------------------------------------
        // 5. UPI merchant: "paid to X", "payment to X"
        // ---------------------------------------------------------------
        val upiKeys = listOf("paid to", "sent to", "payment to", "to ")
        for (key in upiKeys) {
            val idx = lowerBody.indexOf(key)
            if (idx != -1) {
                val tail = lowerBody.substring(idx + key.length).trim()
                val merchant = extractFirstWord(tail)
                if (!merchant.isNullOrBlank()) {
                    val normalized = normalizeName(merchant)
                    overrideProvider("merchant:$normalized")?.let { return it }
                    return normalized
                }
            }
        }

        // ---------------------------------------------------------------
        // 6. POS / Card merchant: "at <merchant>"
        // ---------------------------------------------------------------
        val atIdx = lowerBody.indexOf(" at ")
        if (atIdx != -1) {
            val tail = lowerBody.substring(atIdx + 4)
            val merchant = extractUntilStopChar(tail)
            if (!merchant.isNullOrBlank()) {
                val normalized = normalizeName(merchant)
                overrideProvider("merchant:$normalized")?.let { return it }
                return normalized
            }
        }

        // ---------------------------------------------------------------
        // 7. If it's bank sender, fallback to bank name
        // ---------------------------------------------------------------
        if (senderType == SenderType.BANK) {
            val cleaned = cleanSenderName(sender)
            overrideProvider("merchant:$cleaned")?.let { return it }
            return cleaned
        }

        // ---------------------------------------------------------------
        // 8. Nothing found
        // ---------------------------------------------------------------
        return null
    }

    // ================================================================
    // Helper Functions
    // ================================================================

    private fun extractFirstWord(text: String): String? {
        return text.split(" ", ",", ".", "(", "\n")
            .firstOrNull { it.isNotBlank() }
            ?.trim()
    }

    private fun extractUntilStopChar(text: String): String {
        return text.takeWhile { it != '.' && it != ',' && it != '(' && it != '\n' }
            .trim()
    }

    private fun normalizeName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9 ]"), "")
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private fun cleanSenderName(sender: String): String {
        // removes "VM-" / "IM-" prefixes
        val parts = sender.split("-", " ")
        val core = parts.lastOrNull() ?: sender
        return core.replace(Regex("[^A-Za-z0-9 ]"), "").uppercase()
    }
}
