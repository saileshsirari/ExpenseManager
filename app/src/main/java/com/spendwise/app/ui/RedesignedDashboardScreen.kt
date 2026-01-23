// File: RedesignedDashboardAndInsights.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.spendwise.app.ui

import android.R
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.spendwise.app.navigation.Screen
import com.spendwise.app.ui.dashboard.CategoryPieChart
import com.spendwise.app.ui.dashboard.DashboardModeSelectorProAware
import com.spendwise.core.com.spendwise.core.ExpenseFrequency
import com.spendwise.core.com.spendwise.core.FrequencyFilter
import com.spendwise.core.ml.CategoryType
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode
import com.spendwise.domain.com.spendwise.feature.smsimport.ui.MonthlyComparisonCard
import com.spendwise.domain.com.spendwise.feature.smsimport.ui.WalletInsightCard
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.MonthlyBar
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import com.spendwise.feature.smsimport.ui.SmsImportViewModel.InsightsUiState
import com.spendwise.feature.smsimport.ui.UiTxnRow
import com.spendwise.feature.smsimport.ui.matchesFrequency
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/* -----------------------------------------------------------
   1) Modern Minimal DashboardScreen
   - Trend-first (bar chart) with compact totals header
   - Quick actions row (filter, insights shortcut)
   - Transactions list (immediately visible)
   - "Insights" nav to deeper analytics (full pie + list)
   ----------------------------------------------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RedesignedDashboardScreen(
    navController: NavController,
    viewModel: SmsImportViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.importProgress.collectAsState()


    var expandedItemId by remember { mutableStateOf<Long?>(null) }
    val onMarkNotExpense: (SmsEntity, Boolean) -> Unit = { id, ignored ->
        viewModel.setIgnoredState(id, ignored)
    }


    val progressReclassify by viewModel.reclassifyProgress.collectAsState()


    // top-level loading / import handling (assumes app-level permission + import triggers)
    if (!progress.done) {
        // small inset progress UI — full screen handled elsewhere
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Importing messages — ${progress.processed}/${progress.total}")
            }
        }
        return
    }


    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Preparing dashboard…")
            }
        }
        return
    }
    val fromInsights =
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow("from_insights", false)
            ?.collectAsState()

    LaunchedEffect(fromInsights?.value) {
        if (fromInsights?.value == true) {
            viewModel.restoreDashboardFromInsights()

            // 🔒 Consume the event
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set("from_insights", false)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddExpense.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (uiState.mode) {
                            DashboardMode.MONTH -> YearMonth.now()
                                .format(DateTimeFormatter.ofPattern("MMMM yyyy"))

                            DashboardMode.QUARTER -> "Quarter"
                            DashboardMode.YEAR -> "${YearMonth.now().year}"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Insights.route) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dialog_info),
                            contentDescription = "Insights"
                        )
                    }
                }
            )
        }
    ) { padding ->
        ApplySelfTransferUiHost(viewModel)
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp)
        ) {
            // 🔒 RECLASSIFY PROGRESS (VISIBLE & SAFE)
            progressReclassify?.let { (done, total) ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Improving transaction classification",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress =
                                    if (total > 0) done.toFloat() / total else 0f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                if (total > 0) "$done of $total transactions"
                                else "Starting…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }


            item {
                Button(
                    onClick = { viewModel.triggerReclassification() },
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text("Reclassify Transactions (Debug)")
                }
            }
            item {
                PeriodNavigator(
                    mode = DashboardMode.MONTH,   // 🔒 FORCE month view
                    period = uiState.period,

                    onPrev = {
                        viewModel.setPeriod(uiState.period.minusMonths(1))
                    },

                    onNext = {
                        viewModel.setPeriod(uiState.period.plusMonths(1))
                    }
                )

                Spacer(Modifier.height(12.dp))
            }

            item {
                // Summary header: totals + mini donut (optional) + quick actions
                SummaryHeader(
                    totalDebit = uiState.totalsDebit,
                    totalCredit = uiState.totalsCredit,
                    onOpenInsights = {
                        viewModel.prepareInsightsFromDashboard()
                        navController.navigate(Screen.Insights.route)
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                // Quick filters & actions row
                QuickActionsRow(
                    showGroupedMerchants = uiState.showGroupedMerchants,
                    onOpenInsights = {
                        viewModel.prepareInsightsFromDashboard()
                        navController.navigate(Screen.Insights.route)
                    },
                    onToggleGroupByMerchant = {
                        viewModel.toggleGroupByMerchant()
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                Spacer(Modifier.height(12.dp))
                SortHeader(
                    sortConfig = uiState.sortConfig,
                    onSortChange = { viewModel.updateSort(it) }
                )
                Spacer(Modifier.height(12.dp))

            }

            item {
                Text(
                    "Recent transactions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            // Transaction list: lightweight items (no heavy recompute here)

            items(
                items = uiState.rows,
                key = { row ->
                    when (row) {
                        is UiTxnRow.Normal -> "tx-${row.tx.id}"
                        is UiTxnRow.Grouped -> "group-${row.groupId}"
                        is UiTxnRow.Section -> "section-${row.id}"
                    }
                }
            ) { row ->
                when (row) {

                    is UiTxnRow.Section -> {
                        InternalTransferSectionHeader(
                            title = row.title,
                            count = row.count,
                            collapsed = row.collapsed,
                            onToggle = {
                                when (row.id) {
                                    "main_transactions" ->
                                        viewModel.toggleMainSection()

                                    "internal_transfers" ->
                                        viewModel.toggleInternalSection()
                                }
                            }

                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    is UiTxnRow.Normal -> {

                        TransactionRow(
                            sms = row.tx,
                            isExpanded = expandedItemId == row.tx.id,
                            onClick = {
                                expandedItemId =
                                    if (expandedItemId == row.tx.id) null else row.tx.id
                                viewModel.onMessageClicked(it)
                            },
                            onChange = { freq ->
                                viewModel.setExpenseFrequency(row.tx, freq)
                            },

                            onMarkAsSelfTransfer = { tx ->
                                viewModel.markAsSelfTransfer(tx)
                                viewModel.requestApplySelfRule(tx)
                            },
                            onDebugClick = {
                                viewModel.onMessageClicked(row.tx)
                                viewModel.debugReprocessSms(row.tx.id)
                            },
                            onChangeCategory = { tx, category ->
                                viewModel.changeMerchantCategory(
                                    merchant = tx.merchant ?: return@TransactionRow,
                                    category = category
                                )
                            },
                            onUndoSelfTransfer = {
                                viewModel.undoSelfTransfer(it)
                            }
                        )

                        Spacer(Modifier.height(8.dp))
                    }

                    is UiTxnRow.Grouped -> {
                        GroupedMerchantRow(
                            group = row,
                            viewModel = viewModel,
                            onMarkNotExpense = onMarkNotExpense
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }


            // small footer / explore insights
            item {
                Spacer(Modifier.height(12.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Want deeper insights?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = {
                            viewModel.prepareInsightsFromDashboard()
                            navController.navigate(Screen.Insights.route)
                        }) {
                            Text("Open Insights")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
@Composable
fun ApplySelfTransferUiHost(
    viewModel: SmsImportViewModel
) {
    var selectedTx by remember { mutableStateOf<SmsEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val applyRuleProgress by viewModel.applyRuleProgress.collectAsState()
    val previewCount by viewModel.selfRulePreviewCount.collectAsState()

    // 🔒 EVENT collector (SharedFlow)
    LaunchedEffect(Unit) {
        viewModel.applySelfRuleRequest.collect { tx ->
            selectedTx = tx
            showDialog = true
            viewModel.computeSelfTransferPreview(tx)
        }
    }

    // 🔒 GLOBAL OVERLAY LAYER
    if (showDialog || applyRuleProgress != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .zIndex(999f),   // 🔥 critical
            contentAlignment = Alignment.Center
        ) {

            // -----------------------------------
            // APPLY IN PROGRESS
            // -----------------------------------
            if (applyRuleProgress != null) {
                val (done, total) = applyRuleProgress!!

                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Applying self-transfer rule",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress =
                                if (total > 0) done.toFloat() / total else 0f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "$done of $total messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            // -----------------------------------
            // APPLY RULE DIALOG
            // -----------------------------------
            else if (showDialog && selectedTx != null) {
                ApplySelfTransferRuleDialog(
                    previewCount = previewCount,
                    onConfirmApplyAll = {
                        viewModel.applySelfTransferPattern(selectedTx!!)
                        viewModel.clearSelfTransferPreview()
                        showDialog = false
                    },
                    onOnlyThis = {
                        viewModel.clearSelfTransferPreview()
                        showDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryHeader(selectedType: String?) {
    if (selectedType == null) return

    Text(
        text = "Transactions for ${selectedType}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun ApplySelfTransferRuleDialog(
    previewCount: Int?,
    onConfirmApplyAll: () -> Unit,
    onOnlyThis: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* non-dismissible */ },
        title = { Text("Apply to similar transactions?") },
        text = {
            Column {
                if (previewCount != null) {
                    Text("This will affect $previewCount messages.")
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Only messages with the same format will be marked as self-transfer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmApplyAll) {
                Text("Apply to all")
            }
        },
        dismissButton = {
            TextButton(onClick = onOnlyThis) {
                Text("Only this one")
            }
        }
    )
}



