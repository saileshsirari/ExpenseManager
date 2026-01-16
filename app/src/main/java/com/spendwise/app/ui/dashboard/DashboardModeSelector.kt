package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode

@Composable
fun DashboardModeSelector(
    mode: DashboardMode,
    onModeChange: (DashboardMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DashboardMode.values().forEach { m ->
            val selected = m == mode
            Button(
                onClick = { onModeChange(m) },
                enabled = !selected
            ) {
                Text(
                    text = m.name.lowercase().replaceFirstChar { it.uppercase() }
                )
            }
        }
    }
}
