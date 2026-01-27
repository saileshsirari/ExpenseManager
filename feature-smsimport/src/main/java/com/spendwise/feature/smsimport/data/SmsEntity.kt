
package com.spendwise.feature.smsimport.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendwise.core.com.spendwise.core.ExpenseFrequency
import com.spendwise.core.com.spendwise.core.FrequencyFilter
import com.spendwise.core.Logger as Log

@Entity(
    tableName = "sms",
    indices = [
        Index(value = ["sender", "timestamp", "body"], unique = true),
        Index(value = ["rawHash"])
    ]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val expenseFrequency: String = ExpenseFrequency.MONTHLY.name,
    val frequencyAnchorYear: Int? = null,   // for yearly grouping (e.g. 2025)

    // Extracted data
    val amount: Double,
    val merchant: String?,
    val type: String?,          // debit/credit/etc (from ML)
    val category: String?,
    val showIgnored: Boolean = false,

    // 🔒 Ignore / override
    val isIgnored: Boolean = false,

    // 🔑 ADD THESE (new, safe)
    val ignoreReason: String? = null,
    val updatedAt: Long = 0L,

    // Linked-transfer fields
    val linkId: String? = null,
    val linkType: String? = null,         // INTERNAL_TRANSFER / POSSIBLE_TRANSFER
    val linkConfidence: Int = 0,
    val isNetZero: Boolean = false ,       // true when auto-excluded by linking

    val currencyCode: String = "INR",
    val countryCode: String? = "IN",
    val sourceType: String = "SMS",  // SMS | EMAIL | PUSH | MANUAL
    val rawHash: String,
    val senderNormalized: String?,
    val processingVersion: Int = 1
)

private val walletRailKeywords = listOf(
    "payzapp",
    "paytm",
    "phonepe",
    "amazon pay",
    "amazonpay",
    "mobikwik"
)

fun SmsEntity.isWalletMerchantSpend(): Boolean {
    if (!isExpense()) return false

    val bodyLower = body.lowercase()

    // Wallet rail must be present
    if (!walletRailKeywords.any { bodyLower.contains(it) }) return false

    // Merchant itself must not be wallet
    val m = merchant?.lowercase() ?: return false
    if ("wallet" in m) return false
    Log.d(
        "WALLET_SPEND",
        "id=$id, merchant=$merchant, isWalletMerchantSpend=true"
    )
    return true
}



fun SmsEntity.isExpense(): Boolean {
    return type.equals("DEBIT", true)
            && !isNetZero
            && !isIgnored
}

 fun SmsEntity.isWalletTopUp(): Boolean {
    val m = merchant ?: return false
    return isNetZero &&
            linkType == "INTERNAL_TRANSFER" &&
            m.contains("wallet", ignoreCase = true)
}
private fun SmsEntity.matchesFrequency(
    filter: FrequencyFilter
): Boolean {
    val freq = expenseFrequency

    return when (filter) {
        FrequencyFilter.MONTHLY_ONLY ->
            freq == ExpenseFrequency.MONTHLY.name

        FrequencyFilter.ALL_EXPENSES ->
            true

        FrequencyFilter.YEARLY_ONLY ->
            freq == ExpenseFrequency.YEARLY.name
    }
}
fun SmsEntity.hasCreditedPartyInSameSms(): Boolean {
    val text = body.lowercase()
    return " credited" in text || "; credited" in text
}
fun SmsEntity.isSystemInfoDebit(): Boolean {
    val text = body.lowercase()
    return text.contains("info:bil") ||
            text.contains("info:imps") ||
            text.contains("info:ach") ||
            text.contains("info:rtgs")
}


@Entity(tableName = "user_self_patterns")
data class UserSelfPattern(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val personName: String,        // "SAILESH"
    val senderBank: String,        // "ICICI", "SBI"
    val direction: String,         // "DEBIT_TO_PERSON"

    val createdAt: Long = System.currentTimeMillis(),
)



