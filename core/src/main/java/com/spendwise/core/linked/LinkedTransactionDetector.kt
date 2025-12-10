package com.spendwise.core.linked

import android.util.Log
import com.spendwise.core.model.TransactionCoreModel
import java.util.UUID
import kotlin.math.absoluteValue

class LinkedTransactionDetector(
    private val repo: LinkedTransactionRepository,

    // Weights
    private val amountWeight: Int = 60,
    private val dateWeightSameDay: Int = 15,
    private val dateWeightOneDay: Int = 10,
    private val oppositeDirWeight: Int = 10,
    private val nameSimWeight: Int = 10,
    private val bankMatchWeight: Int = 5,

    // Thresholds
    private val autoLinkThreshold: Int = 80,
    private val possibleLinkThreshold: Int = 60
) {

    private val TAG = "LinkedDetector"

    suspend fun process(tx: TransactionCoreModel) {
        Log.d(TAG, "\n\n--- PROCESSING TX ID=${tx.id} ---")
        Log.d(TAG, "type=${tx.type}, sender=${tx.sender}, amount=${tx.amount}")
        Log.d(TAG, "merchant=${tx.merchant}, category=${tx.category}")

        if (!isDebitOrCredit(tx)) return

        val from = tx.timestamp - DAY_MS
        val to = tx.timestamp + DAY_MS

        val candidates = repo.findCandidates(tx.amount, from, to, tx.id)

        for (cand in candidates) {

            val score = scorePair(tx, cand)

            Log.d(TAG, "Candidate ${cand.id} -> score=$score")

            when {
                score >= autoLinkThreshold -> {
                    applyLink(tx, cand, score, "INTERNAL_TRANSFER", true)
                    return
                }

                score >= possibleLinkThreshold -> {
                    applyLink(tx, cand, score, "POSSIBLE_TRANSFER", false)
                }
            }
        }
    }

    private suspend fun applyLink(
        a: TransactionCoreModel,
        b: TransactionCoreModel,
        score: Int,
        type: String,
        isNetZero: Boolean
    ) {
        val linkId = generateLinkId(a, b)
        Log.d(TAG, "LINKING ID=${a.id} <-> ${b.id} score=$score type=$type")

        repo.updateLink(a.id, linkId, type, score, isNetZero)
        repo.updateLink(b.id, linkId, type, score, isNetZero)
    }

    private fun isDebitOrCredit(tx: TransactionCoreModel): Boolean {
        val t = tx.type?.lowercase() ?: return false
        return t == "debit" || t == "credit"
    }

    private fun scorePair(a: TransactionCoreModel, b: TransactionCoreModel): Int {

        // ⭐ HARD BLOCK — FIXES all wrong PayZapp links
        val typeA = a.type?.lowercase()
        val typeB = b.type?.lowercase()

        if (typeA == typeB) {
            Log.d(TAG, "❌ BLOCKED SAME DIRECTION: $typeA & $typeB (duplicate or noise)")
            return 0
        }

        // Amount mismatch -> no link
        if (a.amount != b.amount) {
            Log.d(TAG, "Amount mismatch: ${a.amount} vs ${b.amount}")
            return 0
        }

        var score = 0

        // Amount always adds big weight
        score += amountWeight

        // Time proximity
        val dayDiff = ((a.timestamp - b.timestamp).absoluteValue / DAY_MS)
        score += when (dayDiff) {
            0L -> dateWeightSameDay
            1L -> dateWeightOneDay
            else -> 0
        }

        // Opposite direction bonus (only possible after hard-block)
        if (isOpposite(a.type, b.type)) {
            score += oppositeDirWeight
        }

        // Merchant similarity scaled 0..10
        score += (similarity(a.merchant, b.merchant) * nameSimWeight / 100)

        // Sender/bank match small bonus
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

    private fun similarity(a: String?, b: String?): Int {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 100

        val aTokens = na.split(" ").filter { it.isNotEmpty() }.toSet()
        val bTokens = nb.split(" ").filter { it.isNotEmpty() }.toSet()

        val intersect = (aTokens intersect bTokens).size
        val union = (aTokens union bTokens).size

        return if (union == 0) 0 else ((intersect.toDouble() / union) * 100).toInt()
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun generateLinkId(a: TransactionCoreModel, b: TransactionCoreModel): String {
        val raw = "${a.amount}-${a.timestamp}-${b.timestamp}-${a.merchant.orEmpty()}"
        return UUID.nameUUIDFromBytes(raw.toByteArray()).toString()
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
