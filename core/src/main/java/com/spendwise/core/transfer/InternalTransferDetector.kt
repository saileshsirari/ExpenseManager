package com.spendwise.core.transfer

import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.SenderType
import com.spendwise.core.transfer.InternalTransferDetector.TransferInfo
import com.spendwise.core.Logger as Log

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
    private val debitAccountRegex =
        Regex(
            "(acct|a/c|account)\\s*(no\\.)?\\s*[x\\*]{2,}\\d{2,6}.*debited",
            RegexOption.IGNORE_CASE
        )

    private val creditAccountRegex =
        Regex(
            "(acct|a/c|account)\\s*(no\\.)?\\s*[x\\*]{2,}\\d{2,6}.*credited",
            RegexOption.IGNORE_CASE
        )

    private val internalMarkers = listOf(
        "infobil",
        "infoach",
        "infoimps",
        "infortgs"
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

        Log.e("ITD", "==============================")
        Log.e("ITD", "DETECT START")
        Log.e("ITD", "BODY = $body")
        Log.e("ITD", "AMOUNT = $amount")

        if (senderType != SenderType.BANK) return null
// 🔒 HARD STOP: Wallet spend → NEVER internal
        val isWalletSpend =
            body.contains("wallet", ignoreCase = true) &&
                    body.contains("paid", ignoreCase = true)

        if (isWalletSpend) {
            Log.e("ITD", "WALLET SPEND → NOT internal")
            return null
        }

        val lower = body.lowercase()
        val refMatch = refRegex.find(lower)
        val method = refMatch?.groupValues?.get(1)?.uppercase() ?: "BANK_TRANSFER"
        val ref = refMatch?.groupValues?.get(2) ?: "NO_REF"
        val isUpi = body.contains("UPI", ignoreCase = true)


        val hasDebitAccount = debitAccountRegex.containsMatchIn(lower)
        val hasCreditAccount =
            creditAccountRegex.containsMatchIn(body) &&
                    !body.contains("UPI", ignoreCase = true)

        Log.e(
            "ITD",
            "ACCOUNT CHECK → hasDebitAccount=$hasDebitAccount, hasCreditAccount=$hasCreditAccount"
        )

        // 🔒 GUARD: Person credited → NOT internal
        // 🔒 PERSON credited handling — HARD STOP
        val creditedPerson =
            MerchantExtractorMl.extractCreditedPerson(body)
        Log.e(
            "ITD",
            "PERSON EXTRACT → creditedPerson=$creditedPerson"
        )


// 🔒 CRITICAL GUARD:
// If credit side is an ACCOUNT, this is NOT a person transfer
        if (creditedPerson != null && !hasCreditAccount) {
            Log.e(
                "ITD",
                "PERSON GUARD HIT → creditedPerson=$creditedPerson (NO credit account)"
            )

            val normalized = MerchantExtractorMl.normalize(creditedPerson)
            val selfRecipients = selfRecipientProvider()
            Log.e(
                "ITD",
                "PERSON CHECK → normalized=$normalized selfRecipients=$selfRecipients"
            )
            return if (normalized in selfRecipients) {
                Log.e("ITD", "RETURNING SELF (UPI_SELF)")
                // ✅ USER DECLARED SELF
                TransferInfo(
                    ref = ref,
                    amount = amount,
                    hasDebitAccount = true,
                    hasCreditAccount = false,
                    method = "UPI_SELF"
                )
            } else {
                Log.e("ITD", "RETURNING NULL (EXTERNAL PERSON)")
                // ❌ External PERSON → NEVER internal
                null
            }
        }
        Log.e(
            "ITD",
            "CHECK ACCT↔ACCT → hasDebit=$hasDebitAccount hasCredit=$hasCreditAccount"
        )

        // ✅ INTERNAL TRANSFER ONLY IF BOTH SIDES ARE ACCOUNTS
        // 🔒 HARD RULE:
// UPI + PERSON is NEVER auto-internal
        if (isUpi && creditedPerson != null) {
            Log.e("ITD", "UPI PERSON → NOT internal")
            return null
        }

        internalMarkers.forEach { marker ->
            if (lower.contains(marker)) {
                Log.e("ITD", "Marker-based internal transfer detected → $marker")

                return TransferInfo(
                    ref = marker.uppercase(),
                    amount = amount,
                    hasDebitAccount = true,
                    hasCreditAccount = false, // single-sided SMS
                    method = marker.uppercase()
                )
            }
        }


// ✅ TRUE internal only when BOTH sides are accounts AND not UPI-person
        if (hasDebitAccount && hasCreditAccount) {
            Log.e("ITD", "ACCT↔ACCT → internal")
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
        Log.e("ITD", "FALLTHROUGH → returning NULL")
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
