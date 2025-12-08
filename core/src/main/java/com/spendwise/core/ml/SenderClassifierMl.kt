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
