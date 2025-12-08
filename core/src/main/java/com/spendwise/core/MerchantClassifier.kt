package com.spendwise.core

object MerchantClassifier {

    data class Result(
        val merchantName: String,
        val category: String,
        val logoRes: Int
    )

    fun classify(sender: String, body: String): Result {
        val text = (sender + " " + body).lowercase()

        return when {
            text.contains("amazon") -> Result("Amazon", "Shopping", R.drawable.ic_amazon)
            text.contains("flipkart") -> Result("Flipkart", "Shopping", R.drawable.ic_flipkart)
            text.contains("myntra") -> Result("Myntra", "Shopping", R.drawable.ic_myntra)
            text.contains("swiggy") -> Result("Swiggy", "Food", R.drawable.ic_swiggy)
            text.contains("zomato") -> Result("Zomato", "Food", R.drawable.ic_zomato)
            text.contains("ola") -> Result("Ola", "Travel", R.drawable.ic_ola)
            text.contains("uber") -> Result("Uber", "Travel", R.drawable.ic_uber)
            text.contains("irctc") -> Result("IRCTC", "Travel", R.drawable.ic_train)
            text.contains("paytm") -> Result("Paytm", "Payments", R.drawable.ic_paytm)
            text.contains("tatasky") || text.contains("dth") -> Result("DTH", "Bills", R.drawable.ic_dth)
            text.contains("atm") -> Result("ATM", "ATM Withdrawal", R.drawable.ic_atm)

            // Bank Names
            text.contains("hdfc") -> Result("HDFC Bank", "Bank", R.drawable.ic_bank)
            text.contains("icici") -> Result("ICICI Bank", "Bank", R.drawable.ic_bank)
            text.contains("sbi") -> Result("SBI Bank", "Bank", R.drawable.ic_bank)

            // Default fallback
            else -> Result(sender, "Other", R.drawable.ic_default_merchant)
        }
    }
}
