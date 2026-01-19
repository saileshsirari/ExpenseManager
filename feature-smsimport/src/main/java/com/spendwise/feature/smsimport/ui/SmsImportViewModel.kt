package com.spendwise.feature.smsimport.ui

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.core.com.spendwise.core.ExpenseFrequency
import com.spendwise.core.com.spendwise.core.FrequencyFilter
import com.spendwise.core.com.spendwise.core.detector.LINK_TYPE_INVESTMENT_OUTFLOW
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
import com.spendwise.feature.smsimport.data.isWalletTopUp
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
import kotlin.math.roundToInt
import com.spendwise.core.Logger as Log

@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val repo: SmsRepositoryImpl,
    private val prefs: SmsImportPrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {


    companion object {
        const val FREE_LIMIT = 5
        private const val FREE_CATEGORY_LIMIT = 5

    }

    private val _items = MutableStateFlow<List<SmsEntity>>(emptyList())
    val items: StateFlow<List<SmsEntity>> = _items
    private val _selectedExplanation = MutableStateFlow<MlReasonBundle?>(null)
    val selectedExplanation = _selectedExplanation
    val mainSectionCollapsed: Boolean = false
    private val _uiInputs = MutableStateFlow(UiInputs())
    private val uiInputs = _uiInputs.asStateFlow()
    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress = _importProgress.asStateFlow()
    private val _insightsFrequencyFilter =
        MutableStateFlow(FrequencyFilter.MONTHLY_ONLY)

    val insightsFrequencyFilter =
        _insightsFrequencyFilter.asStateFlow()

    fun setInsightsFrequencyFilter(filter: FrequencyFilter) {
        _insightsFrequencyFilter.value = filter
    }


    private val _isPro = MutableStateFlow(true)
    val isPro: StateFlow<Boolean> = _isPro
    private val _showPaywall = MutableStateFlow(false)
    fun onUpgradeClicked() {
        _showPaywall.value = true
    }

    val categoryTotalsForPeriod: StateFlow<List<CategoryTotal>> =
        combine(items, uiInputs, insightsFrequencyFilter) { list, input, freqFilter ->

            list
                .filterByPeriod(input.mode, input.period)
                .filter { it.isCountedAsExpense() }
                .filter { it.matchesFrequency(freqFilter) }
                .groupBy { it.category ?: "Uncategorized" }
                .map { (cat, txs) ->
                    CategoryTotal(
                        name = cat,
                        total = txs.sumOf { it.amount },
                        color = categoryColorProvider(cat)
                    )
                }
                .sortedByDescending { it.total }


        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    val totalSpend = categoryTotalsForPeriod
        .map { list -> list.sumOf { it.total } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            0.0
        )

    val topCategoriesFree: StateFlow<List<CategoryTotal>> =
        categoryTotalsForPeriod
            .map { it.take(5) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emptyList()
            )
    private fun previousPeriod(
        mode: DashboardMode,
        period: YearMonth
    ): YearMonth =
        when (mode) {
            DashboardMode.MONTH -> period.minusMonths(1)
            DashboardMode.QUARTER -> period.minusMonths(3)
            DashboardMode.YEAR -> period.minusYears(1)
        }

    val categoryInsight: StateFlow<InsightBlock.CategoryList> =
        combine(categoryTotalsForPeriod, isPro) { categories, pro ->
            val shouldLock = !pro && categories.size > FREE_CATEGORY_LIMIT
            InsightBlock.CategoryList(
                items =
                    if (shouldLock) categories.take(FREE_CATEGORY_LIMIT)
                    else categories,
                isLocked = shouldLock
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            InsightBlock.CategoryList(emptyList(), isLocked = false)
        )
    private val _reclassifyProgress =
        MutableStateFlow<Pair<Int, Int>?>(null)

    val reclassifyProgress: StateFlow<Pair<Int, Int>?> =
        _reclassifyProgress.asStateFlow()

    fun triggerReclassification() {

        viewModelScope.launch(Dispatchers.Default) {

            // Force UI to show progress immediately
            viewModelScope.launch(Dispatchers.Main) {
                _reclassifyProgress.value = 0 to 0
            }

            repo.reclassifyAllWithProgress { done, total ->
                // Hop to Main safely from callback
                viewModelScope.launch(Dispatchers.Main) {
                    _reclassifyProgress.value = done to total
                }
            }

            // Clear progress at the end
            viewModelScope.launch(Dispatchers.Main) {
                _reclassifyProgress.value = null
            }


        }
    }



    fun prevPeriod() {
        update {
            it.copy(
                period = when (it.mode) {
                    DashboardMode.MONTH -> it.period.minusMonths(1)
                    DashboardMode.QUARTER -> it.period.minusMonths(3)
                    DashboardMode.YEAR -> it.period.minusYears(1)
                },
                selectedType = null,
                selectedDay = null,
                selectedMonth = null
            )
        }
    }
    fun toggleMainSection() {
        update {
            it.copy(mainSectionCollapsed = !it.mainSectionCollapsed)
        }
    }

    fun toggleInternalSection() {
        update {
            it.copy(internalSectionCollapsed = !it.internalSectionCollapsed)
        }
    }

    fun nextPeriod() {
        update {
            it.copy(
                period = when (it.mode) {
                    DashboardMode.MONTH -> it.period.plusMonths(1)
                    DashboardMode.QUARTER -> it.period.plusMonths(3)
                    DashboardMode.YEAR -> it.period.plusYears(1)
                },
                selectedType = null,
                selectedDay = null,
                selectedMonth = null
            )
        }
    }
    fun resetToCurrentMonth() {
        val now = YearMonth.now()

        update {
            if (
                it.mode == DashboardMode.MONTH &&
                it.period == now
            ) it
            else it.copy(
                mode = DashboardMode.MONTH,
                period = now,
                selectedType = null,
                selectedDay = null,
                selectedMonth = null
            )
        }
    }

    private fun YearMonth.previous(): YearMonth =
        this.minusMonths(1)

    /**
     * Total spend for previous month (expenses only)
     */
    val previousPeriodTotal: StateFlow<Double> =
        combine(items, uiInputs) { list, input ->

            val prev = previousPeriod(input.mode, input.period)

            list
                .filterByPeriod(input.mode, prev)
                .filter { it.isExpense() }
                .sumOf { it.amount }

        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            0.0
        )

    val periodComparison: StateFlow<InsightBlock.Comparison> =
        combine(items, uiInputs, isPro) { list, input, pro ->

            val effectiveFilter =
                if (input.mode == DashboardMode.YEAR)
                    FrequencyFilter.ALL_EXPENSES
                else
                    FrequencyFilter.MONTHLY_ONLY

            fun totalFor(period: YearMonth): Double =
                list
                    .filterByPeriod(input.mode, period)
                    .filter { it.isCountedAsExpense() }
                    .filter { it.matchesFrequency(effectiveFilter) }
                    .sumOf { it.amount }

            val current = totalFor(input.period)
            val prevPeriod = previousPeriod(input.mode, input.period)
            val previous = totalFor(prevPeriod)

            val hasData = previous > 0.0
            val delta =
                if (hasData) ((current - previous) / previous * 100).roundToInt()
                else 0

            val title = when (input.mode) {
                DashboardMode.MONTH -> "Compared to last month"
                DashboardMode.QUARTER -> "Compared to last quarter"
                DashboardMode.YEAR -> "Compared to last year"
            }

            when {
                !hasData -> InsightBlock.Comparison(
                    title = title,
                    value = "No previous data",
                    delta = null,
                    isLocked = false
                )

                pro -> InsightBlock.Comparison(
                    title = title,
                    value = "₹${current.toInt()}",
                    delta = "${if (delta >= 0) "+" else ""}$delta%",
                    isLocked = false
                )

                else -> InsightBlock.Comparison(
                    title = title,
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



    /**
     * Total amount routed via wallets in current period
     */
    val walletRoutedAmount: StateFlow<Double> =
        combine(items, uiInputs) { list, input ->

            list
                .filterByPeriod(input.mode, input.period)
                .filter { it.isWalletTopUp() }
                .sumOf { it.amount }

        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            0.0
        )

    val walletInsight: StateFlow<InsightBlock.WalletInsight> =
        combine(
            walletRoutedAmount,   // total routed via wallets
            isPro
        ) { walletAmount, pro ->

            val hasWalletActivity = walletAmount > 0.0

            when {
                // No wallet usage → show unlocked explanation
                !hasWalletActivity -> InsightBlock.WalletInsight(
                    summary = "No wallet activity this period",
                    isLocked = false
                )

                // Pro user → full insight
                pro -> InsightBlock.WalletInsight(
                    summary = "₹${walletAmount.toInt()} routed via wallets",
                    isLocked = false
                )

                // Free user → preview
                else -> InsightBlock.WalletInsight(
                    summary = null,
                    isLocked = true
                )
            }
        }.stateIn(
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
        val internalSectionCollapsed: Boolean = true,
        val mainSectionCollapsed: Boolean = false
    )

    init {
        viewModelScope.launch {
            repo.getAll().collect { list ->
                Log.e("DB_FLOW", "Room emitted ${list.size} rows")
                _items.value = list
            }
        }
    }
    var groupIndex = 0
    // FINAL UI STATE (all expensive computation happens here)
    val uiState: StateFlow<DashboardUiState> =
        combine(items, uiInputs) { list, input ->

            android.util.Log.d("expense", "input.mode ${input.mode}")

            // -------------------------------------------------
            // 1) Base list — ALL transactions from DB
            // -------------------------------------------------
            val baseAll = list

            // -------------------------------------------------
            // 2) Period filter (GLOBAL time context)
            // -------------------------------------------------
            val periodFiltered =
                baseAll.filterByPeriod(input.mode, input.period)

            // -------------------------------------------------
            // 3) Ignored visibility (ALWAYS visible per philosophy)
            //     (Hook kept for future toggle, but no filtering)
            // -------------------------------------------------
            val visibleList = periodFiltered

            // -------------------------------------------------
            // 4) Category lens (analysis only, NOT hiding money)
            //     This affects transaction list + expense math
            // -------------------------------------------------
            val finalList =
                if (input.selectedType == null)
                    visibleList
                else
                    visibleList.filter { it.category == input.selectedType }




            // -------------------------------------------------
            // 5) Expense list (math-only, derived from finalList)
            // -------------------------------------------------
            val monthlyExpenses =
                finalList.filter {
                    it.isExpense() &&
                            it.linkType != LINK_TYPE_INVESTMENT_OUTFLOW &&
                            it.expenseFrequency == ExpenseFrequency.MONTHLY.name
                }

            val monthlyInternalTransfers =
                finalList.filter {
                    it.isNetZero &&
                            it.expenseFrequency == ExpenseFrequency.MONTHLY.name
                }


            // -------------------------------------------------
            // 6) Recalculate category totals
            //     PIE  = period-wide (visibleList)
            //     LIST = lens-applied (expenseList)
            // -------------------------------------------------

            // -------------------------------------------------
            // 7) Totals (Debit / Credit)
            // -------------------------------------------------
            val totalDebit = monthlyExpenses.sumOf { it.amount }

            val totalCredit =
                finalList
                    .filter { it.type.equals("CREDIT", true) && !it.isNetZero }
                    .sumOf { it.amount }

            val debitCreditTotals =
                mapOf(
                    "DEBIT" to totalDebit,
                    "CREDIT" to totalCredit
                ).filter { it.value > 0 }

            // -------------------------------------------------
            // 8) Bar chart data
            // -------------------------------------------------
            val barData = when (input.mode) {
                DashboardMode.MONTH ->
                    monthlyExpenses
                        .groupBy { it.localDate().dayOfMonth }
                        .mapValues { it.value.sumOf { tx -> tx.amount } }

                DashboardMode.QUARTER,
                DashboardMode.YEAR ->
                    monthlyExpenses
                        .groupBy { it.localDate().monthValue }
                        .mapValues { it.value.sumOf { tx -> tx.amount } }
            }

            // -------------------------------------------------
            // 9) Sorting (semantic split happens AFTER this)
            // -------------------------------------------------
            val sortedExpenses =
                sortTransactions(monthlyExpenses, input.sortConfig)

            val sortedInternal =
                sortTransactions(monthlyInternalTransfers, input.sortConfig)

            // -------------------------------------------------
            // 10) MAIN rows (expenses + credits)
            // -------------------------------------------------
            groupIndex =0
            val mainRows =
                buildUiRows(
                    txs = sortedExpenses,
                    sortConfig = input.sortConfig,
                    groupByMerchant = input.showGroupedMerchants
                )
            val internalRows =
                buildUiRows(
                    txs = sortedInternal,
                    sortConfig = input.sortConfig,
                    groupByMerchant = input.showGroupedMerchants
                )

            // -------------------------------------------------
            // 11) INTERNAL TRANSFER section
            // -------------------------------------------------
            // -------------------------------------------------
// INTERNAL TRANSFER section (SAFE)
// -------------------------------------------------
            val internalSectionHeader: UiTxnRow.Section? =
                if (sortedInternal.isEmpty()) null
                else UiTxnRow.Section(
                    id = "internal_transfers",
                    title = "Internal transfers",
                    count = sortedInternal.size,
                    collapsed = input.internalSectionCollapsed
                )


            val internalSectionBody: List<UiTxnRow> =
                if (sortedInternal.isEmpty()) {
                    emptyList()
                } else {
                    if (input.showGroupedMerchants)
                        sortUiRows(internalRows, input.sortConfig)
                    else
                        internalRows
                }


            val visibleInternalRows: List<UiTxnRow> =
                when {
                    internalSectionHeader == null -> emptyList()
                    input.internalSectionCollapsed -> listOf(internalSectionHeader)
                    else -> listOf(internalSectionHeader) + internalSectionBody
                }



            // -------------------------------------------------
            // 12) FINAL rows (main + internal section)
            // -------------------------------------------------
            // -----------------------------
// MAIN section header
// -----------------------------
            val mainSectionHeader =
                UiTxnRow.Section(
                    id = "main_transactions",
                    title = "Transactions",
                    count = sortedExpenses.size,
                    collapsed = input.mainSectionCollapsed
                )


            val visibleMainRows =
                if (input.mainSectionCollapsed) emptyList()
                else mainRows

// -----------------------------
// INTERNAL section (already exists)
// -----------------------------


// -----------------------------
// FINAL rows
// -----------------------------
            val finalRows =
                listOf(mainSectionHeader) +
                        visibleMainRows +
                        visibleInternalRows


            // -------------------------------------------------
            // 13) UI State
            // -------------------------------------------------
            DashboardUiState(
                mode = input.mode,
                period = input.period,
                rows = finalRows,
                selectedType = input.selectedType,
                selectedDay = input.selectedDay,
                selectedMonth = input.selectedMonth,
                sortConfig = input.sortConfig,
                showIgnored = input.showIgnored,
                finalList = finalList,
                sortedList = sortedExpenses,
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

     fun buildUiRows(
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
                    groupId = "merchant:${first.merchant}:${groupIndex++}",  // ✅ UNIQUE + STABLE
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
            _insightsFrequencyFilter.value =
                if (newMode == DashboardMode.YEAR)
                    FrequencyFilter.ALL_EXPENSES
                else
                    FrequencyFilter.MONTHLY_ONLY

            it.copy(
                mode = newMode,
                selectedType = null,
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

    fun setExpenseFrequency(
        tx: SmsEntity,
        frequency: ExpenseFrequency
    ) {
        viewModelScope.launch {
            repo.setExpenseFrequency(tx, frequency)
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

    fun forceReclassify() {
        viewModelScope.launch(Dispatchers.Default) {
            repo.reclassifyAllWithProgress { done, total ->
                _reclassifyProgress.value = done to total
            }
        }
    }

    fun debugReprocessSms(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.reprocessSingleSms(id)
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

    private val _linkProgress =
        MutableStateFlow<ProgressState?>(null)

    val linkProgress = _linkProgress.asStateFlow()

    data class ProgressState(
        val processed: Int,
        val total: Int,
        val done: Boolean
    )


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


    fun sortTransactions(
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



    // --- Convenience helpers to clear or set selected filter (if needed) ---
    fun clearSelectedType() {
        update { it.copy(selectedType = null) } // use your existing update(...) method
        // optionally trigger recompute if you don't recompute from combine automatically
    }

    fun setSelectedTypeSafe(type: String?) {
        update { it.copy(selectedType = type) }
        // The combine block should react to uiInputs.selectedType and recompute finalList / totals
    }

    fun markAsSelfTransfer(tx: SmsEntity) {
        viewModelScope.launch {
            repo.markAsSelfTransfer(tx)
        }
    }

    fun undoSelfTransfer(tx: SmsEntity) {
        viewModelScope.launch {
            repo.undoSelfTransfer(tx)
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

 fun SmsEntity.matchesFrequency(
    filter: FrequencyFilter
): Boolean {
    val freq = expenseFrequency

    return when (filter) {
        FrequencyFilter.MONTHLY_ONLY ->
            freq == ExpenseFrequency.MONTHLY.name

        FrequencyFilter.ALL_EXPENSES ->
            true

        FrequencyFilter.YEARLY_ONLY ->
            freq == ExpenseFrequency.YEARLY.name

        FrequencyFilter.IRREGULAR_ONLY ->
            freq == ExpenseFrequency.IRREGULAR.name ||
                    freq == ExpenseFrequency.ONE_TIME.name
    }
}


