package com.spendwise.core.ml

object CategoryClassifierMl {

    suspend  fun classify(
        merchant: String?,
        body: String,
        intentType: IntentType,
        overrideProvider: suspend (String) -> String?
    ): CategoryType {
        val m = merchant?.lowercase() ?: ""
        val b = body.lowercase()
// Category override
        if (merchant != null) {
            val override = overrideProvider("category:$merchant")
            if (override != null) return CategoryType.valueOf(override)
        }

        if (intentType == IntentType.CREDIT) {
            if (b.contains("salary") || b.contains("sal credited")) {
                return CategoryType.INCOME
            }
            if (b.contains("refund")) {
                return CategoryType.INCOME
            }
        }

        // Merchant-based rules
        if (listOf(
                "zomato",
                "swiggy",
                "domino",
                "pizza",
                "eat",
                "restaurant"
            ).any { m.contains(it) || b.contains(it) }
        ) {
            return CategoryType.FOOD
        }

        if (listOf(
                "uber",
                "ola",
                "rapido",
                "irctc",
                "train",
                "flight",
                "indigo",
                "vistara",
                "goair"
            ).any { m.contains(it) || b.contains(it) }
        ) {
            return CategoryType.TRAVEL
        }

        if (listOf(
                "amazon",
                "flipkart",
                "myntra",
                "bigbasket",
                "blinkit",
                "zepto"
            ).any { m.contains(it) || b.contains(it) }
        ) {
            return CategoryType.SHOPPING
        }

        if (listOf(
                "fuel",
                "petrol",
                "diesel",
                "hpcl",
                "bpcl",
                "ioc"
            ).any { b.contains(it) || m.contains(it) }
        ) {
            return CategoryType.FUEL
        }

        if (listOf(
                "electricity",
                "power",
                "bescom",
                "billdesk",
                "gas",
                "lpg"
            ).any { b.contains(it) }
        ) {
            return CategoryType.UTILITIES
        }

        if (listOf(
                "broadband",
                "fibre",
                "wifi",
                "internet",
                "act fibernet",
                "jiofiber"
            ).any { b.contains(it) }
        ) {
            return CategoryType.BILLS
        }

        if (listOf(
                "netflix",
                "hotstar",
                "sonyliv",
                "zee5",
                "spotify",
                "gaana",
                "wynk"
            ).any { b.contains(it) }
        ) {
            return CategoryType.ENTERTAINMENT
        }

        if (listOf(
                "hospital",
                "clinic",
                "pharma",
                "medic",
                "apollo",
                "max health"
            ).any { b.contains(it) }
        ) {
            return CategoryType.HEALTH
        }

        if (listOf("school", "college", "tuition", "coaching", "academy").any { b.contains(it) }) {
            return CategoryType.EDUCATION
        }

        if (b.contains("atm") || b.contains("cash withdrawal") || b.contains("atm wdl")) {
            return CategoryType.ATM_CASH
        }

        // Transfers: NEFT/IMPS/RTGS/UPI to self / others
        if (listOf("neft", "imps", "rtgs", "fund transfer", "transfer to").any { b.contains(it) }) {
            return CategoryType.TRANSFER
        }
        if (b.contains("upi") && (b.contains("@okicici") || b.contains("@ybl") || b.contains("@ibl") || b.contains(
                "@upi"
            ))
        ) {
            return CategoryType.TRANSFER
        }

        return CategoryType.OTHER
    }
}
