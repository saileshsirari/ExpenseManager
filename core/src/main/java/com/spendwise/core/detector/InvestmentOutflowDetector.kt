
package com.spendwise.core.detector

object InvestmentOutflowDetector {

    private val PLATFORM_KEYWORDS = listOf(
        "groww",
        "zerodha",
        "upstox",
        "angel",
        "fyers",
        "coin",
        "et money",
        "etmoney",
        "paytm money",
        "kuvera",
        "indmoney",
        "smallcase",
        "wint",
        "goldenpi",
        "icicidirect",
        "hdfc sec",
        "kotak sec",
        "sharekhan"
    )

    private val GENERIC_KEYWORDS = listOf(
        "invest",
        "investment",
        "mf",
        "mutual",
        "sip",
        "etf",
        "stock",
        "equity",
        "demat",
        "broker",
        "fund",
        "securities",
        "amc",
        "bond",
        "gold"
    )

    fun isInvestmentOutflow(body: String): Boolean {
        val text = body.lowercase()

        return PLATFORM_KEYWORDS.any { text.contains(it) } ||
                GENERIC_KEYWORDS.any { text.contains(it) }
    }
}

