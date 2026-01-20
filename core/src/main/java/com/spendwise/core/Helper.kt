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

fun isPayZappWalletTopup(body: String?): Boolean {
    if (body == null) return false
    val b = body.lowercase()

    return (
            b.contains("payzappw") ||        // merchant code
                    b.contains("payzapp wallet")
            )
}

 fun isWalletCredit(body: String?): Boolean {
    if (body == null) return false
    val b = body.lowercase()

    val walletKeywords = listOf(
        "amazon pay",
        "amazonpay",
        "payzapp",
        "paytm",
        "phonepe",
        "mobikwik",
        "freecharge",
        "ola financial",
        "ola money"
    )

    val creditKeywords = listOf(
        "credited",
        "loaded",
        "added",
        "top up",
        "top-up",
        "balance"
    )

    return walletKeywords.any { it in b } &&
            creditKeywords.any { it in b }
}

 fun isBillPayment(body: String?): Boolean {
    if (body == null) return false
    val b = body.lowercase()

    val billKeywords = listOf(
        "bill paid",
        "billpay",
        "bill payment",
        "paid towards bill",
        "bill of rs",
        "bill ref",
        "bill no",
        "biller"
    )

    return billKeywords.any { it in b }
}

 fun isWalletAutoload(body: String?): Boolean {
    if (body == null) return false
    val b = body.lowercase()

    val mandateKeywords = listOf(
        "upi mandate",
        "mandate"
    )

    val autoloadKeywords = listOf(
        "wallet autoload",
        "wallet auto load",
        "autoload",
        "auto-load",
        "auto load"
    )

    return mandateKeywords.any { it in b } &&
            autoloadKeywords.any { it in b }
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

fun isWalletDeduction(text: String?): Boolean {
    if (text == null) return false
    val b = text.lowercase()

    // 🔒 Never treat card spends as wallet spends
    if (isCreditCardSpend(b)) return false

    val walletKeywords = listOf(
        "payzapp",
        "paytm",
        "amazon pay",
        "amazonpay",
        "mobikwik",
        "freecharge",
        "airtel money",
        "jio money",
        "phonepe"          // PhonePe is special
    )

    val strongSpendKeywords = listOf(
        "spent",
        "paid",
        "deducted",
        "used"
    )

    // ✅ Explicit wallet spend (Paytm / Amazon / etc.)
    if (walletKeywords.any { it in b } &&
        strongSpendKeywords.any { it in b }
    ) return true

    // ✅ PhonePe implicit wallet spend pattern
    if (
        "phonepe" in b &&
        (
                " paid " in b ||
                        " paid to " in b ||
                        " via phonepe" in b
                )
    ) return true

    return false
}

fun isSingleSmsInternalTransfer(body: String?): Boolean {
    if (body == null) return false

    val b = body.lowercase()

    // Must explicitly say both debit and credit
    val hasDebit = b.contains("debited") || b.contains("debit")
    val hasCredit = b.contains("credited") || b.contains("credit")

    if (!hasDebit || !hasCredit) return false

    // Exclude obvious merchant / wallet cases
    val excluded = listOf(
        "amazon",
        "flipkart",
        "swiggy",
        "zomato",
        "paytm",
        "phonepe",
        "google pay",
        "gpay",
        "wallet",
        "merchant"
    )

    return excluded.none { it in b }
}

fun isSystemInfoDebit(body: String?): Boolean {
    if (body == null) return false

    // Normalize aggressively (critical)
    val normalized = body
        .lowercase()
        .replace("[^a-z0-9]".toRegex(), "")

    // 1️⃣ Strong system routing markers (bank generated)
    val systemMarkers = listOf(
        "infobil",     // Info:BIL*
        "infoimps",    // Info:IMPS*
        "infoach",     // Info:ACH*
        "infortgs",    // Info:RTGS*
        "infoneft",    // Info:NEFT*
        "systemdebit",
        "autodebit"
    )

    if (systemMarkers.any { normalized.contains(it) }) {
        return true
    }

    // 2️⃣ Generic bank "info debit" pattern
    val hasInfo = normalized.contains("info")
    val hasDebit = normalized.contains("debit")
    val hasNoMerchant =
        !normalized.contains("paidto") &&
                !normalized.contains("merchant") &&
                !normalized.contains("purchase") &&
                !normalized.contains("order")

    return hasInfo && hasDebit && hasNoMerchant
}




