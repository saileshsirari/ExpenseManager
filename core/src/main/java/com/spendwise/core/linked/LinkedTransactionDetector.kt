package com.spendwise.core.linked

import com.spendwise.core.com.spendwise.core.isCardBillPayment
import com.spendwise.core.com.spendwise.core.isCreditCardSpend
import com.spendwise.core.model.TransactionCoreModel
import java.util.UUID
import kotlin.math.absoluteValue
import com.spendwise.core.Logger as Log

class LinkedTransactionDetector(
    private val repo: LinkedTransactionRepository,
    private val amountWeight: Int = 60,
    private val dateWeightSameDay: Int = 15,
    private val dateWeightOneDay: Int = 10,
    private val oppositeDirWeight: Int = 10,
    private val nameSimWeight: Int = 10,
    private val bankMatchWeight: Int = 5,
    private val autoLinkThreshold: Int = 80,
    private val possibleLinkThreshold: Int = 60,
    private val inferenceWindowDays: Int = 120   // ±120 days for inference
) {

    private val TAG = "LinkedDetector"

    // Markers considered "internal transfer" (ICICI/HDFC style)
    private val internalMarkers = listOf("infobil", "infoach", "infoimps", "infortgs")

    // ------------------------------------------------------------
    // MAIN ENTRY
    // ------------------------------------------------------------
    suspend fun process(tx: TransactionCoreModel) {

        Log.d(TAG, "\n\n========== PROCESS TX ${tx.id} ==========")
        Log.d(TAG, "type=${tx.type}, merchant=${tx.merchant}, sender=${tx.sender}")
        Log.d(TAG, "amount=${tx.amount}, ts=${tx.timestamp} (${tsReadable(tx.timestamp)})")
        if (tx.isNetZero) {
            Log.d(TAG, "Already net-zero → skipping further processing")
            return
        }
        if (isCardBillPayment(tx.body) && !tx.isNetZero) {
            repo.updateLink(
                id = tx.id,
                linkId = null,
                linkType = "INTERNAL_TRANSFER",
                confidence = 95,
                isNetZero = true
            )
        }

        if (isWalletDeduction(tx.body) && !tx.isNetZero) {
            repo.updateLink(
                id = tx.id,
                linkId = null,
                linkType = "INTERNAL_TRANSFER",
                confidence = 80,
                isNetZero = true
            )
            return
        }

        if (!isDebitOrCredit(tx)) {
            Log.d(TAG, "SKIP — Not debit/credit\n")
            return
        }




        if (
            isAssetDestination(tx.body) &&
            !tx.body.lowercase().contains("interest") &&
            !tx.isNetZero
        ) {
            markAsInternalTransfer(tx)
            return
        }

        if (
            isWalletTransaction(tx.body) &&
            !tx.isNetZero
        ) {
            repo.updateLink(
                id = tx.id,
                linkId = null,
                linkType = "INTERNAL_TRANSFER",
                confidence = 85,
                isNetZero = true
            )
        }

        // ---------- QUICK SINGLE-SIDED INTERNAL TRANSFER RULE (Option A) ----------
        // If it's a DEBIT and body contains internal marker -> mark as internal transfer immediately.
        val bodyLower = tx.body?.lowercase() ?: ""
        if (tx.type?.equals("DEBIT", ignoreCase = true) == true &&
            internalMarkers.any { bodyLower.contains(it) } &&
            tx.linkId.isNullOrBlank()
        ) {
            Log.d(
                TAG,
                "Internal-marker detected (Option A). Will mark TX ${tx.id} as INTERNAL_TRANSFER (single-sided)."
            )
            val singleLinkId = "SINGLE-${UUID.randomUUID()}"
            // Mark transaction single-sided as internal transfer (net-zero) so UI and totals will exclude it
            repo.updateLink(tx.id, singleLinkId, "INTERNAL_TRANSFER", 100, true)

            // Learn pattern immediately for future inference
            try {
                val merchant = normalize(tx.merchant ?: tx.sender ?: "")
                val phrase = extractPhrase(tx.body ?: "")
                val key = "$merchant|$phrase"
                Log.d(TAG, "Learning pattern from single-sided internal: $key")
                repo.saveLinkedPattern(key)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to save linked pattern for single-sided internal: ${e.message}")
            }

            Log.d(TAG, "SINGLE-SIDED LINK APPLIED → $singleLinkId [${tx.id}]")
            // do not continue normal linking — we've classified this as internal transfer
            return
        }

        // ============================================================
        // 1) NORMAL SAME-DAY MATCHING
        // ============================================================
        val windowFrom = tx.timestamp - DAY_MS
        val windowTo = tx.timestamp + DAY_MS

        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = windowFrom,
            to = windowTo,
            excludeId = tx.id
        )

        Log.d(TAG, "Found ${candidates.size} same-day candidates")

        var autoLinked = false
        var maybeLinked = false

        for (cand in candidates) {

            val score = scorePair(tx, cand)

            Log.d(
                TAG,
                " → score vs TX ${cand.id} = $score (merchant=${cand.merchant}, sender=${cand.sender})"
            )

            when {
                score >= possibleLinkThreshold -> {
                    // possibleLinkThreshold now means: "good enough to be internal"
                    Log.d(TAG, " ⭐ INTERNAL_TRANSFER (from possible-or-better)")

                    applyLink(tx, cand, score, "INTERNAL_TRANSFER", true)

                    autoLinked = true
                    break
                }

                else -> {
                    Log.d(TAG, " ❌ Not linked (score=$score)")
                }
            }
        }

        // If auto-linked -> done
        if (autoLinked) return

        // Even if maybeLinked, still attempt inference for stronger historical matches
        inferMissingCredit(tx)
    }

    private suspend fun markAsInternalTransfer(tx: TransactionCoreModel, confidence: Int = 90) {
        repo.updateLink(
            id = tx.id,
            linkId = null,
            linkType = "INTERNAL_TRANSFER",
            confidence = confidence,
            isNetZero = true
        )
    }


    // ------------------------------------------------------------
    // APPLY LINK
    // ------------------------------------------------------------
    private suspend fun applyLink(
        a: TransactionCoreModel,
        b: TransactionCoreModel,
        score: Int,
        type: String,
        isNetZero: Boolean
    ) {
        val linkId = generateLinkId(a, b)

        repo.updateLink(a.id, linkId, type, score, isNetZero)
        repo.updateLink(b.id, linkId, type, score, isNetZero)

        // Learn pattern immediately for both sides
        addLearnedPattern(a)
        addLearnedPattern(b)

        Log.d(TAG, "LINK APPLIED → $linkId  [${a.id} ↔ ${b.id}] (type=$type)")
    }

    private suspend fun addLearnedPattern(tx: TransactionCoreModel) {
        if (!isDebitOrCredit(tx)) return

        val merchant = normalize(tx.merchant ?: return)
        val phrase = extractPhrase(tx.body ?: return)

        val key = "$merchant|$phrase"
        Log.d(TAG, "Learning pattern: $key")

        repo.saveLinkedPattern(key)
    }

    // ------------------------------------------------------------
    // SCORING
    // ------------------------------------------------------------
    private fun scorePair(a: TransactionCoreModel, b: TransactionCoreModel): Int {

        Log.d(TAG, "---- SCORE CHECK: A=${a.id} B=${b.id} ----")

        if (!isOpposite(a.type, b.type)) return 0
        if (a.amount != b.amount) return 0

        val bodyA = a.body.orEmpty()
        val bodyB = b.body.orEmpty()

        val merchantSim = similarity(a.merchant, b.merchant)

        val transferLike =
            isTransferLike(bodyA) && isTransferLike(bodyB)

        val skipMerchantCheck =
            transferLike ||
                    isCardBillPayment(bodyA) || isCardBillPayment(bodyB)


        if (!skipMerchantCheck && merchantSim < 10) return 0


        // Inside scorePair or before linking
        if (isCreditCardSpend(a.body) || isCreditCardSpend(b.body)) {
            Log.d(TAG, "Skip linking — card spend detected")
            return 0
        }
        var score = 0
        score += amountWeight
        score += oppositeDirWeight
        score += (merchantSim * nameSimWeight / 100)

        if (!a.sender.isNullOrBlank() && a.sender == b.sender) {
            score += bankMatchWeight
        }

        val dayDiff =
            ((a.timestamp - b.timestamp).absoluteValue / DAY_MS)

        val strongDelayedTransfer =
            dayDiff in 2..35 &&
                    a.amount >= 1000 &&
                    transferLike &&
                    merchantSim >= 60

        score += when {
            dayDiff == 0L -> dateWeightSameDay
            dayDiff == 1L -> dateWeightOneDay
            strongDelayedTransfer -> {
                Log.d(TAG, "delayed-transfer boost (dayDiff=$dayDiff)")
                dateWeightSameDay
            }

            else -> 0
        }

        return score.coerceIn(0, 100)
    }


    private fun isOpposite(a: String?, b: String?): Boolean {
        val x = a?.lowercase()
        val y = b?.lowercase()
        return (x == "debit" && y == "credit") || (x == "credit" && y == "debit")
    }

    // ------------------------------------------------------------
    // MISSING CREDIT INFERENCE (WIDER WINDOW)
    // ------------------------------------------------------------
    private suspend fun inferMissingCredit(tx: TransactionCoreModel) {
        Log.d(TAG, "---- Missing Credit Inference for TX=${tx.id} ----")
        Log.d(TAG, "merchant=${tx.merchant}, amount=${tx.amount}, sender=${tx.sender}")

        // Do not touch already-internal transactions
        if (tx.linkType == "INTERNAL_TRANSFER") {
            Log.d(TAG, "Skip inference — already INTERNAL_TRANSFER")
            return
        }

        // 1) Build normalized key for current tx
        val normMerchant = normalize(tx.merchant ?: tx.sender ?: "")
        val phrase = extractPhrase(tx.body ?: "")
        val currentKey = "$normMerchant|$phrase"

        Log.d(TAG, "Current key=$currentKey")

        // 2) Determine which patterns to check based on tx type
        val expectedPatterns = when (tx.type?.lowercase()) {
            "debit" -> repo.getAllLinkedCreditPatterns()
            "credit" -> repo.getAllLinkedDebitPatterns()
            else -> emptySet()
        }

        Log.d(TAG, "Expected pattern count = ${expectedPatterns.size}")

        if (!expectedPatterns.contains(currentKey)) {
            Log.d(TAG, "❌ No matching opposite-pattern → skip inference.")
            return
        }

        Log.d(TAG, "✅ Opposite-pattern FOUND → searching wider window...")

        // 3) Wide window ± inferenceWindowDays
        val window = inferenceWindowDays * DAY_MS
        val from = tx.timestamp - window
        val to = tx.timestamp + window

        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = from,
            to = to,
            excludeId = tx.id
        )

        Log.d(TAG, "Found ${candidates.size} wide-window candidates")

        val best = candidates
            .asSequence()
            .filter { isOpposite(tx.type, it.type) }
            .filter { similarity(tx.merchant, it.merchant) >= 20 }
            .map { it to scorePair(tx, it) }
            .filter { it.second >= autoLinkThreshold }   // 🔑 ONLY internal-worthy
            .maxByOrNull { it.second }

        if (best == null) {
            Log.d(TAG, "❌ No strong inference candidate found.")
            return
        }

        val (match, score) = best

        Log.d(TAG, "⭐ INFERRED INTERNAL_TRANSFER → TX=${match.id}, score=$score")

        applyLink(
            a = tx,
            b = match,
            score = score,
            type = "INTERNAL_TRANSFER",
            isNetZero = true
        )
    }


    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------
    private fun tsReadable(ts: Long?): String {
        if (ts == null) return "null"
        return try {
            java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
                .format(java.util.Date(ts))
        } catch (e: Exception) {
            ts.toString()
        }
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun extractPhrase(body: String): String {
        val b = body.lowercase()
        return when {
            "transferred" in b || "transfer to" in b || "sent to" in b -> "person_transfer"
            "deposit" in b || "credited by" in b -> "deposit_from"
            "credited" in b && "wallet" !in b -> "credited_to"
            "debited" in b || "deducted" in b -> "debited_from"
            "payzapp" in b || "wallet" in b -> "wallet_deduction"
            else -> "other"
        }
    }

    private fun isTransferLike(text: String?): Boolean {
        if (text == null) return false
        val b = text.lowercase()
        return ("transfer" in b ||
                "transferred" in b ||
                "sent to" in b ||
                "upi" in b ||
                "deposit" in b ||
                "credited by" in b ||
                internalMarkers.any { b.contains(it) })
    }
    private fun isWalletTransaction(body: String?): Boolean {
        if (body == null) return false
        val b = body.lowercase()

        val walletKeywords = listOf(
            "wallet",
            "payzapp",
            "paytm",
            "phonepe",
            "amazon pay",
            "amazonpay",
            "mobikwik",
            "freecharge",
            "google pay balance",
            "gpay balance"
        )

        return walletKeywords.any { it in b }
    }

    private fun isAssetDestination(body: String?): Boolean {
        if (body == null) return false
        val b = body.lowercase()

        val assetKeywords = listOf(
            // Mutual Funds
            "mutual fund", "mf ", "sip", "folio", "nav",
            "cams", "kfintech", "groww", "zerodha", "coin",

            // EPF / PF / VPF
            "epf", "pf ", "vpf", "provident fund", "epfo", "uan",

            // NPS
            "nps", "pran", "cra", "pension system",

            // RD
            "rd ", "recurring deposit", "installment",

            // FD
            "fd ", "fixed deposit", "term deposit", "td ",

            // EPF implicit patterns (VERY IMPORTANT)
            "passbook balance",
            "contribution of"
            )

        return assetKeywords.any { it in b }
    }

    private fun isWalletDeduction(text: String?): Boolean {
        if (text == null) return false
        val b = text.lowercase()

        val walletKeywords = listOf(
            "payzapp",
            "paytm",
            "phonepe",
            "amazon pay",
            "amazonpay",
            "mobikwik",
            "freecharge",
            "airtel money",
            "jio money",
            "wallet"
        )

        val deductionKeywords = listOf(
            "deducted",
            "spent",
            "paid",
            "used",
            "txn",
            "transaction"
        )

        return walletKeywords.any { it in b } &&
                deductionKeywords.any { it in b }
    }


    private fun similarity(a: String?, b: String?): Int {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 100
        val aTokens = na.split(" ").filter { it.isNotBlank() }.toSet()
        val bTokens = nb.split(" ").filter { it.isNotBlank() }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0
        val intersect = aTokens.intersect(bTokens).size
        val union = aTokens.union(bTokens).size
        return ((intersect.toDouble() / union) * 100).toInt()
    }

    private fun generateLinkId(a: TransactionCoreModel, b: TransactionCoreModel): String =
        UUID.nameUUIDFromBytes("${a.amount}-${a.timestamp}-${b.timestamp}".toByteArray())
            .toString()

    private fun isDebitOrCredit(tx: TransactionCoreModel): Boolean {
        val t = tx.type?.lowercase() ?: return false
        return (t == "debit" || t == "credit")
    }



    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
