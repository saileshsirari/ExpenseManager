package com.spendwise.feature.smsimport.ui

import android.os.Build
import com.spendwise.core.Logger as Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.repo.SmsRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SmsImportViewModel @Inject constructor(private val repo: SmsRepositoryImpl) : ViewModel() {
    private val _items = MutableStateFlow<List<SmsEntity>>(emptyList())
    val items: StateFlow<List<SmsEntity>> = _items
    private val _selectedExplanation = MutableStateFlow<MlReasonBundle?>(null)
    val selectedExplanation = _selectedExplanation

    fun importAll(resolverProvider: () -> android.content.ContentResolver) {
        viewModelScope.launch {
            repo.importAll(resolverProvider).collect { list ->
                _items.value = list
            }
        }
    }

    fun onMessageClicked(tx: SmsEntity) {
        viewModelScope.launch {

            // 1️⃣ Print the clicked SMS raw body
            Log.d("expense", "\n------ CLICKED SMS ------")
            Log.d("expense", tx.body)
            Log.d(
                "expense",
                "raw-linkId" + tx.linkId + " tx.linkType " + tx.linkType + " tx.isNetZero " + tx.isNetZero
            )

            // 2️⃣ If linked, print the paired raw SMS too
            if (tx.linkId != null) {
                val all = repo.getAllOnce()  // safe, synchronous DB read
                val linked = all.filter { it.linkId == tx.linkId && it.id != tx.id }

                Log.d("expense", "\n------ LINKED PAIR (${linked.size}) ------")
                linked.forEach { pair ->
                    Log.d("expense", "ID=${pair.id}")
                    Log.d("expense", pair.body)
                    Log.d("expense", "---------------------" + pair.linkConfidence)
                }
            } else {
                Log.d("expense", "\n(No linked SMS)")
            }

            Log.d("expense", "----------------------\n")

            // 3️⃣ Show ML explanation (already existing)
            val log = repo.getMlExplanationFor(tx)
            Log.d("expense", log.toString())
            _selectedExplanation.value = log
        }
    }

    fun fixMerchant(tx: SmsEntity, newMerchant: String) {
        viewModelScope.launch {

            // 1️⃣ Normalize the CURRENT merchant value
            val originalNorm = MerchantExtractorMl.normalize(tx.merchant ?: tx.sender)

            // 2️⃣ Build correct key
            val key = "merchant:$originalNorm"    // must be lowercase normalized

            Log.d("expense", "Saving override: $key -> ${newMerchant.trim()}")

            // 3️⃣ Save override (repo expects already-normalized key)
            repo.saveMerchantOverride(originalNorm, newMerchant.trim())

            // 4️⃣ Re-run ML for this one transaction
            repo.reclassifySingle(tx.id)

            // 5️⃣ Refresh UI
            refresh()
        }

    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun getMonthlySummary(
        items: List<SmsEntity>,
        month: YearMonth
    ): MonthlySummary {

        if (items.isEmpty()) return MonthlySummary()

        // Compute month range
        val zone = ZoneId.systemDefault()

        val startMillis = month
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val endMillis = month
            .atEndOfMonth()
            .atTime(LocalTime.MAX)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        Log.d("expense", "Example SMS ts=${items.first().timestamp}")
        Log.d(
            "expense", "As date=" + Instant.ofEpochMilli(items.first().timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        )
        // Filter entries strictly within this month
        val monthly = items.filter { it.timestamp in startMillis..endMillis }

        if (monthly.isEmpty()) return MonthlySummary()

        // Daily totals (1..31)
        val dailyTotals = mutableMapOf<Int, Double>()

        val categoryTotals = mutableMapOf<String, Double>()

        monthly.forEach { tx ->
            val localDate = Instant.ofEpochMilli(tx.timestamp)
                .atZone(zone)
                .toLocalDate()

            // DAY OF MONTH
            val day = localDate.dayOfMonth
            dailyTotals[day] = (dailyTotals[day] ?: 0.0) + tx.amount

            // CATEGORY
            val cat = tx.type ?: "Other"
            categoryTotals[cat] = (categoryTotals[cat] ?: 0.0) + tx.amount
        }

        val totalSpent = categoryTotals.values.sum()

        return MonthlySummary(
            total = totalSpent,
            categoryTotals = categoryTotals,
            dailyTotals = dailyTotals
        )
    }


    data class MonthlySummary(
        val total: Double = 0.0,
        val categoryTotals: Map<String, Double> = emptyMap(),
        val dailyTotals: Map<Int, Double> = emptyMap()
    )

    fun refresh() {
        viewModelScope.launch {
            repo.getAll().collect { list ->
                _items.value = list
            }
        }
    }

    fun markNotExpense(tx: SmsEntity) {
        viewModelScope.launch {
            repo.markIgnored(tx)
            refresh()
        }
    }


    fun addManualExpense(
        amount: Double,
        merchant: String,
        category: CategoryType,
        date: LocalDate,
        note: String
    ) {
        viewModelScope.launch {
            repo.saveManualExpense(
                amount = amount,
                merchant = merchant,
                category = category,
                date = date,
                note = note
            )
            refresh()
        }
    }

    fun fixCategory(tx: SmsEntity, newCategory: CategoryType) {
        viewModelScope.launch {
            repo.saveCategoryOverride(tx.merchant ?: tx.sender, newCategory.name)
            repo.reclassifySingle(tx.id)
            refresh()
        }
    }

    fun setIgnoredState(tx: SmsEntity, ignored: Boolean) {
        viewModelScope.launch {
            repo.setIgnored(tx.id, ignored)
            refresh()   // reload list
        }
    }


}
