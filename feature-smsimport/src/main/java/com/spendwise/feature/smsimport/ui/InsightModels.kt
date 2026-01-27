package com.spendwise.domain.com.spendwise.feature.smsimport.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendwise.domain.com.spendwise.feature.smsimport.data.CategoryTotal

sealed class InsightBlock {
    data class CategoryList(
        val items: List<CategoryTotal>,
        val isLocked: Boolean
    ) : InsightBlock()

    data class Comparison(
        val title: String,
        val value: String?,
        val delta: String?,
        val isLocked: Boolean
    ) : InsightBlock()

    data class WalletInsight(
        val amount: Double?,
        val currencyCode: String?,
        val isLocked: Boolean
    )

}

@Composable
fun MonthlyComparisonCard(
    comparison: InsightBlock.Comparison,
    onUpgrade: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(comparison.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            when {
                !comparison.isLocked -> {
                    Text(
                        text = comparison.value ?: "",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    comparison.delta?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                }
                else -> {
                    Text("See how your spending changed from last month")
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onUpgrade) {
                        Text("Upgrade to compare")
                    }
                }
            }
        }
    }
}
@Composable
fun WalletInsightCard(
    walletInsight: InsightBlock.WalletInsight,
    onUpgrade: () -> Unit
) {
    if (walletInsight.amount != null && walletInsight.currencyCode != null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Wallet usage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            when {
                !walletInsight.isLocked -> {
                    Text(walletInsight.amount?.toString() ?: "No wallet activity this period")
                }
                else -> {
                    Text("Understand how wallets route your money")
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onUpgrade) {
                        Text("Upgrade to unlock")
                    }
                }
            }
        }
    }
    }
}
