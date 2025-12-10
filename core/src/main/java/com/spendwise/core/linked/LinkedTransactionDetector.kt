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

        Log.d(TAG, "--- TX ${tx.id} --- ${tx.type} amt=${tx.amount}")

        val tNorm = tx.type?.trim()?.lowercase()
        Log.d(TAG, "typeNorm=$tNorm sender=${tx.sender}")

        if (!isDebitOrCredit(tx)) {
            Log.d(TAG, "❌ Skipped (not debit/credit)")
            return
        }

        val from = tx.timestamp - DAY_MS
        val to = tx.timestamp + DAY_MS

        val candidates = repo.findCandidates(tx.amount, from, to, tx.id)

        Log.d(TAG, "candidates=${candidates.size}")

        for (cand in candidates) {

            val score = compactScore(tx, cand)

            Log.d(TAG, "→ cand=${cand.id} t=${cand.type} score=$score")

            when {
                score >= autoLinkThreshold -> {
                    Log.d(TAG, "✔ AUTO LINK ${tx.id} ↔ ${cand.id}")
                    applyLink(tx, cand, score, "INTERNAL_TRANSFER", true)
                    return
                }
                score >= possibleLinkThreshold -> {
                    Log.d(TAG, "⚠ POSSIBLE LINK ${tx.id} ↔ ${cand.id}")
                    applyLink(tx, cand, score, "POSSIBLE_TRANSFER", false)
                }
                else -> {
                    Log.d(TAG, "no match (score=$score)")
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
        repo.updateLink(a.id, linkId, type, score, isNetZero)
        repo.updateLink(b.id, linkId, type, score, isNetZero)
    }

    private fun compactScore(a: TransactionCoreModel, b: TransactionCoreModel): Int {
        var score = 0

        if (a.amount != b.amount) return 0

        score += amountWeight

        val dayDiff = ((a.timestamp - b.timestamp).absoluteValue / DAY_MS)
        score += when (dayDiff) {
            0L -> dateWeightSameDay
            1L -> dateWeightOneDay
            else -> 0
        }

        if (isOpposite(a.type, b.type)) score += oppositeDirWeight

        val sim = similarity(a.merchant, b.merchant)
        score += (sim * nameSimWeight / 100)

        if (a.sender == b.sender) score += bankMatchWeight

        return score
    }

    private fun isDebitOrCredit(tx: TransactionCoreModel): Boolean {
        val t = tx.type?.trim()?.lowercase() ?: return false
        return t == "debit" || t == "credit"
    }

    private fun isOpposite(a: String?, b: String?): Boolean {
        val x = a?.trim()?.lowercase()
        val y = b?.trim()?.lowercase()
        return (x == "debit" && y == "credit") || (x == "credit" && y == "debit")
    }

    private fun similarity(a: String?, b: String?): Int {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 100
        val aSet = na.split(" ").filter { it.isNotEmpty() }.toSet()
        val bSet = nb.split(" ").filter { it.isNotEmpty() }.toSet()
        val intersect = aSet.intersect(bSet).size
        val union = aSet.union(bSet).size
        return ((intersect.toDouble() / union.toDouble()) * 100).toInt()
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun generateLinkId(a: TransactionCoreModel, b: TransactionCoreModel): String {
        return UUID.nameUUIDFromBytes(
            "${a.amount}-${a.timestamp}-${b.timestamp}-${a.merchant ?: ""}".toByteArray()
        ).toString()
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
