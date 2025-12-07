package com.spendwise.core.ml

object CategoryClassifierMl {

    suspend fun classify(
        merchant: String?,
        body: String,
        intentType: IntentType,
        overrideProvider: suspend (String) -> String?
    ): CategoryType {

        val m = merchant?.lowercase() ?: ""
        val b = body.lowercase()

        // 1️⃣ User-defined category override
        if (merchant != null) {
            val override = overrideProvider("category:$merchant")
            if (override != null) return CategoryType.valueOf(override)
        }

        // ------------------------------------------------------------
        // 2️⃣ PERSON detection (NEW FIX)
        // ------------------------------------------------------------
        // If merchant is a detected personal name → PERSON
        if (merchant != null && looksLikePersonName(merchant)) {
            return CategoryType.PERSON
        }

        // If SMS indicates a transfer "to <person>"
        if (intentType == IntentType.DEBIT &&
            (b.contains("to ") || b.contains("sent to") || b.contains("paid to"))
        ) {
            if (merchant != null && looksLikePersonName(merchant)) {
                return CategoryType.PERSON
            }
        }

        // ------------------------------------------------------------
        // 3️⃣ Incoming credits
        // ------------------------------------------------------------
        if (intentType == IntentType.CREDIT) {
            if (b.contains("salary") || b.contains("sal credited")) return CategoryType.INCOME
            if (b.contains("refund")) return CategoryType.INCOME
        }

        // ------------------------------------------------------------
        // 4️⃣ Merchant/body based rules (YOUR EXISTING LOGIC)
        // ------------------------------------------------------------
        if (listOf("zomato", "swiggy", "domino", "pizza", "eat", "restaurant")
                .any { m.contains(it) || b.contains(it) }
        ) return CategoryType.FOOD

        if (listOf("uber", "ola", "rapido", "irctc", "train", "flight", "indigo", "vistara", "goair")
                .any { m.contains(it) || b.contains(it) }
        ) return CategoryType.TRAVEL

        if (listOf("amazon", "flipkart", "myntra", "bigbasket", "blinkit", "zepto")
                .any { m.contains(it) || b.contains(it) }
        ) return CategoryType.SHOPPING

        if (listOf("fuel", "petrol", "diesel", "hpcl", "bpcl", "ioc")
                .any { m.contains(it) || b.contains(it) }
        ) return CategoryType.FUEL

        if (listOf("electricity", "power", "bescom", "billdesk", "gas", "lpg")
                .any { b.contains(it) }
        ) return CategoryType.UTILITIES

        if (listOf("broadband", "fibre", "wifi", "internet", "act fibernet", "jiofiber")
                .any { b.contains(it) }
        ) return CategoryType.BILLS

        if (listOf("netflix", "hotstar", "sonyliv", "zee5", "spotify", "gaana", "wynk")
                .any { b.contains(it) }
        ) return CategoryType.ENTERTAINMENT

        if (listOf("hospital", "clinic", "pharma", "medic", "apollo", "max health")
                .any { b.contains(it) }
        ) return CategoryType.HEALTH

        if (listOf("school", "college", "tuition", "coaching", "academy")
                .any { b.contains(it) }
        ) return CategoryType.EDUCATION

        if (b.contains("atm") || b.contains("cash withdrawal") || b.contains("atm wdl")) {
            return CategoryType.ATM_CASH
        }

        // Transfers: NEFT / IMPS / RTGS / UPI
        if (listOf("neft", "imps", "rtgs", "fund transfer", "transfer to").any { b.contains(it) }) {
            return CategoryType.TRANSFER
        }

        if (b.contains("upi") &&
            listOf("@okicici", "@ybl", "@ibl", "@upi").any { b.contains(it) }
        ) {
            return CategoryType.TRANSFER
        }

        // ------------------------------------------------------------
        // 5️⃣ Default
        // ------------------------------------------------------------
        return CategoryType.OTHER
    }

    // 🔍 Detect if merchant looks like a real person's name
    private fun looksLikePersonName(name: String): Boolean {
        val cleaned = name.replace(Regex("[^A-Za-z ]"), "").trim()

        // Exclude known merchants
        if (MerchantExtractorMl.merchantKeywords.any { cleaned.lowercase().contains(it) }) return false

        // Must be alphabetic
        if (cleaned.none { it.isLetter() }) return false

        // Must be 1–3 words
        val parts = cleaned.split(" ")
        if (parts.size !in 1..3) return false

        // Each part should start with uppercase (Manish Kumar)
        if (parts.all { it.firstOrNull()?.isUpperCase() == true && it.length >= 3 }) {
            return true
        }

        // Single name allowed: Ravi, Manish, Arjun
        if (parts.size == 1 && cleaned.length in 3..12) {
            return true
        }

        return false
    }
}
