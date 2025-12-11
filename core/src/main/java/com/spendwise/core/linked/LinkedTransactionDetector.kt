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
    private val inferenceWindowDays: Int = 120   // ±120 days for missing-credit matching
) {

    private val TAG = "LinkedDetector"

    // ------------------------------------------------------------
    // MAIN ENTRY
    // ------------------------------------------------------------
    suspend fun process(tx: TransactionCoreModel) {

        Log.d(TAG, "\n\n========== PROCESS TX ${tx.id} ==========")
        Log.d(TAG, "type=${tx.type}, merchant=${tx.merchant}, sender=${tx.sender}")
        Log.d(TAG, "amount=${tx.amount}, ts=${tx.timestamp} (${tsReadable(tx.timestamp)})")

        if (!isDebitOrCredit(tx)) {
            Log.d(TAG, "SKIP — Not debit/credit\n")
            return
        }

        // ============================================================
        // 1) SAME-DAY CANDIDATE LINKING
        // ============================================================
        val from = tx.timestamp - DAY_MS
        val to = tx.timestamp + DAY_MS

        val candidates = repo.findCandidates(tx.amount, from, to, tx.id)
        Log.d(TAG, "Found ${candidates.size} same-day candidates")

        var autoLinked = false

        for (cand in candidates) {
            val score = scorePair(tx, cand)

            Log.d(TAG, " → Score vs TX ${cand.id} = $score (merchant=${cand.merchant}, sender=${cand.sender})")

            when {
                score >= autoLinkThreshold -> {
                    Log.d(TAG, " ⭐ AUTO-LINKED (Internal Transfer)")
                    applyLink(tx, cand, score, "INTERNAL_TRANSFER", true)
                    autoLinked = true
                    break
                }
                score >= possibleLinkThreshold -> {
                    Log.d(TAG, " ⚠️ POSSIBLE-LINK")
                    applyLink(tx, cand, score, "POSSIBLE_TRANSFER", false)
                }
                else -> Log.d(TAG, " ❌ Not linked (score=$score)")
            }
        }

        if (autoLinked) return

        // ============================================================
        // 2) MISSING CREDIT INFERENCE (wide window search)
        // ============================================================
        inferMissingCredit(tx)
    }


    // ------------------------------------------------------------
    // LINKING LOGIC
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

        Log.d(TAG, "LINK APPLIED → $linkId  [${a.id} ↔ ${b.id}] (type=$type)")
    }

    private suspend fun addLearnedPattern(tx: TransactionCoreModel) {
        if (!isDebitOrCredit(tx)) return

        val rawMerchant = tx.merchant ?: return
        val merchant = normalize(
            rawMerchant.replace("payzapp wallet", "") // prevent noise pollution
        )

        val phrase = extractPhrase(tx.body ?: return)
        val key = "$merchant|$phrase"

        Log.d(TAG, "Learning pattern: $key")
        repo.saveLinkedPattern(key)
    }


    // ------------------------------------------------------------
    // SCORING
    // ------------------------------------------------------------
    private fun scorePair(a: TransactionCoreModel, b: TransactionCoreModel): Int {

        if (!isOpposite(a.type, b.type)) {
            Log.d(TAG, "   block: same direction")
            return 0
        }

        if (a.amount != b.amount) return 0

        val merchantSim = similarity(a.merchant, b.merchant)
        if (merchantSim < 10) return 0

        var score = amountWeight

        val dayDiff = ((a.timestamp - b.timestamp).absoluteValue / DAY_MS)
        score += when (dayDiff) {
            0L -> dateWeightSameDay
            1L -> dateWeightOneDay
            else -> 0
        }

        score += oppositeDirWeight
        score += (merchantSim * nameSimWeight / 100)

        if (!a.sender.isNullOrBlank() && a.sender == b.sender) {
            score += bankMatchWeight
        }

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

        val normMerchant = normalize(tx.merchant ?: tx.sender ?: "")
        val phrase = extractPhrase(tx.body ?: "")
        val currentKey = "$normMerchant|$phrase"

        Log.d(TAG, "Current key=$currentKey")

        // which patterns to look for
        val expectedPatterns = when (tx.type?.lowercase()) {
            "debit" -> repo.getAllLinkedCreditPatterns()
            "credit" -> repo.getAllLinkedDebitPatterns()
            else -> emptySet()
        }

        Log.d(TAG, "Expected pattern count = ${expectedPatterns.size}")
        expectedPatterns.forEach { Log.d(TAG, " → ExpectedPattern = '$it'") }

        if (!expectedPatterns.contains(currentKey)) {
            Log.d(TAG, "❌ No matching opposite-pattern → skip inference.")
            return
        }

        Log.d(TAG, "✅ Opposite-pattern FOUND → searching wider window...")

        // wide window
        val window = inferenceWindowDays * DAY_MS
        val from = tx.timestamp - window
        val to = tx.timestamp + window

        val candidates = repo.findCandidates(tx.amount, from, to, tx.id)
        Log.d(TAG, "Found ${candidates.size} wide-window candidates")

        val filtered = candidates.filter { isOpposite(tx.type, it.type) }
            .filter { similarity(tx.merchant, it.merchant) >= 20 }

        Log.d(TAG, "Filtered to ${filtered.size} opposite-direction + merchant-sim candidates")

        val best = filtered
            .map { it to scorePair(tx, it) }
            .filter { it.second >= possibleLinkThreshold }
            .maxByOrNull { it.second }

        if (best == null) {
            Log.d(TAG, "❌ No strong inference candidate found.")
            return
        }

        val (match, score) = best
        Log.d(TAG, "✅ INFERRED MATCH → TX=${match.id}, score=$score")
        applyLink(tx, match, score, "POSSIBLE_TRANSFER", false)
    }


    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------

    private fun tsReadable(ts: Long?): String {
        if (ts == null) return "null"
        return try {
            val sdf = java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
            sdf.format(java.util.Date(ts))
        } catch (e: Exception) {
            ts.toString()
        }
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    /**
     * CRITICAL FIX:
     * DEBIT and CREDIT sides of person transfers
     * must map to the SAME phrase key → "person_transfer"
     */
    private fun extractPhrase(body: String): String {
        val b = body.lowercase()

        return when {
            // Unified person-to-person transfer
            (b.contains("transferred to") || b.contains("transfer to")) &&
                    b.contains("mr") ->
                "person_transfer"

            b.contains("deposit by transfer") ||
                    b.contains("deposit by") ||
                    b.contains("deposit from") ->
                "person_transfer"

            b.contains("credited") -> "credited_to"
            b.contains("debited") || b.contains("deducted") -> "debited_from"

            b.contains("payzapp") || b.contains("wallet") -> "wallet"

            else -> "other"
        }
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
        UUID.nameUUIDFromBytes(
            "${a.amount}-${a.timestamp}-${b.timestamp}-${a.merchant}".toByteArray()
        ).toString()

    private fun isDebitOrCredit(tx: TransactionCoreModel): Boolean {
        val t = tx.type?.lowercase() ?: return false
        return t == "debit" || t == "credit"
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
