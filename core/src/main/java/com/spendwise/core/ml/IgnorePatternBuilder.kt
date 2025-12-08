package com.spendwise.core.ml

object IgnorePatternBuilder {

    fun build(body: String): String {
        var b = body.lowercase()

        // Replace amounts
        b = b.replace(Regex("(inr|rs\\.?|₹)\\s*[0-9,]+(\\.\\d{1,2})?"), ".*")

        // Replace dates
        b = b.replace(Regex("\\b\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4}\\b"), ".*")

        // Replace card numbers
        b = b.replace(Regex("card\\s*[0-9*]+"), "card .*")

        // Replace transaction ID patterns
        b = b.replace(Regex("ref[: ]?[a-z0-9]+"), "ref .*")

        return b.trim()
    }
}
