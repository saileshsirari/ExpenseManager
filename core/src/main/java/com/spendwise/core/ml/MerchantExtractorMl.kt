package com.spendwise.core.ml

import com.spendwise.core.Logger as Log

object MerchantExtractorMl {

    private const val TAG = "MerchantDebug"
    private val knownWallets = mapOf(
        "payzapp" to "PayZapp Wallet",
        "amazon pay" to "Amazon Pay Wallet",
        "phonepe" to "PhonePe Wallet",
        "paytm" to "Paytm Wallet",
        "mobikwik" to "MobiKwik Wallet"
    )


    private val personStopWords = setOf(
        "LIMITED", "LTD", "PRIVATE", "PVT", "LLP",
        "ON", "OF", "FOR", "AND",
        // 🔒 FD / FINANCIAL TERMS (NOT PEOPLE)
        "fd",
        "fd no",
        "fixed",
        "fixed deposit",
        "term",
        "term deposit",
        "deposit",
        "interest",
        "maturity",
        "loan",
        "emi",
        "installment",
        "reinvestment",
        "renewal"

    )


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

    private fun normalizeFinalMerchantName(input: String): String {
        // 1️⃣ Remove trailing numbers (SWIGGY123 → SWIGGY)
        var name = input.replace(Regex("\\d+$"), "")

        // 2️⃣ Normalize spacing
        name = name.replace(Regex("\\s+"), " ").trim()

        // 3️⃣ Smart title case (preserve acronyms)
        name = smartTitleCase(name)

        // 4️⃣ Limit to max 2 words
        val parts = name.split(" ").filter { it.isNotBlank() }
        if (parts.size > 2) {
            name = parts.take(2).joinToString(" ")
        }

        return name
    }


    fun extractPersonName(body: String): String? {

        // 1️⃣ Strongest: credited-person (bank formats)
        extractCreditedPerson(body)?.let { return it }

        // 2️⃣ Fallback: to/from person (UPI, IMPS, NEFT)
        extractToFromPerson(body)?.let { return it }

        return null
    }
    private val toFromRegex = Regex(
        "(to|from)\\s+(mr\\.?|mrs\\.?|ms\\.?|shri)?\\s*([A-Za-z][A-Za-z ]{2,50})",
        RegexOption.IGNORE_CASE
    )

