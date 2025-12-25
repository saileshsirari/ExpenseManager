package com.spendwise.core.com.spendwise.core

 fun isCardBillPayment(b: String): Boolean {
    return listOf(
        "credit card bill",
        "card bill payment",
        "bbps",
        "bharat bill payment",
        "statement payment",
        "autopay"
    ).any { it in b }
}

 fun isCreditCardSpend(text: String?): Boolean {
    if (text == null) return false
    val b = text.lowercase()

    val cardIndicators = listOf(
        "credit card",
        "debit card",
        "card x",
        "card xx",
        "card ending"
    )

    val spendIndicators = listOf(
        "spent",
        "purchase",
        "used at",
        "pos"
    )

    val billIndicators = listOf(
        "card bill",
        "credit card bill",
        "cc bill",
        "statement",
        "payment received",
        "autopay"
    )

    return cardIndicators.any { it in b } &&
            spendIndicators.any { it in b } &&
            billIndicators.none { it in b }
}

 fun isCreditCardSpend1(text: String?): Boolean {
    if (text == null) return false
    val b = text.lowercase()

    // Generic card identifiers (bank-agnostic)
    val cardIndicators = listOf(
        " bank card",
        " credit card",
        " debit card",
        " card x",
        " card xx",
        " card ending",
        " card *",
        " card ****"
    )

    // Spend indicators
    val spendIndicators = listOf(
        "spent",
        "purchase",
        "txn",
        "transaction",
        "used at",
        " at "
    )

    // Explicit exclusions (bill payments must stay)
    val billIndicators = listOf(
        "card bill",
        "credit card bill",
        "cc bill",
        "statement",
        "payment received",
        "autopay"
    )

    return cardIndicators.any { it in b } &&
            spendIndicators.any { it in b } &&
            billIndicators.none { it in b }
}

fun isWalletDeduction(text: String?): Boolean {
    if (text == null) return false
    val b = text.lowercase()

    if (isCreditCardSpend(b)) return false   // 🔒 must stay

    val walletKeywords = listOf(
        "payzapp",
        "paytm",
        "phonepe",
        "amazon pay",
        "amazonpay",
        "mobikwik",
        "freecharge",
        "airtel money",
        "jio money"
    )

    val strongSpendKeywords = listOf(
        "spent",
        "paid",
        "used",
        "deducted"
    )

    return walletKeywords.any { it in b } &&
            strongSpendKeywords.any { it in b }
}
