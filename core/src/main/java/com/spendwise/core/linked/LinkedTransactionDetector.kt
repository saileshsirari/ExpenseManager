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
    private val possibleLinkThreshold: Int = 60
) {

    private val TAG = "LinkedDetector"

    suspend fun process(tx: TransactionCoreModel) {

        Log.d(TAG, "\n\n========== PROCESSING TX ${tx.id} ==========")
        Log.d(TAG, "type=${tx.type}, amount=${tx.amount}")
        Log.d(TAG, "sender=${tx.sender}, merchant=${tx.merchant}")
        Log.d(TAG, "timestamp=${tx.timestamp}")

        if (!isDebitOrCredit(tx)) {
            Log.d(TAG, "SKIP — Not debit/credit\n")
            return
        }

        var linked = false

        // -----------------------------------------------------
        // 1) NORMAL SAME-DAY LINKING
        // -----------------------------------------------------
        val from = tx.timestamp - DAY_MS
        val to = tx.timestamp + DAY_MS

        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = from,
            to = to,
            excludeId = tx.id
        )

        Log.d(TAG, "Found ${candidates.size} potential candidates (same-day window)")

        for (cand in candidates) {

            val score = scorePair(tx, cand)

            Log.d(TAG, "Score vs TX ${cand.id} = $score (type=${cand.type}, merchant=${cand.merchant}, sender=${cand.sender})")

            when {
                score >= autoLinkThreshold -> {
                    Log.d(TAG, "AUTO-LINKED as INTERNAL_TRANSFER (score=$score)\n")
                    applyLink(tx, cand, score, "INTERNAL_TRANSFER", true)
                    linked = true
                    break
                }

                score >= possibleLinkThreshold -> {
                    Log.d(TAG, "POSSIBLE LINK (score=$score)\n")
                    applyLink(tx, cand, score, "POSSIBLE_TRANSFER", false)
                    linked = true
                    // continue searching — could find an auto link candidate
                }

                else -> Log.d(TAG, "NOT LINKED (score=$score)\n")
            }
        }

        if (linked) return

        // -----------------------------------------------------
        // 2) MISSING CREDIT INFERENCE (try historical inference)
        // -----------------------------------------------------
        inferMissingCredit(tx)
    }

    // =========================================================================
    // INFER MISSING CREDIT (search historical patterns & larger window)
    // =========================================================================
    private suspend fun inferMissingCredit(tx: TransactionCoreModel) {
        Log.d(TAG, "Normal linking FAILED → Running missing-credit inference for TX ${tx.id}")

        // Debug header
        Log.d(TAG, "---- Missing Credit DEBUG for TX ${tx.id} ----")
        Log.d(TAG, "TX type=${tx.type}, amount=${tx.amount}, merchant=${tx.merchant}, sender=${tx.sender}")
        Log.d(TAG, "TX body=${tx.body ?: "(no body)"}")

        // 1) Load historical linked patterns (merchant+phrase WITHOUT bank prefix)
        val patterns = repo.getAllLinkedPatterns()
        Log.d(TAG, "Loaded ${patterns.size} historical patterns:")
        patterns.forEach { Log.d(TAG, "   pattern = $it") }

        // 2) Build current pattern key (merchant + phrase) — deliberately IGNORE bank/prefix
        val normMerchant = normalize(tx.merchant ?: tx.sender ?: "")
        val phrase = extractPhrase(tx.body ?: "")
        val currentKey = buildPatternKey(normMerchant, phrase)

        Log.d(TAG, "normalizeName('$normMerchant') = '$normMerchant'")
        Log.d(TAG, "extractPhrase('${tx.body ?: ""}') = '$phrase'")
        Log.d(TAG, "Current TX PatternKey = $currentKey")

        // 3) If we have matching historical pattern, try to find historical opposite-direction candidate
        if (!patterns.contains(currentKey)) {
            Log.d(TAG, "❌ NO MATCH for pattern key. This is why inference failed.")
            return
        }

        Log.d(TAG, "✅ Pattern match found. Searching historical candidates (wider window)")

        // widen window: +/- 120 days (configurable)
        val window = 120 * DAY_MS
        val histFrom = tx.timestamp - window
        val histTo = tx.timestamp + window

        val histCandidates = repo.findCandidates(
            amount = tx.amount,
            from = histFrom,
            to = histTo,
            excludeId = tx.id
        )

        Log.d(TAG, "Found ${histCandidates.size} historical candidates in +/-120d window")

        // Filter candidates by opposite direction and merchant similarity (looser)
        val filtered = histCandidates.filter { isOpposite(tx.type, it.type) }
            .filter { similarity(tx.merchant, it.merchant) >= 20 } // relaxed requirement

        Log.d(TAG, "Filtered to ${filtered.size} opposite-direction + merchant-sim candidates")

        var best: Pair<TransactionCoreModel, Int>? = null
        for (cand in filtered) {
            val score = scorePair(tx, cand)
            Log.d(TAG, "Historical score vs ${cand.id} = $score (type=${cand.type}, timestamp=${cand.timestamp})")
            if (best == null || score > best.second) best = Pair(cand, score)
        }

        if (best != null) {
            val (cand, score) = best
            // For inferred historical matches be conservative: require >= possibleLinkThreshold
            if (score >= possibleLinkThreshold) {
                Log.d(TAG, "✅ INFERRED LINK — applying POSSIBLE_TRANSFER (score=$score) to ${cand.id}")
                applyLink(tx, cand, score, "POSSIBLE_TRANSFER", false)
            } else {
                Log.d(TAG, "❌ Best historical match score=$score < possibleLinkThreshold. Not linking.")
            }
        } else {
            Log.d(TAG, "❌ No usable historical candidate found.")
        }
    }

    // =========================================================================
    // APPLY LINK
    // =========================================================================
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
        Log.d(TAG, "APPLIED LINK: $linkId to ${a.id} and ${b.id} (type=$type score=$score isNetZero=$isNetZero)")
    }

    // =========================================================================
    // PAIR SCORING
    // =========================================================================
    private fun scorePair(a: TransactionCoreModel, b: TransactionCoreModel): Int {

        // ---------- 1. Block same direction (must be opposite) ----------
        if (a.type?.lowercase() == b.type?.lowercase()) {
            Log.d(TAG, "BLOCKED: Same direction ${a.type} & ${b.type}")
            return 0
        }

        // ---------- 2. Amount mismatch (fast reject) ----------
        if (a.amount != b.amount) {
            Log.d(TAG, "BLOCKED: Amount mismatch")
            return 0
        }

        // ---------- 3. Merchant mismatch (fast reject) ----------
        val merchantSim = similarity(a.merchant, b.merchant)
        if (merchantSim < 10) {
            Log.d(TAG, "BLOCKED: Merchant mismatch '${a.merchant}' vs '${b.merchant}' (sim=$merchantSim)")
            return 0
        }

        var score = 0

        // ---------- 4. Amount match ----------
        score += amountWeight

        // ---------- 5. Date proximity ----------
        val dayDiff = ((a.timestamp - b.timestamp).absoluteValue / DAY_MS)
        score += when (dayDiff) {
            0L -> dateWeightSameDay
            1L -> dateWeightOneDay
            else -> 0
        }

        // ---------- 6. Opposite direction ----------
        if (isOpposite(a.type, b.type)) score += oppositeDirWeight

        // ---------- 7. Merchant similarity ----------
        score += (merchantSim * nameSimWeight / 100)

        // ---------- 8. Sender match (same bank) ----------
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

    // =========================================================================
    // PATTERN BUILDERS & HELPERS (no bank/prefix used)
    // =========================================================================
    private fun buildPatternKey(normMerchant: String, phrase: String): String {
        return "${normMerchant}|${phrase}"
    }

    private fun extractPhrase(body: String): String {
        val b = body.lowercase()

        // Normalize common phrases to limited set
        return when {
            b.contains("transferred to") || b.contains("transfer to") || b.contains("transferred") -> "transferred_to"
            b.contains("deposit by transfer") || b.contains("deposit by") || b.contains("deposit") || b.contains("credited by transfer") -> "deposit_from"
            b.contains("credited") || b.contains("credit of") || b.contains("credited to") -> "credited_to"
            b.contains("debited") || b.contains("deducted") || b.contains("deducted from") -> "debited_from"
            b.contains("paid") && b.contains("via") -> "paid_via"
            b.contains("deducted from payzapp") || b.contains("payzapp") || b.contains("wallet") -> "wallet_deduction"
            else -> "other"
        }
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    // =========================================================================
    // SIMILARITY FUNCTION
    // =========================================================================
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

        return ((intersect.toDouble() / union.toDouble()) * 100).toInt()
    }

    private fun generateLinkId(a: TransactionCoreModel, b: TransactionCoreModel): String {
        return UUID.nameUUIDFromBytes(
            "${a.amount}-${a.timestamp}-${b.timestamp}-${a.merchant}".toByteArray()
        ).toString()
    }

    private fun isDebitOrCredit(tx: TransactionCoreModel): Boolean {
        val t = tx.type?.lowercase() ?: return false
        return t == "debit" || t == "credit"
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