@Composable
fun InternalTransferSectionHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Icon(
            imageVector =
                if (collapsed) Icons.Default.ExpandMore
                else Icons.Default.ExpandLess,
            contentDescription = null
        )
    }

    Divider()
}

@Composable
fun InsightsFrequencySelector(
    uiState: InsightsUiState,
    onChange: (FrequencyFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyChip(
                filter = FrequencyFilter.MONTHLY_ONLY,
                uiState = uiState,
                onChange = onChange
            )
            FrequencyChip(
                filter = FrequencyFilter.YEARLY_ONLY,
                uiState = uiState,
                onChange = onChange
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyChip(
                filter = FrequencyFilter.ALL_EXPENSES,
                uiState = uiState,
                onChange = onChange
            )
        }
    }
}



fun FrequencyFilter.isEnabled(ui: InsightsUiState): Boolean =
    when (this) {
        FrequencyFilter.MONTHLY_ONLY -> ui.hasMonthlyData
        FrequencyFilter.YEARLY_ONLY -> ui.hasYearlyData
        FrequencyFilter.ALL_EXPENSES -> ui.hasAnyData
    }

@Composable
fun MerchantCategorySelector(
    currentCategory: String,
    onChange: (CategoryType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Category (applies to this merchant)",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(6.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = currentCategory
                    .lowercase()
                    .replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                CategoryType.values().forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                category.name
                                    .lowercase()
                                    .replaceFirstChar { it.uppercase() }
                            )
                        },
                        onClick = {
                            expanded = false
                            onChange(category)
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun FrequencyChip(
    filter: FrequencyFilter,
    uiState: InsightsUiState,
    onChange: (FrequencyFilter) -> Unit
) {
    val enabledByData = filter.isEnabled(uiState)

    FilterChip(
        selected = uiState.frequency == filter,
        enabled = enabledByData,
        onClick = { onChange(filter) },
        label = {
            Text(
                when (filter) {
                    FrequencyFilter.MONTHLY_ONLY -> "Monthly"
                    FrequencyFilter.ALL_EXPENSES -> "All"
                    FrequencyFilter.YEARLY_ONLY -> "Yearly"
                }
            )
        }
    )
}



@Composable
fun ProExplainDialog(
    title: String,
    message: String,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text("Upgrade to Pro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}

@Composable
fun MonthlyTrendBarChart(
    bars: List<MonthlyBar>,
    isPro: Boolean,
    onMonthSelected: (YearMonth) -> Unit,
    onUpgrade: () -> Unit
) {
    if (!isPro) {
        LockedTrendPreview(onUpgrade)
        return
    }

    val max = bars.maxOfOrNull { it.total } ?: 1.0
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    LaunchedEffect(bars.size) {
        if (bars.isNotEmpty()) {
            listState.scrollToItem(bars.lastIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bars) { bar ->
            MonthlyBarItem(
                bar = bar,
                max = max,
                onClick = { onMonthSelected(bar.month) }
            )
        }
    }
}
@Composable
private fun MonthlyBarItem(
    bar: MonthlyBar,
    max: Double,
    onClick: () -> Unit
) {
    fun formatRupeesCompact(amount: Double): String {
        return when {
            amount >= 1_00_000 ->
                "₹${(amount / 1_00_000).roundToInt()}L"

            amount >= 1_000 ->
                "₹${(amount / 1_000).roundToInt()}K"

            else ->
                "₹${amount.roundToInt()}"
        }
    }
    val heightRatio = (bar.total / max).coerceIn(0.05, 1.0)
    val barHeight = 160.dp * heightRatio.toFloat()

    Column(
        modifier = Modifier
            .width(42.dp)
            .fillMaxHeight()
            .clickable { onClick() },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // 🔹 VALUE LABEL (NEW)
        Text(
            text = formatRupeesCompact(bar.total),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp
            ),
            color = Color(0xFF9E9E9E), // slightly lighter gray
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 2.dp)
        )


        // 🔹 BAR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF5B6CFF))
        )

        Spacer(Modifier.height(8.dp))

        // 🔹 MONTH
        Text(
            text = bar.month.month.name
                .take(3)
                .lowercase()
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )

        Text(
            text = "'${bar.month.year % 100}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
fun LockedTrendPreview(onUpgrade: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onUpgrade() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Monthly trends are Pro",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "See spending patterns across months.\nYour data stays the same.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

/* -----------------------------------------------------------
   2) Insights screen: full-size pie chart + category breakdown
   - Timeframe toggle (Month / Quarter / Year)
   - Category list sorted by amount
   ----------------------------------------------------------- */
@Composable
fun InsightsScreen(
    navController: NavController,
    viewModel: SmsImportViewModel,
    isPro: Boolean
) {
    val freqFilter by viewModel.insightsFrequencyFilter.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    BackHandler {
        exitInsights(navController,viewModel,viewModel.uiState.value.mode,viewModel.uiState.value.period)
    }

    // Reset filter whenever entering Insights
    LaunchedEffect(Unit) {
        viewModel.clearSelectedType()
        viewModel.setInsightsFrequencyFilter(FrequencyFilter.MONTHLY_ONLY)
    }


    val totalSpend by viewModel.totalSpend.collectAsState()

    val insightRows = remember(uiState.rows, freqFilter) {
        uiState.rows.filter { row ->
            when (row) {
                is UiTxnRow.Section -> true
                is UiTxnRow.Normal -> row.tx.matchesFrequency(freqFilter)
                is UiTxnRow.Grouped ->
                    row.children.any { it.matchesFrequency(freqFilter) }
            }
        }
    }


    var expandedItemId by remember { mutableStateOf<Long?>(null) }
    // Full categories for pie chart
    val categoriesAll by viewModel.categoryTotalsForPeriod.collectAsState()

    // Filtered categories (if selectedType is applied)
    val categoryInsight by viewModel.categoryInsight.collectAsState()
    val comparison by viewModel.periodComparison.collectAsState()
    val walletInsight by viewModel.walletInsight.collectAsState()
    val insightsUi by viewModel.insightsUiState.collectAsState()

    var lockedMode by remember { mutableStateOf<DashboardMode?>(null) }
    val monthlyBars by viewModel.monthlyTrendBars.collectAsState()



    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = {
                        exitInsights(navController,viewModel,viewModel.uiState.value.mode,viewModel.uiState.value.period)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        ApplySelfTransferUiHost(viewModel)
        if (lockedMode != null) {
            ProExplainDialog(
                title = "${lockedMode!!.name.lowercase().replaceFirstChar { it.uppercase() }} view is Pro",
                message = viewModel.explainDashboardModeLock(lockedMode!!),
                onUpgrade = {
                    lockedMode = null
                    viewModel.onUpgradeClicked()
                },
                onDismiss = { lockedMode = null }
            )
        }
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {

            item {
                DashboardModeSelectorProAware(
                    current = uiState.mode,
                    isPro = viewModel.isPro.collectAsState().value,
                    onChange = viewModel::setMode,
                    onLocked = { lockedMode = it }
                )
            }
            item {
                PeriodNavigator(
                    mode = uiState.mode,
                    period = uiState.period,
                    onPrev = viewModel::prevPeriod,
                    onNext = viewModel::nextPeriod
                )
            }

            // ----------------------------
            // 🔹 Monthly Trends
            // ----------------------------
            item {
                MonthlyTrendBarChart(
                    bars = monthlyBars,
                    isPro = isPro,
                    onMonthSelected = { month ->
                        viewModel.setMode(DashboardMode.MONTH) // safe no-op if already month
                        viewModel.setPeriod(month)
                    },
                    onUpgrade = {
                        viewModel.onUpgradeClicked()
                    }
                )

                Spacer(Modifier.height(24.dp))

            }

            /* -------------------------------------------------------------
                CATEGORY PIE CHART
            ------------------------------------------------------------- */
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Category Breakdown", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        if (categoriesAll.isNotEmpty()) {
                            Text(
                                text = "₹${totalSpend.roundToInt()}",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(Modifier.height(10.dp))


                            InsightsFrequencySelector(
                                uiState = insightsUi,
                                onChange = viewModel::setInsightsFrequencyFilter,
                            )


                            if (freqFilter != FrequencyFilter.MONTHLY_ONLY) {
                                Text(
                                    text = when (freqFilter) {
                                        FrequencyFilter.ALL_EXPENSES ->
                                            "Includes yearly and irregular expenses"

                                        FrequencyFilter.YEARLY_ONLY ->
                                            "Showing yearly expenses only"

                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            CategoryPieChart(
                                data = categoriesAll.map { it.total },
                                labels = categoriesAll.map { it.name },
                                colors = categoriesAll.map { it.color },
                                selectedLabel = uiState.selectedType,
                                onSliceClick = { clicked ->
                                    viewModel.setSelectedTypeSafe(clicked)
                                }

                            )
                            Spacer(Modifier.height(12.dp))

                            CategoryHeader(uiState.selectedType)


                        } else {
                            Text(
                                "No category data found",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            /* -------------------------------------------------------------
                CATEGORY LIST
            ------------------------------------------------------------- */
            item {
                CategoryListCard(
                    title = "Spending by category",
                    items = categoryInsight.items,
                    locked = categoryInsight.isLocked,
                    onUpgrade = { viewModel.onUpgradeClicked() }
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                MonthlyComparisonCard(
                    comparison = comparison,
                    onUpgrade = { viewModel.onUpgradeClicked() }
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                WalletInsightCard(
                    insight = walletInsight,
                    onUpgrade = { viewModel.onUpgradeClicked() }
                )
                Spacer(Modifier.height(16.dp))
            }


            item {
                // Sorting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SortHeader(
                        sortConfig = uiState.sortConfig,
                        onSortChange = { viewModel.updateSort(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
            item {
                if (freqFilter != FrequencyFilter.MONTHLY_ONLY) {
                    Text(
                        "Filtered by ${freqFilter.name.lowercase()} expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            items(
                items = insightRows,
                key = { row ->
                    when (row) {
                        is UiTxnRow.Normal -> "tx-${row.tx.id}"
                        is UiTxnRow.Grouped -> "group-${row.groupId}"
                        is UiTxnRow.Section -> "section-${row.id}"

                    }
                }
            ) { row ->
                when (row) {
                    is UiTxnRow.Normal -> {
                        TransactionRow(
                            sms = row.tx,
                            isExpanded = expandedItemId == row.tx.id,
                            onClick = {
                                expandedItemId =
                                    if (expandedItemId == row.tx.id) null else row.tx.id

                                viewModel.onMessageClicked(it)
                            },
                            onChange = { freq ->
                                viewModel.setExpenseFrequency(row.tx, freq)
                            },
                            onMarkAsSelfTransfer = { tx ->
                                viewModel.markAsSelfTransfer(tx)
                                viewModel.requestApplySelfRule(tx)
                            },
                            onUndoSelfTransfer = {
                                viewModel.undoSelfTransfer(it)
                            },
                            onChangeCategory = { tx, category ->
                                viewModel.changeMerchantCategory(
                                    merchant = tx.merchant ?: return@TransactionRow,
                                    category = category
                                )
                            },
                            onDebugClick = {
                                viewModel.onMessageClicked(row.tx)
                                viewModel.debugReprocessSms(row.tx.id)
                            }
                        )
                    }

                    is UiTxnRow.Grouped -> {
                        GroupedMerchantRow(
                            group = row,
                            viewModel = viewModel,
                            onMarkNotExpense = { _, _ -> }
                        )
                    }

                    // 🔒 Explicitly ignore sections in Insights
                    is UiTxnRow.Section -> {
                        InternalTransferSectionHeader(
                            title = row.title,
                            count = row.count,
                            collapsed = row.collapsed,
                            onToggle = {
                                when (row.id) {
                                    "main_transactions" ->
                                        viewModel.toggleMainSection()
                                    "internal_transfers" ->
                                        viewModel.toggleInternalSection()
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                }
            }

        }
    }
}


/* -----------------------------------------------------------
   3) Small reusable components
   - SummaryHeader, QuickActionsRow, SmallDonutCard, TransactionRow
   ----------------------------------------------------------- */

@Composable
fun SummaryHeader(totalDebit: Double, totalCredit: Double, onOpenInsights: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Total spent", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("₹${totalDebit.toInt()}", style = MaterialTheme.typography.headlineSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Total credit", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("₹${totalCredit.toInt()}", style = MaterialTheme.typography.titleMedium)
        }
    }
}
private fun exitInsights(navController: NavController, viewModel: SmsImportViewModel,
                         mode: DashboardMode,
                         period: YearMonth) {
    // 🔒 Save current insights state
    viewModel.rememberInsightsContext(
        mode = mode,
        period = period
    )
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set("from_insights", true)

    navController.popBackStack()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupGroupingToggle(
    grouped: Boolean,
    onToggle: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = grouped,
            onClick = onToggle,
            label = { Text("Group similar merchants") }
        )

        Spacer(Modifier.width(6.dp))


    }
}

@Composable
fun QuickActionsRow(
    showGroupedMerchants: Boolean,
    onOpenInsights: () -> Unit,
    onToggleGroupByMerchant: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Row 1: Primary actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onOpenInsights) {
                Text("Insights")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Row 2: Filter chips (secondary / advanced)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Column {
                FilterChip(
                    selected = showGroupedMerchants,
                    onClick = { onToggleGroupByMerchant() },
                    label = { Text("Group") }
                )
            }

        }
    }
}

@Composable
fun GroupedMerchantRow(
    group: UiTxnRow.Grouped,
    viewModel: SmsImportViewModel,
    onMarkNotExpense: (SmsEntity, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<Long?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        val isCredit = group.netAmount > 0
        val displayAmount = kotlin.math.abs(group.netAmount)

        // 🔹 Group header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (isCredit) "+₹$displayAmount" else "-₹$displayAmount",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCredit) Color(0xFF2E7D32) else Color.Red
                )
            }

            Icon(
                imageVector =
                    if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        // 🔹 Children
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                group.children.forEach { tx ->
                    TransactionRow(
                        sms = tx,
                        isExpanded = expandedItemId == tx.id,
                        onClick = {
                            expandedItemId =
                                if (expandedItemId == tx.id) null else tx.id
                            viewModel.onMessageClicked(it)
                        },
                        onChange = { freq ->
                            viewModel.setExpenseFrequency(tx, freq)
                        },
                        onMarkAsSelfTransfer = { tx ->
                            viewModel.markAsSelfTransfer(tx)
                            viewModel.requestApplySelfRule(tx)
                        },
                        onUndoSelfTransfer = { viewModel.undoSelfTransfer(it) },
                        onChangeCategory = { tx, category ->
                            viewModel.changeMerchantCategory(
                                merchant = tx.merchant ?: return@TransactionRow,
                                category = category
                            )
                        },
                        onDebugClick = {
                            viewModel.onMessageClicked(tx)
                            viewModel.debugReprocessSms(tx.id)
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun TransactionRowContent(
    sms: SmsEntity,
    isExpanded: Boolean
) {
    val isCredit =
        sms.type.equals("CREDIT", true) ||
                sms.type.equals("REFUND", true)

    val formattedDate = remember(sms.timestamp) {
        java.text.SimpleDateFormat(
            "dd MMM yyyy",
            java.util.Locale.getDefault()
        ).format(java.util.Date(sms.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {

        // Main row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = sms.merchant ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                if (sms.isNetZero &&
                    (sms.linkType == "INTERNAL_TRANSFER" || sms.linkType == "USER_SELF")
                ) {
                    // show "Internal Transfer" badge
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "Internal Transfer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        if (isCredit) "+₹${sms.amount.toInt()}"
                        else "-₹${sms.amount.toInt()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCredit) Color(0xFF2E7D32) else Color.Red
                )
            }
        }

        // Expanded SMS body ONLY
        if (isExpanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun ExpenseFrequencySelector(
    current: String,
    amount: Double,
    onChange: (ExpenseFrequency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val currentEnum =
        runCatching { ExpenseFrequency.valueOf(current) }
            .getOrNull() ?: ExpenseFrequency.MONTHLY

    // 🔒 Small amount rule
    val isSmallAmount = amount < 1000.0

    Column {
        Text(
            text = "Expense frequency",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(6.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = currentEnum.displayName(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Frequency") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ExpenseFrequency.values().forEach { freq ->

                    val disabled =
                        isSmallAmount &&
                                (freq == ExpenseFrequency.YEARLY )

                    DropdownMenuItem(
                        enabled = !disabled,
                        text = {
                            Text(
                                text = freq.displayName(),
                                color = if (disabled)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            if (!disabled) {
                                expanded = false
                                onChange(freq)
                            }
                        }
                    )
                }
            }
        }

        // Optional helper text
        if (isSmallAmount) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Yearly / Irregular disabled for small amounts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun ExpenseFrequency.displayName(): String =
    when (this) {
        ExpenseFrequency.MONTHLY -> "Monthly"
        ExpenseFrequency.YEARLY -> "Yearly"
    }

private const val MIN_AMOUNT_FOR_FREQUENCY_SELECTOR = 1000.0


@Composable
fun TransactionRow(
    sms: SmsEntity,
    isExpanded: Boolean,
    onClick: (SmsEntity) -> Unit,
    onDebugClick: () -> Unit,
    onChange: (ExpenseFrequency) -> Unit,
    onChangeCategory: (SmsEntity, CategoryType) -> Unit,
    onMarkAsSelfTransfer: (SmsEntity) -> Unit,
    onUndoSelfTransfer: (SmsEntity) -> Unit
) {

    val rowBackground = MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize() // 🔒 animate height smoothly
            .combinedClickable(
                onClick = { onClick(sms) },
            ),
        colors = CardDefaults.cardColors(containerColor = rowBackground)
    ) {

        Column {

            // Main content
            TransactionRowContent(
                sms = sms,
                isExpanded = isExpanded
            )


            // Expanded controls INSIDE same card
            if (isExpanded) {
                Divider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Self transfer")
                        Switch(
                            checked = sms.isNetZero &&
                                    (sms.linkType == "INTERNAL_TRANSFER" || sms.linkType == "USER_SELF"),
                                    onCheckedChange = { checked ->
                                if (checked) {
                                    onMarkAsSelfTransfer(sms)
                                }
                                else onUndoSelfTransfer(sms)
                            }
                        )
                    }



                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        if (sms.expenseFrequency != ExpenseFrequency.MONTHLY.name) {
                            Text(
                                text = sms.expenseFrequency.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                    TextButton(
                        onClick = onDebugClick
                    ) {
                        Text("Re-run classification (debug)")
                    }

                    if (sms.amount >= MIN_AMOUNT_FOR_FREQUENCY_SELECTOR) {
                        ExpenseFrequencySelector(
                            current = sms.expenseFrequency,
                            amount = sms.amount,
                            onChange = onChange
                        )
                    }



                    if (!sms.merchant.isNullOrBlank() && !sms.category.isNullOrBlank()) {
                        Divider()

                        MerchantCategorySelector(
                            currentCategory = sms.category!!,
                            onChange = { newCategory ->
                                onChangeCategory(sms, newCategory)
                            }
                        )
                    }


                }
            }
        }
    }
}


/* -----------------------------------------------------------
   4) Navigation note:
   - Add route "insights" in your Screen object (or use Screen.Insights)
   - Hook up navigation in SpendWiseApp nav graph:
       composable("insights") { InsightsScreen(navController, viewModel) }
   ----------------------------------------------------------- */

/* -----------------------------------------------------------
   5) Integration notes:
   - Replace placeholder "CategoryPieChart" and "DailyBarChart" with your existing components
   - In InsightsScreen I used viewModel.getCategoryTotalsSorted() — implement this helper in your VM:
       fun getCategoryTotalsSorted(): List<Pair<String, Double>> = categoryTotalsMap.entries.sortedByDescending { it.value }.map { it.key to it.value }
   - All actions call simple VM methods (setSelectedType, toggleInternalTransfers, setSelectedDay, setMode)
   ----------------------------------------------------------- */
