// File: RedesignedDashboardAndInsights.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.spendwise.app.ui

import android.R
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.spendwise.app.navigation.Screen
import com.spendwise.app.ui.dashboard.CategoryPieChart
import com.spendwise.app.ui.dashboard.DashboardModeSelector
import com.spendwise.core.extensions.nextQuarter
import com.spendwise.core.extensions.previousQuarter
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode
import com.spendwise.domain.com.spendwise.feature.smsimport.ui.MonthlyComparisonCard
import com.spendwise.domain.com.spendwise.feature.smsimport.ui.WalletInsightCard
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import com.spendwise.feature.smsimport.ui.UiTxnRow
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
    val categories by viewModel.categoryTotals.collectAsState()
    var expandedItemId by remember { mutableStateOf<Long?>(null) }
    val onMarkNotExpense: (SmsEntity, Boolean) -> Unit = { id, ignored ->
        viewModel.setIgnoredState(id, ignored)
    }
    LaunchedEffect(Unit) {
        viewModel.resetToCurrentMonth()
    }
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

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp)
        ) {
            item {
                PeriodNavigator(
                    mode = uiState.mode,
                    period = uiState.period,
                    onPrev = {
                        val newPeriod = when (uiState.mode) {
                            DashboardMode.MONTH -> uiState.period.minusMonths(1)
                            DashboardMode.QUARTER -> uiState.period.previousQuarter()
                            DashboardMode.YEAR -> uiState.period.minusYears(1)
                        }
                        viewModel.setPeriod(newPeriod)
                    },
                    onNext = {
                        val newPeriod = when (uiState.mode) {
                            DashboardMode.MONTH -> uiState.period.plusMonths(1)
                            DashboardMode.QUARTER -> uiState.period.nextQuarter()
                            DashboardMode.YEAR -> uiState.period.plusYears(1)
                        }
                        viewModel.setPeriod(newPeriod)
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                // Summary header: totals + mini donut (optional) + quick actions
                SummaryHeader(
                    totalDebit = uiState.totalsDebit,
                    totalCredit = uiState.totalsCredit,
                    onOpenInsights = { navController.navigate(Screen.Insights.route) }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                // Quick filters & actions row
                QuickActionsRow(
                    showGroupedMerchants = uiState.showGroupedMerchants,
                    onOpenInsights = { navController.navigate(Screen.Insights.route) },
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
                                viewModel.toggleInternalSection()
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
                            onMarkNotExpense = { sms, ignored ->
                                onMarkNotExpense(sms, ignored)
                            },
                            onMarkAsSelfTransfer = {
                                viewModel.markAsSelfTransfer(it)
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
                        Button(onClick = { navController.navigate(Screen.Insights.route) }) {
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
fun CategoryHeader(selectedType: String?) {
    if (selectedType == null) return

    Text(
        text = "Transactions for ${selectedType}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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

/* -----------------------------------------------------------
   2) Insights screen: full-size pie chart + category breakdown
   - Timeframe toggle (Month / Quarter / Year)
   - Category list sorted by amount
   ----------------------------------------------------------- */
@Composable
fun InsightsScreen(
    navController: NavController,
    viewModel: SmsImportViewModel
) {
    // Reset filter whenever entering Insights
    LaunchedEffect(Unit) {
        viewModel.clearSelectedType()
    }

    val totalSpend by viewModel.totalSpend.collectAsState()

    val uiState by viewModel.uiState.collectAsState()
    var expandedItemId by remember { mutableStateOf<Long?>(null) }

    val topCategories by viewModel.topCategoriesFree.collectAsState()

    // Full categories for pie chart
    val categoriesAll by viewModel.categoryTotalsForPeriod.collectAsState()

    // Filtered categories (if selectedType is applied)
    val categoriesFiltered by viewModel.categoryTotals.collectAsState()
    val categoryInsight by viewModel.categoryInsight.collectAsState()
    val comparison by viewModel.periodComparison.collectAsState()
    val walletInsight by viewModel.walletInsight.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->


        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {

            item {
                DashboardModeSelector(
                    mode = uiState.mode,
                    onModeChange = viewModel::setMode
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


            items(
                items = uiState.sortedList,
                key = { it.id }
            ) { tx ->
                SmsListItem(
                    sms = tx,
                    isExpanded = expandedItemId == tx.id,
                    onClick = {
                        expandedItemId =
                            if (expandedItemId == tx.id) null else tx.id
                        viewModel.onMessageClicked(it)
                    },
                    onRequestMerchantFix = {  },
                    onMarkNotExpense = { item, checked ->
                        viewModel.setIgnoredState(item, checked)
                    }
                )
                Spacer(Modifier.height(8.dp))
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
                        onMarkNotExpense = onMarkNotExpense,
                        onMarkAsSelfTransfer = { viewModel.markAsSelfTransfer(it) },
                        onUndoSelfTransfer = { viewModel.undoSelfTransfer(it) }
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
            Column(modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)) {
                Text(
                    text = sms.merchant ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                if ( sms.linkType == "INTERNAL_TRANSFER") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows ,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32)
                        )
                        Text(
                            text =  "Internal Transfer" ,
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
fun TransactionRow(
    sms: SmsEntity,
    isExpanded: Boolean,
    onClick: (SmsEntity) -> Unit,
    onMarkNotExpense: (SmsEntity, Boolean) -> Unit,
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
                            checked = sms.isNetZero && sms.linkType == "INTERNAL_TRANSFER",
                            onCheckedChange = { checked ->
                                if (checked) onMarkAsSelfTransfer(sms)
                                else onUndoSelfTransfer(sms)
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