    private fun extractToFromPerson(body: String): String? {
        val m = toFromRegex.find(body) ?: return null
        val raw = m.groupValues[3]

        val cleaned = raw
            .replace(Regex("[^A-Za-z ]"), "")
            .trim()

        if (cleaned.split(" ").size > 3) return null

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
// WALLET SPEND (PhonePe / OlaMoney / Paytm etc.)
// Explicit: "via <wallet> wallet for/on <merchant>"
// --------------------------------------------------------------------

        // 🔑 Wallet spend override
        val walletMerchant = extractWalletMerchant(body)
        if (walletMerchant != null) {
            Log.d(TAG, "Wallet spend merchant → $walletMerchant")
            return normalizeFinalMerchantName(walletMerchant)
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
        var skipPersonDetection  =false
        // --------------------------------------------------------------------
        val isCardSpend =
            lower.contains(" card ") ||
                    lower.contains(" credit card ") ||
                    lower.contains(" debit card ")

        if (isCardSpend) {
            // 🔒 Persons are impossible in card spends
            skipPersonDetection = true
        }

        // UPI P2P — "<NAME> credited"
// ------------------------------------------------------------
        if (!skipPersonDetection) {
            // 1️⃣ STRONGEST: "<NAME> credited"
            extractCreditedPerson(body)?.let {
                Log.d(TAG, "UPI credited person → $it")
                return it
            }

            // 2️⃣ "To <NAME>"
            extractToPerson(body)?.let {
                Log.d(TAG, "UPI to-person → $it")
                return it
            }

            // 3️⃣ Generic fallback
            if (!lower.contains(" wallet")) {
                extractGenericPerson(body)?.let {
                    Log.d(TAG, "UPI generic person → $it")
                    return it
                }
            }
        }


        // --------------------------------------------------------------------
        // POS detector — skip for wallet spends
        if (!lower.contains(" wallet")) {
            val posMatch = Regex(
                "at\\s+([A-Za-z0-9 _&-]{2,50}?)(?=\\.{1,2}|\\s+on\\s|\\s+bal\\s|\\s+rs\\.|$)",
                RegexOption.IGNORE_CASE
            ).find(body)

            if (posMatch != null) {

                val cleaned = normalize(posMatch.groupValues[1])
                val pretty = smartTitleCase(cleaned)
                Log.d(TAG, "POS merchant → $cleaned")
                return normalizeFinalMerchantName(
                    cleanTrailingNoise(
                        stripGatewayTokens(pretty)
                    )
                )
            }
        }




        // ------------------------------------------------------------



// SECONDARY "on <MERCHANT>." detector (non-POS)
        Regex(
            "on\\s+([A-Z][A-Z0-9 &-]{2,50})(?=\\.)"
        ).find(body)?.let {
            val cleaned = normalize(it.groupValues[1])
            val pretty = smartTitleCase(cleaned)
            val finalName = cleanTrailingNoise(pretty)
            Log.d(TAG, "ON-merchant → $finalName")
            return normalizeFinalMerchantName(finalName)
        }

        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        // STANDARD KEYWORD MERCHANTS
        // --------------------------------------------------------------------
        merchantMap.forEach { (token, pretty) ->
            if (lower.contains(token)) {
                Log.d(TAG, "Keyword merchant → $pretty")
                return normalizeFinalMerchantName(pretty)
            }
        }
// WALLET SELF TRANSACTION (balance / deduction)
        extractWalletSelf(body)?.let {
            Log.d(TAG, "Wallet self transaction → $it")
            return it
        }


        // FALLBACK BANK SENDER CLEANING
        // --------------------------------------------------------------------
        if (senderType == SenderType.BANK) {
            val cleaned = cleanSenderName(sender)
            if (cleaned.length > 2) {
                Log.d(TAG, "BANK fallback merchant → $cleaned")
                return normalizeFinalMerchantName(cleaned)

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
    private fun splitCamelCasePreserveAcronyms(input: String): String {
        // Do not touch all-uppercase words (IRCTC, HDFC, UPI)
        if (input.all { it.isUpperCase() || !it.isLetter() }) {
            return input
        }

        // Insert space between lowerCase → UpperCase boundaries
        return input.replace(
            Regex("([a-z])([A-Z])"),
            "$1 $2"
        )
    }

    private fun extractWalletSelf(body: String): String? {
        val lower = body.lowercase()
        knownWallets.forEach { (token, pretty) ->
            if (
                lower.contains(token) &&
                lower.contains(" wallet")
            ) {
                return normalizeFinalMerchantName(pretty)
            }
        }
        return null
    }


    fun normalize(raw: String): String {
        return raw
            .replace(Regex("[^A-Za-z0-9 &-]"), " ") // remove junk, KEEP CASE
            .replace(Regex("\\s+"), " ")             // collapse spaces
            .replace("\r\n", "\n")
            .trim()
    }


    private fun extractToPerson(body: String): String? {
            val regex = Regex(
                "(?im)^\\s*to\\s+([A-Z][A-Z ]{2,40})\\b"
            )
        val match = regex.find(body) ?: return null
        val raw = match.groupValues[1]
        val cleaned = normalize(raw)
        val words = cleaned.split(" ")
        if (words.any { it.uppercase() in personStopWords }) {
            return null
        }

        return titleCaseName(
            normalize(raw)
        )
    }

    fun extractCreditedPerson(body: String): String? {
        // Matches: "; SHAHEED CHAMAN  credited"
        val regex = Regex(
            "[;\\n]\\s*([A-Z][A-Z ]{2,40})\\s*credited\\b",
            RegexOption.IGNORE_CASE
        )

        val match = regex.find(body) ?: return null

        val raw = match.groupValues[1].trim()
        val cleaned = normalize(raw)
        val words = cleaned.split(" ")
        if (words.any { it.uppercase() in personStopWords }) {
            return null
        }

        return cleanTrailingNoise(
            titleCaseName(normalize(raw))
        )

    }
    fun titleCaseName(input: String): String {
        return input
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") {
                it.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
    }
    private val gatewayTokens = setOf(
        "CYBS", "SI", "RZRPAY", "RAZORPAY", "PAYU", "JUSPAY"
    )

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

    // --------------------------------------------------------------------
// WALLET SPEND — extract real merchant AFTER "for"/"on"
// Examples:
// "via PhonePe wallet for Wangzom Garments"
// "via OlaMoney Wallet ... on OlaCabs"
// --------------------------------------------------------------------
    fun extractWalletMerchant(body: String?): String? {
        if (body == null) return null

        val lower = body.lowercase()
        if (!lower.contains(" wallet")) return null

        // Strong signal: "on <Merchant>."
        Regex(
            "on\\s+([A-Za-z][A-Za-z &-]{2,40}?)(?=\\.)",
            RegexOption.IGNORE_CASE
        ).find(body)?.let {
            return splitCamelCasePreserveAcronyms(
                normalize(it.groupValues[1])
            )
        }

        // Fallback: "for <Merchant>." but NOT txn
        Regex(
            "for\\s+(?!txn)([A-Za-z][A-Za-z &-]{2,40}?)(?=\\.)",
            RegexOption.IGNORE_CASE
        ).find(body)?.let {
            return splitCamelCasePreserveAcronyms(
                normalize(it.groupValues[1])
            )
        }

        return null
    }


    private val acronyms = setOf(
        "UPI", "IRCTC", "SBI", "HDFC", "ICICI",
        "BBPS", "POS", "ATM", "HPCL", "BPCL", "IOCL"
    )

    private fun smartTitleCase(input: String): String {
        return input
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                val raw = word.trim()

                when {
                    raw.uppercase() in acronyms ->
                        raw.uppercase()   // IRCTC, SBI

                    else ->
                        raw.lowercase()
                            .replaceFirstChar { it.uppercase() } // Swiggy, Amazon
                }
            }
    }


    private fun stripGatewayTokens(input: String): String {
        return input
            .split(" ")
            .filterNot { it.uppercase() in gatewayTokens }
            .joinToString(" ")
            .trim()
    }

    private val trailingNoiseTokens = setOf(
        "O", "P", "G", "E"
    )

    private val routingPairs = setOf(
        "IN", "UP", "DL", "MH", "KA", "TN"
    )

    private fun cleanTrailingNoise(input: String): String {
        val parts = input.split(" ").toMutableList()

        while (parts.isNotEmpty()) {

            // Rule 1: routing pairs at end (IN G, IN E, UP P, etc.)
            if (
                parts.size >= 2 &&
                parts[parts.size - 2].uppercase() in routingPairs &&
                parts.last().uppercase().length == 1
            ) {
                parts.removeAt(parts.lastIndex)       // remove G / E / P
                parts.removeAt(parts.lastIndex)       // remove IN / UP
                continue
            }

            val last = parts.last().uppercase()

            // Rule 2: single-letter noise
            if (last.length == 1 && last in trailingNoiseTokens) {
                parts.removeAt(parts.lastIndex)
                continue
            }

            break
        }

        return parts.joinToString(" ")
    }

    private fun extractGenericPerson(body: String): String? {
        val regex = Regex(
            "(?i)(?:to|from)\\s+([A-Z][A-Z ]{2,40})"
        )

        val match = regex.find(body) ?: return null
        val raw = match.groupValues[1]

        val cleaned = normalize(raw)
        val words = cleaned.split(" ")

        // Guardrails
        if (words.any { it.uppercase() in personStopWords }) return null
        if (words.size > 3) return null
        if (cleaned.contains("BANK")) return null
        if (cleaned.contains("CARD")) return null

        return cleanTrailingNoise(
            titleCaseName(cleaned)
        )
    }

}


