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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.spendwise.app.navigation.Screen
import com.spendwise.app.ui.dashboard.CategoryPieChart
import com.spendwise.app.ui.dashboard.FixMerchantDialog
import com.spendwise.core.extensions.nextQuarter
import com.spendwise.core.extensions.previousQuarter
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import com.spendwise.feature.smsimport.ui.UiTxnRow
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
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
    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }
    var expandedItemId by remember { mutableStateOf<Long?>(null) }

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


        showFixDialog?.let { tx ->
            FixMerchantDialog(
                tx = tx,
                onConfirm = { newName ->
                    viewModel.fixMerchant(tx, newName)
                    showFixDialog = null
                },
                onDismiss = { showFixDialog = null }
            )
        }
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
                    showInternal = uiState.showInternalTransfers,
                    showGroupedMerchants = uiState.showGroupedMerchants,
                    onToggleInternal = { viewModel.toggleInternalTransfers() },
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
                            },
                            onMarkNotExpense = { _, _ -> }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    is UiTxnRow.Grouped -> {
                        GroupedMerchantRow(row)
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
    var showFixDialog by remember { mutableStateOf<SmsEntity?>(null) }

    val totalSpend by viewModel.totalSpend.collectAsState()

    val uiState by viewModel.uiState.collectAsState()
    var expandedItemId by remember { mutableStateOf<Long?>(null) }


    // Full categories for pie chart
    val categoriesAll by viewModel.categoryTotalsAll.collectAsState()

    // Filtered categories (if selectedType is applied)
    val categoriesFiltered by viewModel.categoryTotals.collectAsState()

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

        // Sort ALL categories for list (NOT filtered ones!)
        val sortedCategoriesAll = remember(categoriesAll) {
            categoriesAll.sortedByDescending { it.total }
        }

        // Sort filtered categories for list view
        val sortedFilteredCategories = remember(categoriesFiltered) {
            categoriesFiltered.sortedByDescending { it.total }
        }

        showFixDialog?.let { tx ->
            FixMerchantDialog(
                tx = tx,
                onConfirm = { newName ->
                    viewModel.fixMerchant(tx, newName)
                    showFixDialog = null
                },
                onDismiss = { showFixDialog = null }
            )
        }
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {

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
                TIMEFRAME SWITCH
            ------------------------------------------------------------- */
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = { viewModel.setMode(DashboardMode.MONTH) }) { Text("Month") }
                    Button(onClick = { viewModel.setMode(DashboardMode.QUARTER) }) { Text("Quarter") }
                    Button(onClick = { viewModel.setMode(DashboardMode.YEAR) }) { Text("Year") }
                }
                Spacer(Modifier.height(16.dp))
            }

            /* -------------------------------------------------------------
                CATEGORY LIST
            ------------------------------------------------------------- */
            item {
                Text("Categories", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }


            // If filtered list is empty, fallback to showing all categories
            val listToShow = if (categoriesFiltered.isNotEmpty()) {
                sortedFilteredCategories
            } else {
                sortedCategoriesAll
            }

            items(listToShow) { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(cat.color, shape = CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                    }

                    Text("₹${cat.total.toInt()}", style = MaterialTheme.typography.bodyLarge)
                }

                Divider()
            }

            item {
                Spacer(Modifier.height(16.dp))

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
                    onRequestMerchantFix = { showFixDialog = it },
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
    showInternal: Boolean,
    showGroupedMerchants: Boolean,
    onToggleInternal: () -> Unit,
    onOpenInsights: () -> Unit,
    onToggleGroupByMerchant: () -> Unit,
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

            
            FilterChip(
                selected = showInternal,
                onClick = onToggleInternal,
                label = {
                    Text(if (showInternal) "Transfers shown" else "Transfers hidden")
                }
            )

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
fun GroupedMerchantRow(group: UiTxnRow.Grouped) {
    var expanded by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()   // 🔥 smooth height animation
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // Header
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${group.count} spends",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 🔴 ONLY amount is red
                Text(
                    text = "₹${group.totalAmount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Red
                )
            }


            Icon(
                imageVector =
                    if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }


        // Children (animated)
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
                        },
                        onMarkNotExpense = { _, _ ->
                            {

                            }

                        }   // optional: lighter UI

                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun TransactionRow(
    sms: SmsEntity,
    isExpanded: Boolean,
    onClick: (SmsEntity) -> Unit,
    onMarkNotExpense: (SmsEntity, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight() // 🚀 prevents vertical stretch
            .clickable { onClick(sms) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            // LEFT: Merchant + message
            Column(
                modifier = Modifier.weight(1f), // 🚀 prevents squeezing
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    sms.merchant ?: sms.sender,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    sms.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
                )

                if (sms.isNetZero) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Internal transfer",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

            }

            Spacer(Modifier.width(12.dp))

            // RIGHT: Amount + date
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val amountText = when {
                    sms.isNetZero -> "₹${sms.amount.toInt()}"
                    sms.type == "DEBIT" -> "-₹${sms.amount.toInt()}"
                    else -> "₹${sms.amount.toInt()}"
                }

                val amountColor = when {
                    sms.isNetZero -> Color.Gray
                    sms.type == "DEBIT" -> Color.Red
                    else -> Color(0xFF2E7D32) // green
                }

                Text(
                    amountText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = amountColor,
                    maxLines = 1
                )


                Spacer(Modifier.height(4.dp))

                val date = Instant
                    .ofEpochMilli(sms.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                Text(
                    date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )
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
