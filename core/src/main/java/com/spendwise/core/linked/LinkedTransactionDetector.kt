package com.spendwise.core.linked

import com.spendwise.core.com.spendwise.core.isBillPayment
import com.spendwise.core.com.spendwise.core.isCardBillPayment
import com.spendwise.core.com.spendwise.core.isCreditCardSpend
import com.spendwise.core.com.spendwise.core.isPayZappWalletTopup
import com.spendwise.core.com.spendwise.core.isSingleSmsInternalTransfer
import com.spendwise.core.com.spendwise.core.isWalletAutoload
import com.spendwise.core.com.spendwise.core.isWalletCredit
import com.spendwise.core.com.spendwise.core.isWalletDeduction
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MerchantExtractorMl.extractWalletMerchant
import com.spendwise.core.model.TransactionCoreModel
import com.spendwise.core.transfer.SelfRecipientProvider
import java.util.UUID
import kotlin.math.absoluteValue
import com.spendwise.core.Logger as Log

class LinkedTransactionDetector(
    private val repo: LinkedTransactionRepository,
    private val autoLinkThreshold: Int = 80,
    private val possibleLinkThreshold: Int = 60,
    private val inferenceWindowDays: Int = 120
) {

    private val TAG = "LinkedDetector"

    private val internalMarkers = listOf(
        "infobil", "infoach", "infoimps", "infortgs"
    )

    // ------------------------------------------------------------
    // MAIN ENTRY
    // ------------------------------------------------------------
    suspend fun process(
        tx: TransactionCoreModel,
        selfRecipientProvider: SelfRecipientProvider
    ) {
        Log.d(TAG, "PROCESS TX ${tx.id} merchant=${tx.merchant}")
        if (tx.isNetZero) return
        /* --------------------------------------------------------
         * 🔒 SAFE single-SMS internal (ONLY if SELF recipient)
         * -------------------------------------------------------- */
        if (
            isSingleSmsInternalTransfer(tx.body) &&
            isSelfRecipient(tx, selfRecipientProvider)
        ) {
            markAsInternal(tx, 95)
            return
        }

        /* --------------------------------------------------------
         * INFO / ROUTING MESSAGES → INTERNAL
         * -------------------------------------------------------- */
        if (
            tx.type == "DEBIT" &&
            internalMarkers.any { tx.body.lowercase().contains(it) }
        ) {
            markAsInternal(tx, 100)
            return
        }

        /* --------------------------------------------------------
         * CARD / BILL / WALLET ROUTING
         * -------------------------------------------------------- */
        if (
            isCardBillPayment(tx.body) ||
            isBillPayment(tx.body) ||
            isWalletAutoload(tx.body) ||
            isWalletCredit(tx.body) ||
            (isCreditCardSpend(tx.body) &&
                    (isWalletCredit(tx.body) || isPayZappWalletTopup(tx.body)))
        ) {
            markAsInternal(tx, 95)
            return
        }

        /* --------------------------------------------------------
         * WALLET SPEND = REAL EXPENSE
         * -------------------------------------------------------- */
        extractWalletMerchant(tx.body)?.let {
            repo.updateMerchant(tx.id, it)
        }
        if (isWalletDeduction(tx.body)) return

        /* --------------------------------------------------------
         * CARD SPEND = REAL EXPENSE
         * -------------------------------------------------------- */
        if (isCreditCardSpend(tx.body)) return

        if (!isDebitOrCredit(tx)) return

        /* --------------------------------------------------------
         * SAME-DAY MATCHING
         * -------------------------------------------------------- */
        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = tx.timestamp - DAY_MS,
            to = tx.timestamp + DAY_MS,
            excludeId = tx.id
        )

        for (cand in candidates) {
            val score = scorePair(tx, cand)
            if (score >= possibleLinkThreshold) {

                // 🔒 FINAL SAFETY: NEVER internal unless self
                if (!isSelfRecipient(tx, selfRecipientProvider)) {
                    Log.e(TAG, "BLOCKED false internal: ${tx.merchant}")
                    break
                }

                applyLink(tx, cand, score)
                return
            }
        }

        /* --------------------------------------------------------
         * FINAL SAFETY NET (ABSOLUTE)
         * -------------------------------------------------------- */
        if (
            tx.linkType == "INTERNAL_TRANSFER" &&
            !isSelfRecipient(tx, selfRecipientProvider)
        ) {
            Log.e(TAG, "REVERTING false INTERNAL_TRANSFER ${tx.id}")
            repo.updateLink(
                id = tx.id,
                linkId = null,
                linkType = null,
                confidence = 0,
                isNetZero = false
            )
        }

        inferMissingCredit(tx, selfRecipientProvider)
    }

    // ------------------------------------------------------------
    // INTERNAL HELPERS
    // ------------------------------------------------------------
    private suspend fun isSelfRecipient(
        tx: TransactionCoreModel,
        selfRecipientProvider: SelfRecipientProvider
    ): Boolean {
        val merchant = tx.merchant ?: return false
        val norm = MerchantExtractorMl.normalize(merchant)

        // 🔒 Only explicit self recipients or wallets qualify
        return selfRecipientProvider().contains(norm) ||
                extractWalletMerchant(tx.body) != null
    }

    private suspend fun markAsInternal(
        tx: TransactionCoreModel,
        confidence: Int
    ) {
        repo.updateLink(
            id = tx.id,
            linkId = null,
            linkType = "INTERNAL_TRANSFER",
            confidence = confidence,
            isNetZero = true
        )
    }

    private suspend fun applyLink(
        a: TransactionCoreModel,
        b: TransactionCoreModel,
        score: Int
    ) {
        val linkId = UUID.randomUUID().toString()
        repo.updateLink(a.id, linkId, "INTERNAL_TRANSFER", score, true)
        repo.updateLink(b.id, linkId, "INTERNAL_TRANSFER", score, true)
    }

    private fun scorePair(a: TransactionCoreModel, b: TransactionCoreModel): Int {
        if (!isOpposite(a.type, b.type)) return 0
        if (a.amount != b.amount) return 0

        val merchantSim = similarity(a.merchant, b.merchant)
        if (merchantSim < 60) return 0

        val dayDiff =
            ((a.timestamp - b.timestamp).absoluteValue / DAY_MS)

        return when (dayDiff) {
            0L -> 90
            1L -> 80
            else -> 0
        }
    }

    private suspend fun inferMissingCredit(
        tx: TransactionCoreModel,
        selfRecipientProvider: SelfRecipientProvider
    ) {
        if (tx.linkType == "INTERNAL_TRANSFER") return
        if (!isSelfRecipient(tx, selfRecipientProvider)) return

        val patterns =
            if (tx.type == "DEBIT")
                repo.getAllLinkedCreditPatterns()
            else
                repo.getAllLinkedDebitPatterns()

        val key = "${normalize(tx.merchant ?: "")}|${extractPhrase(tx.body ?: "")}"
        if (!patterns.contains(key)) return

        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = tx.timestamp - inferenceWindowDays * DAY_MS,
            to = tx.timestamp + inferenceWindowDays * DAY_MS,
            excludeId = tx.id
        )

        candidates
            .filter { isOpposite(tx.type, it.type) }
            .firstOrNull()
            ?.let {
                applyLink(tx, it, autoLinkThreshold)
            }
    }

    // ------------------------------------------------------------
    // UTILS
    // ------------------------------------------------------------
    private fun isOpposite(a: String?, b: String?): Boolean =
        (a == "DEBIT" && b == "CREDIT") || (a == "CREDIT" && b == "DEBIT")

    private fun isDebitOrCredit(tx: TransactionCoreModel): Boolean =
        tx.type == "DEBIT" || tx.type == "CREDIT"

    private fun normalize(s: String): String =
        s.lowercase().replace("[^a-z0-9 ]".toRegex(), " ").trim()

    private fun extractPhrase(body: String): String =
        when {
            "transfer" in body.lowercase() -> "transfer"
            "credited" in body.lowercase() -> "credited"
            else -> "other"
        }

    private fun similarity(a: String?, b: String?): Int {
        if (a == null || b == null) return 0
        val sa = normalize(a).split(" ").toSet()
        val sb = normalize(b).split(" ").toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0
        return ((sa.intersect(sb).size.toDouble() / sa.union(sb).size) * 100).toInt()
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
