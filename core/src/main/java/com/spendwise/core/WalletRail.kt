package com.spendwise.core.com.spendwise.core

enum class WalletRail {
    PHONEPE,
    OLAMONEY,
    PAYTM,
    AMAZONPAY,
    PAYZAPP,
    UNKNOWN
}
 fun extractWalletMerchant(body: String?): String? {
    if (body == null) return null
    val lower = body.lowercase()

    // Only wallet spends
    if (!lower.contains(" wallet")) return null

    val patterns = listOf(
        Regex("for ([a-zA-Z0-9 &.-]{2,40})", RegexOption.IGNORE_CASE),
        Regex("on ([a-zA-Z0-9 &.-]{2,40})", RegexOption.IGNORE_CASE)
    )

    for (p in patterns) {
        val m = p.find(body)
        if (m != null) {
            return m.groupValues[1].trim()
                .replaceFirstChar { it.uppercase() }
        }
    }
    return null
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
