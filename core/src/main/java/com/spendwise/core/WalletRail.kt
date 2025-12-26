package com.spendwise.core.com.spendwise.core

enum class WalletRail {
    PHONEPE,
    OLAMONEY,
    PAYTM,
    AMAZONPAY,
    PAYZAPP,
    UNKNOWN
}
private fun extractWalletMerchant(body: String): String? {
    val lower = body.lowercase()
    if (!lower.contains(" wallet")) return null

    val patterns = listOf(
        // Prefer "on <Merchant>" first
        Regex("on ([a-zA-Z][a-zA-Z &.-]{2,40})", RegexOption.IGNORE_CASE),
        // Then "for <Merchant>" but NOT txn
        Regex("for (?!txn)([a-zA-Z][a-zA-Z &.-]{2,40})", RegexOption.IGNORE_CASE)
    )

    for (p in patterns) {
        val m = p.find(body)
        if (m != null) {
            return normalize(m.groupValues[1])
        }
    }
    return null
}
private fun normalize(raw: String): String {
    return raw
        .replace(Regex("[^a-zA-Z0-9 &.-]"), " ") // remove weird chars
        .replace(Regex("\\s+"), " ")             // collapse spaces
        .trim()
        .replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase() else c.toString()
        }
}


fun detectWalletRail(body: String?): WalletRail {
    if (body == null) return WalletRail.UNKNOWN
    val b = body.lowercase()

    return when {
        "via phonepe" in b || "phonepe wallet" in b ->
            WalletRail.PHONEPE

        "via olamoney" in b || "olamoney wallet" in b ->
            WalletRail.OLAMONEY

        "via paytm" in b || "paytm wallet" in b ->
            WalletRail.PAYTM

        "amazon pay" in b ->
            WalletRail.AMAZONPAY

        "payzapp" in b ->
            WalletRail.PAYZAPP

        else -> WalletRail.UNKNOWN
    }
}
