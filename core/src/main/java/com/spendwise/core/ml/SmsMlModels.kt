package com.spendwise.core.ml

data class RawSms(
    val sender: String,
    val body: String,
    val timestamp: Long
)

enum class SenderType {
    BANK, WALLET, MERCHANT, PROMOTIONAL, PERSONAL, UNKNOWN
}

enum class IntentType {
    DEBIT, CREDIT, REFUND, PENDING, REMINDER, PROMO, BALANCE, UNKNOWN
}

enum class CategoryType {
    FOOD, TRAVEL, SHOPPING, FUEL, BILLS, UTILITIES, ENTERTAINMENT,
    HEALTH, EDUCATION, TRANSFER, ATM_CASH, INCOME, OTHER
}

/**
 * Final output of ML pipeline.
 * If this is null → we should skip the SMS (non-transaction).
 */
data class ClassifiedTxn(
    val rawSms: RawSms,
    val senderType: SenderType,
    val intentType: IntentType,
    val merchant: String?,
    val category: CategoryType,
    val amount: Double,
    val isCredit: Boolean,
    val explanation: MlReasonBundle
)


