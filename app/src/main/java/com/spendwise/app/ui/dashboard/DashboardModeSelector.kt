package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode

@Composable
fun DashboardModeSelectorProAware(
    current: DashboardMode,
    isPro: Boolean,
    onChange: (DashboardMode) -> Unit,
    onLocked: (DashboardMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

        DashboardMode.values().forEach { mode ->

            val isAllowed =
                mode == DashboardMode.MONTH || isPro

            FilterChip(
                selected = current == mode,
                enabled = true, // 🔒 NEVER disable
                onClick = {
                    if (isAllowed) onChange(mode)
                    else onLocked(mode)
                },
                label = {
                    Text(
                        when (mode) {
                            DashboardMode.MONTH -> "Month"
                            DashboardMode.QUARTER -> "Quarter"
                            DashboardMode.YEAR -> "Year"
                        },
                        color =
                            if (!isAllowed)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        }
    }
}

