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

        // ------------------------------------------------------------
        // 1️⃣ USER OVERRIDE (highest priority)
        // ------------------------------------------------------------
        if (!merchant.isNullOrBlank()) {
            overrideProvider("category:$merchant")?.let {
                return CategoryType.valueOf(it)
            }
        }

        // ------------------------------------------------------------
        // 2️⃣ PERSON CATEGORY DETECTION
        // ------------------------------------------------------------

        // Case A: Merchant looks like a human name
        if (merchant != null && looksLikePersonName(merchant)) {
            // Salary should NOT be marked as PERSON
            if (!b.contains("salary") && !b.contains("credited")) {
                return CategoryType.PERSON
            }
        }

        // Case B: SMS says "Paid to <Person>"
        if (intentType == IntentType.DEBIT &&
            (b.contains("paid to") || b.contains("sent to") || b.contains("to "))
        ) {
            if (merchant != null && looksLikePersonName(merchant)) {
                return CategoryType.PERSON
            }
        }

        // ------------------------------------------------------------
        // 3️⃣ CREDITS = INCOME / REFUND
        // ------------------------------------------------------------
        if (intentType == IntentType.CREDIT) {

            // Salary deposits
            if (listOf("salary", "sal credited", "credited by employer", "nps", "pf payout")
                    .any { b.contains(it) }) {
                return CategoryType.INCOME
            }

            // Refunds & cashbacks
            if (listOf("refund", "reversal", "cashback", "reversed")
                    .any { b.contains(it) }) {
                return CategoryType.INCOME
            }

            // IMPS/NEFT incoming transfer → TRANSFER
            if (listOf("neft", "imps", "rtgs").any { b.contains(it) }) {
                return CategoryType.TRANSFER
            }
        }

        // ------------------------------------------------------------
        // 4️⃣ SUBSCRIPTIONS / OTT (NEW & IMPROVED)
        // ------------------------------------------------------------
        val ottKeywords = listOf(
            "netflix", "hotstar", "prime video", "amazon prime",
            "sonyliv", "zee5", "spotify", "gaana", "wynk",
            "youtube", "youtube premium", "apple.com/bill"
        )

        if (ottKeywords.any { m.contains(it) || b.contains(it) }) {
            return CategoryType.ENTERTAINMENT
        }

        // ------------------------------------------------------------
        // 5️⃣ FOOD
        // ------------------------------------------------------------
        if (listOf("zomato", "swiggy", "domino", "pizza", "eat", "restaurant", "food")
                .any { m.contains(it) || b.contains(it) }) {
            return CategoryType.FOOD
        }

        // ------------------------------------------------------------
        // 6️⃣ TRAVEL
        // ------------------------------------------------------------
        if (listOf(
                "uber", "ola", "rapido", "irctc", "train",
                "flight", "indigo", "vistara", "goair", "spicejet"
            ).any { m.contains(it) || b.contains(it) }) {

            return CategoryType.TRAVEL
        }

        // ------------------------------------------------------------
        // 7️⃣ SHOPPING / QUICK-COMMERCE
        // ------------------------------------------------------------
        if (listOf("amazon", "flipkart", "myntra", "bigbasket", "blinkit", "zepto", "ajio", "nykaa")
                .any { m.contains(it) || b.contains(it) }) {
            return CategoryType.SHOPPING
        }

        // ------------------------------------------------------------
        // 8️⃣ FUEL
        // ------------------------------------------------------------
        if (listOf("fuel", "petrol", "diesel", "hpcl", "bpcl", "ioc", "indian oil")
                .any { m.contains(it) || b.contains(it) }) {
            return CategoryType.FUEL
        }

        // ------------------------------------------------------------
        // 9️⃣ UTILITIES (Electricity, LPG, Water)
        // ------------------------------------------------------------
        if (listOf("electricity", "power", "billdesk", "bescom", "tneb", "mseb", "gas", "lpg", "bharat gas")
                .any { b.contains(it) }) {
            return CategoryType.UTILITIES
        }

        // ------------------------------------------------------------
        // 🔟 INTERNET/BROADBAND
        // ------------------------------------------------------------
        if (listOf("broadband", "fibre", "fiber", "wifi", "internet", "jiofiber", "act fibernet")
                .any { b.contains(it) }) {
            return CategoryType.BILLS
        }

        // ------------------------------------------------------------
        // 1️⃣1️⃣ HEALTH
        // ------------------------------------------------------------
        if (listOf("hospital", "clinic", "pharma", "medic", "apollo", "fortis", "max health", "diagnostic")
                .any { b.contains(it) }) {
            return CategoryType.HEALTH
        }

        // ------------------------------------------------------------
        // 1️⃣2️⃣ EDUCATION
        // ------------------------------------------------------------
        if (listOf("school", "college", "tuition", "coaching", "academy")
                .any { b.contains(it) }) {
            return CategoryType.EDUCATION
        }

        // ------------------------------------------------------------
        // 1️⃣3️⃣ ATM / CASH WITHDRAWAL
        // ------------------------------------------------------------
        if (listOf("atm", "cash withdrawal", "atm wdl")
                .any { b.contains(it) }) {
            return CategoryType.ATM_CASH
        }

        // ------------------------------------------------------------
        // 1️⃣4️⃣ TRANSFERS (UPI / IMPS / NEFT)
        // ------------------------------------------------------------
        if (listOf("transfer to", "fund transfer", "neft", "imps", "rtgs")
                .any { b.contains(it) }) {
            return CategoryType.TRANSFER
        }

        if (b.contains("upi") &&
            listOf("@okicici", "@ybl", "@ibl", "@upi", "@axl", "@okaxis", "@oksbi")
                .any { b.contains(it) }
        ) {
            return CategoryType.TRANSFER
        }

        // ------------------------------------------------------------
        // 1️⃣5️⃣ DEFAULT
        // ------------------------------------------------------------
        return CategoryType.OTHER
    }

    // --------------------------------------------------------------------
    // PERSON-NAME HEURISTIC (much improved)
    // --------------------------------------------------------------------
    private fun looksLikePersonName(name: String): Boolean {
        val cleaned = name.replace(Regex("[^A-Za-z ]"), "").trim()

        // Exclude known merchants
        if (MerchantExtractorMl.merchantKeywords.any { cleaned.lowercase().contains(it) })
            return false

        // Require actual alphabet characters
        if (cleaned.none { it.isLetter() }) return false

        val parts = cleaned.split(" ").filter { it.isNotBlank() }

        // Allow 1–3 names
        if (parts.size !in 1..3) return false

        // Each part must start with uppercase if it's a name
        if (parts.all { it.first().isUpperCase() && it.length >= 3 }) return true

        // Single names like "Ravi", "Arjun", "Meena"
        if (parts.size == 1 && cleaned.length in 3..14) return true

        return false
    }
}
