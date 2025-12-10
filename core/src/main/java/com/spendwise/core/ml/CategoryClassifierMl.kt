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
        // ❌ REMOVED CATEGORY OVERRIDE
        //    This caused invalid values like "Amazon1" to crash the app.
        // ------------------------------------------------------------

        // ------------------------------------------------------------
        // 1️⃣ PERSON CATEGORY DETECTION
        // ------------------------------------------------------------
        if (merchant != null && looksLikePersonName(merchant)) {
            if (!b.contains("salary") && !b.contains("credited")) {
                return CategoryType.PERSON
            }
        }

        if (intentType == IntentType.DEBIT &&
            (b.contains("paid to") || b.contains("sent to") || b.contains("to "))
        ) {
            if (merchant != null && looksLikePersonName(merchant)) {
                return CategoryType.PERSON
            }
        }

        // ------------------------------------------------------------
        // 2️⃣ CREDITS = INCOME
        // ------------------------------------------------------------
        if (intentType == IntentType.CREDIT) {

            if (listOf("salary", "sal credited", "credited by employer", "nps", "pf payout")
                    .any { b.contains(it) }) {
                return CategoryType.INCOME
            }

            if (listOf("refund", "reversal", "cashback", "reversed")
                    .any { b.contains(it) }) {
                return CategoryType.INCOME
            }

            if (listOf("neft", "imps", "rtgs").any { b.contains(it) }) {
                return CategoryType.TRANSFER
            }
        }

        // ------------------------------------------------------------
        // 3️⃣ ENTERTAINMENT / OTT
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
        // 4️⃣ FOOD
        // ------------------------------------------------------------
        if (listOf("zomato", "swiggy", "domino", "pizza", "eat", "restaurant", "food")
                .any { m.contains(it) || b.contains(it) }) {
            return CategoryType.FOOD
        }

        // ------------------------------------------------------------
        // 5️⃣ TRAVEL
        // ------------------------------------------------------------
        if (listOf(
                "uber", "ola", "rapido", "irctc", "train",
                "flight", "indigo", "vistara", "goair", "spicejet"
            ).any { m.contains(it) || b.contains(it) }) {

            return CategoryType.TRAVEL
        }

        // ------------------------------------------------------------
        // 6️⃣ SHOPPING
        // ------------------------------------------------------------
        if (listOf("amazon", "flipkart", "myntra", "bigbasket", "blinkit", "zepto", "ajio", "nykaa")
                .any { m.contains(it) || b.contains(it) }) {
            return CategoryType.SHOPPING
        }

        // ------------------------------------------------------------
        // 7️⃣ FUEL
        // ------------------------------------------------------------
        if (listOf("fuel", "petrol", "diesel", "hpcl", "bpcl", "ioc", "indian oil")
                .any { m.contains(it) || b.contains(it) }) {
            return CategoryType.FUEL
        }

        // ------------------------------------------------------------
        // 8️⃣ UTILITIES
        // ------------------------------------------------------------
        if (listOf("electricity", "power", "billdesk", "bescom", "tneb", "mseb",
                "gas", "lpg", "bharat gas")
                .any { b.contains(it) }) {
            return CategoryType.UTILITIES
        }

        // ------------------------------------------------------------
        // 9️⃣ INTERNET/BROADBAND
        // ------------------------------------------------------------
        if (listOf("broadband", "fibre", "fiber", "wifi", "internet", "jiofiber", "act fibernet")
                .any { b.contains(it) }) {
            return CategoryType.BILLS
        }

        // ------------------------------------------------------------
        // 1️⃣0️⃣ HEALTH
        // ------------------------------------------------------------
        if (listOf("hospital", "clinic", "pharma", "medic", "apollo", "fortis", "max health", "diagnostic")
                .any { b.contains(it) }) {
            return CategoryType.HEALTH
        }

        // ------------------------------------------------------------
        // 1️⃣1️⃣ EDUCATION
        // ------------------------------------------------------------
        if (listOf("school", "college", "tuition", "coaching", "academy")
                .any { b.contains(it) }) {
            return CategoryType.EDUCATION
        }

        // ------------------------------------------------------------
        // 1️⃣2️⃣ ATM / CASH WITHDRAWAL
        // ------------------------------------------------------------
        if (listOf("atm", "cash withdrawal", "atm wdl")
                .any { b.contains(it) }) {
            return CategoryType.ATM_CASH
        }

        // ------------------------------------------------------------
        // 1️⃣3️⃣ TRANSFERS
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
        // 1️⃣4️⃣ DEFAULT
        // ------------------------------------------------------------
        return CategoryType.OTHER
    }


    // --------------------------------------------------------------------
    // PERSON DETECTION LOGIC
    // --------------------------------------------------------------------
    private fun looksLikePersonName(name: String): Boolean {
        val cleaned = name.replace(Regex("[^A-Za-z ]"), "").trim()

        if (MerchantExtractorMl.merchantKeywords.any { cleaned.lowercase().contains(it) })
            return false

        if (cleaned.none { it.isLetter() }) return false

        val parts = cleaned.split(" ").filter { it.isNotBlank() }

        if (parts.size !in 1..3) return false

        if (parts.all { it.first().isUpperCase() && it.length >= 3 }) return true

        if (parts.size == 1 && cleaned.length in 3..14) return true

        return false
    }
}
