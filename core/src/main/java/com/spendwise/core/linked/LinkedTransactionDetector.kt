package com.spendwise.core.linked

import android.util.Log
import com.spendwise.core.model.TransactionCoreModel
import java.util.UUID
import kotlin.math.absoluteValue

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
    private val inferenceWindowDays: Int = 120
) {

    private val TAG = "LinkedDetector"

    // ------------------------------------------------------------
    // MAIN ENTRY
    // ------------------------------------------------------------
    suspend fun process(tx: TransactionCoreModel) {

        Log.d(TAG, "\n\n========== PROCESS TX ${tx.id} ==========")
        Log.d(TAG, "type=${tx.type}, merchant=${tx.merchant}, sender=${tx.sender}")
        Log.d(TAG, "amount=${tx.amount}, ts=${tx.timestamp} (${tsReadable(tx.timestamp)})")
        Log.d(TAG, "body=${tx.body}")

        if (!isDebitOrCredit(tx)) {
            Log.d(TAG, "SKIP — Not debit/credit\n")
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

            Log.d(TAG, " → score vs TX ${cand.id} = $score (merchant=${cand.merchant}, sender=${cand.sender})")

            when {
                score >= autoLinkThreshold -> {
                    Log.d(TAG, " ⭐ AUTO-LINKED")
                    applyLink(tx, cand, score, "INTERNAL_TRANSFER", true)
                    autoLinked = true
                    break
                }

                score >= possibleLinkThreshold -> {
                    Log.d(TAG, " ⚠️ POSSIBLE-LINK")
                    applyLink(tx, cand, score, "POSSIBLE_TRANSFER", false)
                    maybeLinked = true
                }

                else -> Log.d(TAG, " ❌ Not linked (score=$score)")
            }
        }

        if (autoLinked) return

        inferMissingCredit(tx)
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

        addLearnedPattern(a)
        addLearnedPattern(b)

        Log.d(TAG, "LINK APPLIED → $linkId  [${a.id} ↔ ${b.id}] ($type)")
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
    // CONDITIONAL MERCHANT MATCHING WITH LOGS
    // ------------------------------------------------------------
    private fun isTransferLike(text: String?): Boolean {
        if (text == null) return false
        val b = text.lowercase()

        val contains = listOf(
            "transfer" in b,
            "transferred" in b,
            "sent to" in b,
            "upi" in b,
            "deposit" in b,
            "credited by" in b,
            "infobil" in b,
            "infoach" in b,
            "infoimps" in b,
            "infortgs" in b
        )

        val result = contains.any { it }

        Log.d("TransferCheck", "isTransferLike?=$result  BODY=$b")

        return result
    }

    private fun isCardPayment(text: String?): Boolean {
        if (text == null) return false
        val b = text.lowercase()
        val result =
            ("credit card" in b || "card payment" in b || "bill payment" in b)

        Log.d("TransferCheck", "isCardPayment?=$result  BODY=$b")
        return result
    }

    // ------------------------------------------------------------
    // SCORING
    // ------------------------------------------------------------
    private fun scorePair(a: TransactionCoreModel, b: TransactionCoreModel): Int {

        Log.d("DetectorDebug", "---- SCORE CHECK ----")
        Log.d("DetectorDebug", "A(id=${a.id}) merchant=${a.merchant} type=${a.type}")
        Log.d("DetectorDebug", "B(id=${b.id}) merchant=${b.merchant} type=${b.type}")

        if (!isOpposite(a.type, b.type)) {
            Log.d("DetectorDebug", "BLOCK: same direction")
            return 0
        }

        if (a.amount != b.amount) {
            Log.d("DetectorDebug", "BLOCK: amount mismatch")
            return 0
        }

        val bodyA = a.body ?: ""
        val bodyB = b.body ?: ""

        val skipMerchantCheck =
            isTransferLike(bodyA) || isTransferLike(bodyB) ||
                    isCardPayment(bodyA) || isCardPayment(bodyB)

        val merchantSim = similarity(a.merchant, b.merchant)

        Log.d("DetectorDebug", "MerchantSim=$merchantSim  skipMerchantCheck=$skipMerchantCheck")

        if (!skipMerchantCheck && merchantSim < 10) {
            Log.d("DetectorDebug", "🚫 BLOCKED: merchant mismatch (sim=$merchantSim)")
            return 0
        }

        // ---- COMPUTE SCORE ----
        var score = 0
        score += amountWeight

        val dayDiff = ((a.timestamp - b.timestamp).absoluteValue / DAY_MS)
        score += when (dayDiff) {
            0L -> dateWeightSameDay
            1L -> dateWeightOneDay
            else -> 0
        }

        score += oppositeDirWeight
        score += (merchantSim * nameSimWeight / 100)

        if (a.sender == b.sender && !a.sender.isNullOrBlank()) {
            score += bankMatchWeight
        }

        Log.d("DetectorDebug", "FINAL SCORE=$score")
        return score.coerceIn(0, 100)
    }

    private fun isOpposite(a: String?, b: String?): Boolean {
        val x = a?.lowercase()
        val y = b?.lowercase()
        return (x == "debit" && y == "credit") || (x == "credit" && y == "debit")
    }

    // ------------------------------------------------------------
    // MISSING CREDIT INFERENCE
    // ------------------------------------------------------------
    private suspend fun inferMissingCredit(tx: TransactionCoreModel) {
        Log.d(TAG, "---- Missing Credit Inference for TX=${tx.id} ----")
        Log.d(TAG, "merchant=${tx.merchant}, amount=${tx.amount}, sender=${tx.sender}")

        val merchant = normalize(tx.merchant ?: tx.sender ?: "")
        val phrase = extractPhrase(tx.body ?: "")
        val key = "$merchant|$phrase"

        Log.d(TAG, "Current key=$key")

        val expectedPatterns = when (tx.type?.lowercase()) {
            "debit" -> repo.getAllLinkedCreditPatterns()
            "credit" -> repo.getAllLinkedDebitPatterns()
            else -> emptySet()
        }

        Log.d(TAG, "Expected pattern count=${expectedPatterns.size}")
        expectedPatterns.forEach { Log.d(TAG, " → ExpectedPattern=$it") }

        if (!expectedPatterns.contains(key)) {
            Log.d(TAG, "❌ NO PATTERN MATCH → stop.")
            return
        }

        Log.d(TAG, "✅ PATTERN MATCH — searching wide window...")

        val window = inferenceWindowDays * DAY_MS
        val from = tx.timestamp - window
        val to = tx.timestamp + window

        val candidates = repo.findCandidates(tx.amount, from, to, tx.id)
        Log.d(TAG, "Wide-window candidates=${candidates.size}")

        val filtered = candidates.filter { isOpposite(tx.type, it.type) }
            .filter { similarity(tx.merchant, it.merchant) >= 20 }

        Log.d(TAG, "Filtered candidates=${filtered.size}")

        val best = filtered
            .map { it to scorePair(tx, it) }
            .filter { it.second >= possibleLinkThreshold }
            .maxByOrNull { it.second }

        if (best == null) {
            Log.d(TAG, "❌ No strong match found.")
            return
        }

        val (match, score) = best
        Log.d(TAG, "✅ INFERRED MATCH TX=${match.id}, score=$score")
        applyLink(tx, match, score, "POSSIBLE_TRANSFER", false)
    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------
    private fun tsReadable(ts: Long?): String {
        if (ts == null) return "null"
        return java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
            .format(java.util.Date(ts))
    }

    private fun normalize(s: String): String =
        s.lowercase().replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()

    private fun extractPhrase(body: String): String {
        val b = body.lowercase()

        return when {
            "transferred" in b || "transfer to" in b || "sent to" in b ->
                "person_transfer"

            "deposit" in b || "credited by" in b ->
                "deposit_from"

            "credited" in b && "wallet" !in b ->
                "credited_to"

            "debited" in b || "deducted" in b ->
                "debited_from"

            "wallet" in b ->
                "wallet_deduction"

            else -> "other"
        }
    }

    private fun similarity(a: String?, b: String?): Int {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 100
        val at = na.split(" ").toSet()
        val bt = nb.split(" ").toSet()
        val inter = at.intersect(bt).size
        val union = at.union(bt).size
        return ((inter.toDouble() / union) * 100).toInt()
    }

    private fun generateLinkId(a: TransactionCoreModel, b: TransactionCoreModel): String =
        UUID.nameUUIDFromBytes("${a.amount}-${a.timestamp}-${b.timestamp}".toByteArray()).toString()

    private fun isDebitOrCredit(tx: TransactionCoreModel): Boolean {
        val t = tx.type?.lowercase() ?: return false
        return (t == "debit" || t == "credit")
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
