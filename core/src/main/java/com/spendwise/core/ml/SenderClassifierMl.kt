package com.spendwise.core.ml

object SenderClassifierMl {

    private val bankTokens = listOf(
        "hdfc", "hdfcbk", "icici", "icicibank", "sbi", "sbin", "cbssbi",
        "axis", "axisbk", "kotak", "kotakb", "idfc", "idfcfb",
        "pnb", "boi", "canara", "union", "indusind", "yesbank", "federal", "aubank"
    )

    private val walletTokens = listOf(
        "paytm", "phonepe", "gpay", "googlepay", "freecharge", "mobikwik"
    )

    private val merchantTokens = listOf(
        "amazon", "amzn", "flipkart", "fkrt", "myntra", "swiggy", "zomato",
        "ola", "uber", "rapido", "irctc", "bookmyshow", "bbdaily", "bigbasket",
        "blinkit", "zepto", "jio", "airtel", "vi ", "vodafone", "tata sky", "sun direct"
    )

        // existing code stays exactly as-is …

        /**
         * Returns a normalized bank identifier if sender is a bank.
         * Used for USER_SELF patterns and safety guards.
         */
        fun extractBankName(sender: String): String? {
            val s = sender.lowercase()

            return when {
                s.contains("icici") -> "ICICI"
                s.contains("sbi") || s.contains("cbssbi") -> "SBI"
                s.contains("hdfc") -> "HDFC"
                s.contains("axis") -> "AXIS"
                s.contains("kotak") -> "KOTAK"
                s.contains("idfc") -> "IDFC"
                s.contains("pnb") -> "PNB"
                s.contains("boi") -> "BOI"
                s.contains("canara") || s.contains("cnrb") -> "CANARA"
                s.contains("union") -> "UNION"
                s.contains("indusind") -> "INDUSIND"
                s.contains("yes") -> "YES"
                s.contains("federal") -> "FEDERAL"
                s.contains("aubank") -> "AU"
                else -> null
            }
        }

    fun classify(sender: String, body: String): SenderType {
        val s = sender.lowercase()
        val b = body.lowercase()

        // 1. If sender looks like a mobile number → likely personal / OTP / promo
        val isNumeric = s.all { it.isDigit() }
        if (isNumeric && s.length in 8..12) {
            if (b.contains("otp") || b.contains("one time password")) {
                return SenderType.PROMOTIONAL
            }
            return SenderType.PERSONAL
        }

        // 2. Banks
        if (bankTokens.any { s.contains(it) }) return SenderType.BANK

        // 3. Wallets
        if (walletTokens.any { s.contains(it) }) return SenderType.WALLET

        // 4. Merchants
        if (merchantTokens.any { s.contains(it) }) return SenderType.MERCHANT

        // 5. Promos (short codes with offers)
        if (b.contains("offer") || b.contains("cashback") || b.contains("sale")) {
            return SenderType.PROMOTIONAL
        }

        // fallback
        return SenderType.UNKNOWN
    }
}
