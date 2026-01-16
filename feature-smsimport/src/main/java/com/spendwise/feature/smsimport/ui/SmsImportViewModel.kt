package com.spendwise.feature.smsimport.ui

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.domain.com.spendwise.feature.smsimport.data.CategoryTotal
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardUiState
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortConfig
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortField
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortOrder
import com.spendwise.domain.com.spendwise.feature.smsimport.data.categoryColorProvider
import com.spendwise.domain.com.spendwise.feature.smsimport.data.localDate
import com.spendwise.domain.com.spendwise.feature.smsimport.ui.InsightBlock
import com.spendwise.feature.smsimport.data.ImportEvent
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.data.isExpense
import com.spendwise.feature.smsimport.prefs.SmsImportPrefs
import com.spendwise.feature.smsimport.repo.SmsRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import com.spendwise.core.Logger as Log

@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val repo: SmsRepositoryImpl,
    private val prefs: SmsImportPrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _items = MutableStateFlow<List<SmsEntity>>(emptyList())
    val items: StateFlow<List<SmsEntity>> = _items
    private val _selectedExplanation = MutableStateFlow<MlReasonBundle?>(null)
    val selectedExplanation = _selectedExplanation
    private val _uiInputs = MutableStateFlow(UiInputs())
    private val uiInputs = _uiInputs.asStateFlow()
    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress = _importProgress.asStateFlow()
    private val _categoryTotalsAll = MutableStateFlow(emptyList<CategoryTotal>())
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro
    private val _showPaywall = MutableStateFlow(false)
    fun onUpgradeClicked() {
        _showPaywall.value = true
    }

    val categoryTotalsAll = _categoryTotalsAll.asStateFlow()
    val totalSpend = categoryTotalsAll
        .map { list -> list.sumOf { it.total } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            0.0
        )

    val topCategoriesFree: StateFlow<List<CategoryTotal>> =
        categoryTotalsAll
            .map { it.take(5) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emptyList()
            )

    val categoryInsight: StateFlow<InsightBlock.CategoryList> =
        combine(categoryTotalsAll, isPro) { categories, pro ->
            if (pro) {
                InsightBlock.CategoryList(
                    items = categories,
                    isLocked = false
                )
            } else {
                InsightBlock.CategoryList(
                    items = categories.take(5),
                    isLocked = true
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            InsightBlock.CategoryList(emptyList(), isLocked = false)
        )

    val monthlyComparison: StateFlow<InsightBlock.Comparison> =
        combine(totalSpend, isPro) { total, pro ->
            if (pro) {
                InsightBlock.Comparison(
                    title = "Compared to last month",
                    value = "₹${total.toInt()}",
                    delta = "+18%",
                    isLocked = false
                )
            } else {
                InsightBlock.Comparison(
                    title = "Compared to last month",
                    value = null,
                    delta = null,
                    isLocked = true
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            InsightBlock.Comparison("", null, null, false)
        )
    val walletInsight: StateFlow<InsightBlock.WalletInsight> =
        isPro
            .map { pro ->
                if (pro) {
                    InsightBlock.WalletInsight(
                        summary = "Wallets routed ₹4,200 this month",
                        isLocked = false
                    )
                } else {
                    InsightBlock.WalletInsight(
                        summary = null,
                        isLocked = true
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                InsightBlock.WalletInsight(null, false)
            )


    data class ImportProgress(
        val total: Int = 0,
        val processed: Int = 0,
        val message: String = "",
        val done: Boolean = false
    )

    data class UiInputs(
        val sortConfig: SortConfig = SortConfig(),
        val showGroupedMerchants: Boolean = true,
        val mode: DashboardMode = DashboardMode.MONTH,
        val period: YearMonth = YearMonth.now(),
        val selectedType: String? = null,
        val selectedDay: Int? = null,
        val selectedMonth: Int? = null,
        val isLoading: Boolean = true,
        val showIgnored: Boolean = false,
        val internalSectionCollapsed: Boolean = true
    )

    init {
        viewModelScope.launch {
            repo.getAll().collect { list ->
                Log.e("DB_FLOW", "Room emitted ${list.size} rows")
                _items.value = list
            }
        }
    }

    // FINAL UI STATE (all expensive computation happens here)
    val uiState: StateFlow<DashboardUiState> =
        combine(items, uiInputs) { list, input ->

            android.util.Log.d("expense", "input.mode ${input.mode}")

            // -----------------------------
            // 1) Base list — ALL items
            val baseAll = list

// 2) Period filter
            val periodFiltered = baseAll.filterByPeriod(input.mode, input.period)


// 4) Ignored visibility toggle (NEW)
            // 4) UI list — ALWAYS show ignored items
            val visibleList = periodFiltered


// 5) Category filter
            val finalList =
                if (input.selectedType == null) visibleList
                else visibleList.filter { it.category == input.selectedType }

// 6) Expense list (math-only)
            val expenseList = finalList.filter { it.isExpense() }


            // -----------------------------
            // 5) Recalculate category totals
            // -----------------------------
            recalcCategoryTotalsAll(visibleList)   // PIE
            recalcCategoryTotals(expenseList)     // LIST

            // -----------------------------
            // 6) Compute totals (Debit / Credit)
            // -----------------------------
            val totalDebit = expenseList.sumOf { it.amount }


            // Optional: credits usually informational only
            val totalCredit = finalList
                .filter { it.type.equals("CREDIT", true) && !it.isNetZero }
                .sumOf { it.amount }

            val debitCreditTotals = mapOf(
                "DEBIT" to totalDebit,
                "CREDIT" to totalCredit
            ).filter { it.value > 0 }

            // -----------------------------
            // 7) Compute bar chart
            // -----------------------------
            val barData = when (input.mode) {
                DashboardMode.MONTH ->
                    expenseList.groupBy { it.localDate().dayOfMonth }
                        .mapValues { it.value.sumOf { tx -> tx.amount } }

                DashboardMode.QUARTER,
                DashboardMode.YEAR ->
                    expenseList.groupBy { it.localDate().monthValue }
                        .mapValues { it.value.sumOf { tx -> tx.amount } }
            }


            // -----------------------------
            // 8) Sorting
            // -----------------------------

            // -----------------------------
// 8) Sorting (base, semantic split happens AFTER this)
// -----------------------------
            val sortedTxs = sortTransactions(finalList, input.sortConfig)

// 🔒 Split by semantic meaning
            val (internalTxs, normalTxs) =
                sortedTxs.partition { it.isNetZero }

// -----------------------------
// 9) MAIN rows (expenses + income)
// -----------------------------
            val mainRows = buildUiRows(
                txs = normalTxs,
                sortConfig = input.sortConfig,
                groupByMerchant = input.showGroupedMerchants
            )

            val sortedMainRows = mainRows

// -----------------------------
// 10) INTERNAL TRANSFER section
// -----------------------------
            val internalSectionRows =
                if (internalTxs.isEmpty()) {
                    emptyList()
                } else {
                    val internalGrouped = buildUiRows(
                        txs = internalTxs,
                        sortConfig = input.sortConfig,
                        groupByMerchant = input.showGroupedMerchants
                    )

                    val sortedInternal =
                        if (input.showGroupedMerchants)
                            sortUiRows(internalGrouped, input.sortConfig)
                        else
                            internalGrouped

                    listOf(
                        UiTxnRow.Section(
                            id = "internal_transfers",
                            title = "Internal transfers",
                            count = internalTxs.size,
                            collapsed = input.internalSectionCollapsed
                        )
                    ) + sortedInternal
                }

// -----------------------------
// 11) FINAL rows
// -----------------------------
            val finalRows = sortedMainRows + internalSectionRows


// -----------------------------
// 9) UI grouping (wallet merchant spends)
// -----------------------------

            // -----------------------------
            // 9) UI State
            // -----------------------------
            DashboardUiState(
                mode = input.mode,
                period = input.period,
                rows = finalRows,              // 👈 NEW
                selectedType = input.selectedType,
                selectedDay = input.selectedDay,
                selectedMonth = input.selectedMonth,
                sortConfig = input.sortConfig,
                showIgnored = input.showIgnored,
                finalList = finalList,
                sortedList = sortedTxs,
                totalsDebit = totalDebit,
                totalsCredit = totalCredit,
                debitCreditTotals = debitCreditTotals,
                showGroupedMerchants = input.showGroupedMerchants,
                barData = barData,
                isLoading = input.isLoading
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                DashboardUiState()
            )

    private fun sortUiRows(
        rows: List<UiTxnRow>,
        config: SortConfig
    ): List<UiTxnRow> {

        // 1️⃣ Split into chunks separated by Section headers
        val result = mutableListOf<UiTxnRow>()
        var buffer = mutableListOf<UiTxnRow>()

        fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                result += sortDataRows(buffer, config)
                buffer.clear()
            }
        }

        for (row in rows) {
            when (row) {
                is UiTxnRow.Section -> {
                    flushBuffer()
                    result += row       // keep section exactly here
                }

                else -> buffer += row // Normal or Grouped
            }
        }

        flushBuffer()
        return result
    }

    private fun sortDataRows(
        rows: List<UiTxnRow>,
        config: SortConfig
    ): List<UiTxnRow> {

        fun valueFor(row: UiTxnRow, field: SortField): Double =
            when (field) {
                SortField.AMOUNT ->
                    when (row) {
                        is UiTxnRow.Grouped -> row.netAmount
                        is UiTxnRow.Normal -> row.tx.amount
                        else -> 0.0
                    }

                SortField.DATE ->
                    when (row) {
                        is UiTxnRow.Grouped ->
                            row.children.first().timestamp.toDouble()

                        is UiTxnRow.Normal ->
                            row.tx.timestamp.toDouble()

                        else -> 0.0
                    }
            }

        fun compare(
            a: UiTxnRow,
            b: UiTxnRow,
            field: SortField,
            order: SortOrder
        ): Int {
            val result =
                valueFor(a, field).compareTo(valueFor(b, field))
            return if (order == SortOrder.ASC) result else -result
        }

        return rows.sortedWith { a, b ->
            val primary = compare(a, b, config.primary, config.primaryOrder)
            if (primary != 0) primary
            else compare(a, b, config.secondary, config.secondaryOrder)
        }
    }

    fun toggleInternalSection() {
        update { state ->
            state.copy(
                internalSectionCollapsed = !state.internalSectionCollapsed
            )
        }
    }

    private fun buildUiRows(
        txs: List<SmsEntity>,
        sortConfig: SortConfig,
        groupByMerchant: Boolean
    ): List<UiTxnRow> {

        if (!groupByMerchant) {
            return txs.map { UiTxnRow.Normal(it) }
        }

        val result = mutableListOf<UiTxnRow>()
        val buffer = mutableListOf<SmsEntity>()

        fun flush() {
            if (buffer.isEmpty()) return

            val first = buffer.first()

            // 🔒 If merchant is null OR only one item → normal row(s)
            if (
                first.merchant == null ||
                buffer.size == 1 ||
                first.isNetZero
            ) {
                buffer.forEach { tx ->
                    result += UiTxnRow.Normal(tx)
                }
            } else {
                val effective = buffer.filterNot { it.isIgnored }

                val totalDebit = effective
                    .filter { it.type.equals("DEBIT", true) }
                    .sumOf { it.amount }

                val totalCredit = effective
                    .filter { it.type.equals("CREDIT", true) }
                    .sumOf { it.amount }

                val netAmount = totalCredit - totalDebit

                result += UiTxnRow.Grouped(
                    groupId = "${first.merchant}-${first.timestamp}",
                    title = first.merchant,   // ✅ safe now
                    count = buffer.size,
                    netAmount = netAmount,
                    totalAmount = buffer.sumOf { it.amount },
                    children = buffer.toList()
                )
            }

            buffer.clear()
        }


        for (tx in txs) {
            val sameMerchant =
                buffer.isNotEmpty() &&
                        buffer.first().merchant != null &&
                        tx.merchant != null &&
                        buffer.first().merchant == tx.merchant &&
                        !buffer.first().isNetZero &&
                        !tx.isNetZero


            val shouldGroup =
                when (sortConfig.primary) {
                    SortField.AMOUNT -> sameMerchant   // ignore date
                    SortField.DATE -> sameMerchant     // date already handled by sort
                }

            if (shouldGroup) {
                buffer += tx
            } else {
                flush()
                buffer += tx
            }
        }
        flush()
        return result
    }

    fun toggleGroupByMerchant() =
        update { it.copy(showGroupedMerchants = !it.showGroupedMerchants) }

    // ---- Expose input update functions ----
    fun toggleShowIgnored() =
        update { it.copy(showIgnored = !it.showIgnored) }

    fun updateSort(sort: SortConfig) = update { it.copy(sortConfig = sort) }

    fun setMode(newMode: DashboardMode) {
        update {
            if (it.mode == newMode) it
            else it.copy(
                mode = newMode,
                selectedType = null,      // reset category filter
                selectedDay = null,
                selectedMonth = null
            )
        }
    }

    fun setPeriod(p: YearMonth) = update { it.copy(period = p) }
    fun setSelectedType(t: String?) = update { it.copy(selectedType = t) }
    fun setSelectedDay(d: Int?) = update { it.copy(selectedDay = d) }
    fun setSelectedMonth(m: Int?) = update { it.copy(selectedMonth = m) }

    private fun update(block: (UiInputs) -> UiInputs) {
        _uiInputs.value = block(_uiInputs.value)
    }

    suspend fun importAll(resolverProvider: () -> ContentResolver) {

        repo.importAll(resolverProvider).collect { event ->

            when (event) {

                is ImportEvent.Progress -> {
                    _importProgress.value = ImportProgress(
                        total = event.total,
                        processed = event.processed,
                        message = event.message,
                        done = false
                    )
                }

                is ImportEvent.Finished -> {
                    _items.value = event.list
                    prefs.importCompleted = true
                    update { it.copy(isLoading = false) }
                    _importProgress.value = _importProgress.value.copy(done = true)
                }


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


    fun addManualExpense(
        amount: Double,
        merchant: String,
        category: CategoryType,
        date: LocalDate,
        note: String
    ) {
        viewModelScope.launch {
            repo.saveManualExpense(amount, merchant, category, date, note)
            // 🔥 CRITICAL FIX
            update {
                it.copy(
                    mode = DashboardMode.MONTH,
                    period = YearMonth.from(date)
                )
            }
        }
    }


    fun SmsEntity.isCountedAsExpense(): Boolean {
        return type == "DEBIT" &&
                !isIgnored &&
                !isNetZero
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
            refresh()
            // NO refresh() needed if repo.getAll() is a Flow
        }
    }


    fun startImportIfNeeded1(resolverProvider: () -> ContentResolver) {
        viewModelScope.launch(Dispatchers.Default) {

            if (!prefs.importCompleted) {
                // 1) Start loading
                update { it.copy(isLoading = true) }
                _importProgress.value = ImportProgress(done = false)

                // 2) Start import
                importAll(resolverProvider)

            } else {
                // 1) Start loading
                // repo.reclassifyAll()

                update { it.copy(isLoading = true) }
                // 2) Start import
                loadExistingData()
            }
        }
    }

    fun startImportIfNeeded(resolverProvider: () -> ContentResolver) {
        viewModelScope.launch {

            update { it.copy(isLoading = true) }

            if (!prefs.importCompleted) {
                _importProgress.value = ImportProgress(done = false)

                importAll(resolverProvider)   // ← SUSPEND, awaited

            } else {
                repo.importIncremental(resolverProvider).collect { event ->
                    when (event) {
                        is ImportEvent.Progress -> {
                            _importProgress.value = ImportProgress(
                                total = event.total,
                                processed = event.processed,
                                message = event.message,
                                done = false
                            )
                        }

                        is ImportEvent.Finished -> {
                            _items.value = event.list
                            _importProgress.value =
                                _importProgress.value.copy(done = true)
                        }
                    }
                }

            }

            update { it.copy(isLoading = false) }
        }
    }


    fun loadExistingData() {
        viewModelScope.launch {
            val list = repo.loadExisting()
            _items.value = list

            update { it.copy(isLoading = false) }

            // Required for second+ launch (no import progress)
            _importProgress.value = _importProgress.value.copy(done = true)
        }
    }


    private val _categoryTotals = MutableStateFlow(emptyList<CategoryTotal>())
    val categoryTotals = _categoryTotals.asStateFlow()

    // --- Convenience helpers to clear or set selected filter (if needed) ---
    fun clearSelectedType() {
        update { it.copy(selectedType = null) } // use your existing update(...) method
        // optionally trigger recompute if you don't recompute from combine automatically
    }

    fun setSelectedTypeSafe(type: String?) {
        update { it.copy(selectedType = type) }
        // The combine block should react to uiInputs.selectedType and recompute finalList / totals
    }

    private fun recalcCategoryTotalsAll(list: List<SmsEntity>) {

        val expenseList = list.filter { it.isCountedAsExpense() }

        _categoryTotalsAll.value =
            expenseList
                .groupBy { it.category ?: "Uncategorized" }
                .map { (cat, txs) ->
                    CategoryTotal(
                        name = cat,
                        total = txs.sumOf { it.amount },
                        color = categoryColorProvider(cat)
                    )
                }
                .sortedByDescending { it.total }
    }

    private fun recalcCategoryTotals(list: List<SmsEntity>) {
        _categoryTotals.value =
            list.groupBy { it.category ?: "Other" }
                .map { (cat, items) ->
                    CategoryTotal(
                        name = cat,
                        total = items.sumOf { it.amount },
                        color = categoryColorProvider(cat)
                    )
                }
                .sortedByDescending { it.total }
    }

    fun markAsSelfTransfer(tx: SmsEntity) {
        viewModelScope.launch {
            repo.markAsSelfTransfer(tx)
            refresh()   // reload list + recompute UI state
        }
    }

    fun undoSelfTransfer(tx: SmsEntity) {
        viewModelScope.launch {
            repo.undoSelfTransfer(tx)
            refresh()
        }
    }

}

sealed class UiTxnRow {
    data class Normal(val tx: SmsEntity) : UiTxnRow()

    data class Grouped(
        val groupId: String,
        val title: String,             // same as merchant
        val count: Int,
        val netAmount: Double,
        val totalAmount: Double,
        val children: List<SmsEntity>
    ) : UiTxnRow()

    /** Section header row (for Internal Transfers) */
    data class Section(
        val id: String,              // e.g. "internal_transfers"
        val title: String,           // "Internal transfers"
        val count: Int,
        val collapsed: Boolean
    ) : UiTxnRow()
}

private fun sortTransactions(
    list: List<SmsEntity>,
    config: SortConfig
): List<SmsEntity> {

    fun compareByField(
        a: SmsEntity,
        b: SmsEntity,
        field: SortField,
        order: SortOrder
    ): Int {
        val result = when (field) {
            SortField.DATE ->
                a.timestamp.compareTo(b.timestamp)

            SortField.AMOUNT ->
                a.amount.compareTo(b.amount)
        }

        return if (order == SortOrder.ASC) result else -result
    }

    return list.sortedWith { a, b ->
        val primaryResult =
            compareByField(a, b, config.primary, config.primaryOrder)

        if (primaryResult != 0) {
            primaryResult
        } else {
            compareByField(a, b, config.secondary, config.secondaryOrder)
        }
    }
}


private fun SmsEntity.isWalletMovement(): Boolean {
    val m = merchant ?: return false

    return m.contains("wallet", ignoreCase = true)
            && !isExpense()          // 🔒 exclude real spends
}


private fun List<SmsEntity>.filterByPeriod(
    mode: DashboardMode,
    period: YearMonth
): List<SmsEntity> {

    return when (mode) {

        DashboardMode.MONTH -> {
            filter {
                val date = Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                date.year == period.year && date.monthValue == period.monthValue
            }
        }

        DashboardMode.QUARTER -> {
            val startMonth = ((period.monthValue - 1) / 3) * 3 + 1
            val quarterStart = YearMonth.of(period.year, startMonth)
            val quarterEnd = quarterStart.plusMonths(2)

            filter {
                val date = Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                val ym = YearMonth.of(date.year, date.monthValue)
                ym >= quarterStart && ym <= quarterEnd
            }
        }

        DashboardMode.YEAR -> {
            filter {
                val date = Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                date.year == period.year
            }
        }
    }


}

