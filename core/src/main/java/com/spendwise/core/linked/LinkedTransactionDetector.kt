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
        val windowMs = LINK_WINDOW_DAYS * DAY_MS

        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = tx.timestamp - windowMs,
            to = tx.timestamp + windowMs,
            excludeId = tx.id
        )


        for (cand in candidates) {
            // 🔒 Optional safety: debit should precede credit (or same day)
            if (tx.type == "DEBIT" && cand.timestamp < tx.timestamp - windowMs) continue

            val score = scorePair(tx, cand)
            if (score >= possibleLinkThreshold) {

                // 🔒 FINAL SAFETY: NEVER internal unless self
                val isExplicitSelf = isSelfRecipient(tx, selfRecipientProvider)
                val isSamePerson = isSamePersonTransfer(tx, cand)

                if (!isExplicitSelf && !isSamePerson) {
                    Log.e(
                        TAG,
                        "BLOCKED false internal: not self, not same person"
                    )
                    continue
                }


                applyLink(tx, cand, score)
                if (isSamePerson) {
                    repo.saveLinkedPattern(
                        "${extractPersonName(tx.body)}|person_transfer"
                    )
                }

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
            in 1L..2L -> 80
            in 3L..35L -> 65    // 👈 still ≥ possibleLinkThreshold (60)
            else -> 0
        }

    }
    private val personNameRegex = Regex(
        "(to|from)\\s+(mr\\.?|mrs\\.?|ms\\.?|shri)?\\s*([A-Za-z][A-Za-z ]{2,50})",
        RegexOption.IGNORE_CASE
    )

    private fun extractPersonName(body: String): String? {
        val m = personNameRegex.find(body.lowercase()) ?: return null
        val raw = m.groupValues[3]

        val cleaned = raw
            .replace(Regex("[^A-Za-z ]"), "")
            .trim()

        if (cleaned.split(" ").size > 3) return null // avoid long garbage matches

        return cleaned.uppercase()
    }

    private fun isSamePersonTransfer(
        a: TransactionCoreModel,
        b: TransactionCoreModel
    ): Boolean {
        val p1 = extractPersonName(a.body)
        val p2 = extractPersonName(b.body)

        return p1 != null && p1 == p2
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

        val windowMs = LINK_WINDOW_DAYS * DAY_MS

        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = tx.timestamp - windowMs,
            to = tx.timestamp + windowMs,
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
        private const val LINK_WINDOW_DAYS = 35L
    }
}
