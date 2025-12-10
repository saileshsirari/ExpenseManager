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

        val from = tx.timestamp - DAY_MS
        val to = tx.timestamp + DAY_MS

        val candidates = repo.findCandidates(
            amount = tx.amount,
            from = from,
            to = to,
            excludeId = tx.id
        )

        Log.d(TAG, "Found ${candidates.size} potential candidates")

        for (cand in candidates) {

            val score = scorePair(tx, cand)

            Log.d(TAG, "Score vs TX ${cand.id} = $score")

            when {
                score >= autoLinkThreshold -> {
                    Log.d(TAG, "AUTO-LINKED as INTERNAL_TRANSFER\n")
                    applyLink(tx, cand, score, "INTERNAL_TRANSFER", true)
                    return
                }

                score >= possibleLinkThreshold -> {
                    Log.d(TAG, "POSSIBLE LINK\n")
                    applyLink(tx, cand, score, "POSSIBLE_TRANSFER", false)
                }

                else -> Log.d(TAG, "NOT LINKED\n")
            }
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
    }

    // =========================================================================
    // PAIR SCORING
    // =========================================================================
    private fun scorePair(a: TransactionCoreModel, b: TransactionCoreModel): Int {

        // ---------- 1. Block same direction (critical fix) ----------
        if (a.type?.lowercase() == b.type?.lowercase()) {
            Log.d(TAG, "BLOCKED: Same direction ${a.type} & ${b.type}")
            return 0
        }

        // ---------- 2. Amount mismatch (fast reject) ----------
        if (a.amount != b.amount) {
            Log.d(TAG, "BLOCKED: Amount mismatch")
            return 0
        }

        // ---------- 3. Merchant mismatch (critical fix) ----------
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

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

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
