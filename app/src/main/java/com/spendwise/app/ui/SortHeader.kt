package com.spendwise.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortConfig
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortField
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SortOrder

// ------------------------------------------------------
// SORT HEADER
// ------------------------------------------------------
@Composable
fun SortHeader(
    sortConfig: SortConfig,
    onSortChange: (SortConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        val dateArrow =
            if (sortConfig.primary == SortField.DATE)
                if (sortConfig.primaryOrder == SortOrder.ASC) "▲" else "▼"
            else ""

        Text(
            text = "Sort: Date $dateArrow",
            modifier = Modifier.clickable {
                onSortChange(
                    if (sortConfig.primary == SortField.DATE) {
                        sortConfig.copy(
                            primaryOrder = if (sortConfig.primaryOrder == SortOrder.ASC)
                                SortOrder.DESC else SortOrder.ASC
                        )
                    } else {
                        sortConfig.copy(
                            primary = SortField.DATE,
                            primaryOrder = SortOrder.DESC,
                            secondary = SortField.AMOUNT
                        )
                    }
                )
            },
            style = MaterialTheme.typography.labelLarge
        )

        val amountArrow =
            if (sortConfig.primary == SortField.AMOUNT)
                if (sortConfig.primaryOrder == SortOrder.ASC) "▲" else "▼"
            else ""

        Text(
            text = "Sort: Amount $amountArrow",
            modifier = Modifier.clickable {
                onSortChange(
                    if (sortConfig.primary == SortField.AMOUNT) {
                        sortConfig.copy(
                            primaryOrder = if (sortConfig.primaryOrder == SortOrder.ASC)
                                SortOrder.DESC else SortOrder.ASC
                        )
                    } else {
                        sortConfig.copy(
                            primary = SortField.AMOUNT,
                            primaryOrder = SortOrder.DESC,
                            secondary = SortField.DATE
                        )
                    }
                )
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}