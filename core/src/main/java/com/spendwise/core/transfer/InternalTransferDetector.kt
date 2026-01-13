package com.spendwise.core.transfer

import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.SenderType
import com.spendwise.core.transfer.InternalTransferDetector.TransferInfo

/**
 * Detects internal / cross-account transfers for ALL Indian banks.
 *
 * Rules:
 * - Deterministic
 * - Regex-based
 * - No rescans
 * - No merchant involvement
 */
object InternalTransferDetector {

    data class TransferInfo(
        val ref: String,
        val amount: Double,
        val hasDebitAccount: Boolean,
        val hasCreditAccount: Boolean,
        val method: String
    )

    // 🔒 STRICT account-based debit
    private val debitAccountRegex = Regex(
        "(?i)(acct|a/c|account)\\s+(?:xx|\\*)?\\d{2,4}.*?(debited|withdrawn|dr)"
    )

    // 🔒 STRICT account-based credit (NOT person)
    private val creditAccountRegex = Regex(
        "(?i)(acct|a/c|account)\\s+(?:xx|\\*)?\\d{2,4}.*?(credited|cr)"
    )

    private val refRegex = Regex(
        "(?i)\\b(imps|neft|rtgs|upi|ach|ecs)[:\\s-]*([0-9]{6,20})\\b"
    )

    /**
     * Detect INTERNAL (self) transfers only.
     * External transfers / P2P MUST return null.
     */
   suspend fun detect(
        senderType: SenderType,
        body: String,
        amount: Double,
        selfRecipientProvider: SelfRecipientProvider
    ): TransferInfo? {

        if (senderType != SenderType.BANK) return null

        val lower = body.lowercase()
        val refMatch = refRegex.find(lower)
        val method = refMatch?.groupValues?.get(1)?.uppercase() ?: "BANK_TRANSFER"
        val ref = refMatch?.groupValues?.get(2) ?: "NO_REF"


        val hasDebitAccount = debitAccountRegex.containsMatchIn(lower)
        val hasCreditAccount = creditAccountRegex.containsMatchIn(lower)

        // 🔒 GUARD: Person credited → NOT internal
        val creditedPerson =
            MerchantExtractorMl.extractCreditedPerson(body)

        if (creditedPerson != null) {
            val normalized = MerchantExtractorMl.normalize(creditedPerson)
            val selfRecipients = selfRecipientProvider()   // injected

            if (normalized in selfRecipients) {
                // USER DECLARED SELF
                return TransferInfo(
                    ref = ref,
                    amount = amount,
                    hasDebitAccount = true,
                    hasCreditAccount = false, // person, but self
                    method = "UPI_SELF"
                )
            }
        }


        // ✅ INTERNAL TRANSFER ONLY IF BOTH SIDES ARE ACCOUNTS
        if (hasDebitAccount && hasCreditAccount) {
            return TransferInfo(
                ref = ref,
                amount = amount,
                hasDebitAccount = true,
                hasCreditAccount = true,
                method = method
            )
        }

        // ✅ Split SMS case (wait for pairing)
        if (refMatch != null && (hasDebitAccount || hasCreditAccount)) {
            return TransferInfo(
                ref = ref,
                amount = amount,
                hasDebitAccount = hasDebitAccount,
                hasCreditAccount = hasCreditAccount,
                method = method
            )
        }

        return null
    }
}

object TransferBuffer {

    private val pending = mutableMapOf<String, TransferInfo>()

    fun match(info: TransferInfo): Boolean {
        val key = "${info.ref}-${info.amount}"

        val existing = pending[key]
        return if (existing != null) {
            pending.remove(key)
            true // matched
        } else {
            pending[key] = info
            false // waiting
        }
    }


}
