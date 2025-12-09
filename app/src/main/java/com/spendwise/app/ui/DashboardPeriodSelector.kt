package com.spendwise.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.core.extensions.nextQuarter
import com.spendwise.core.extensions.nextYear
import com.spendwise.core.extensions.previousQuarter
import com.spendwise.core.extensions.previousYear
import com.spendwise.core.extensions.quarter
import java.time.YearMonth

@Composable
fun DashboardPeriodSelector(
    mode: DashboardMode,
    current: YearMonth,
    onChange: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = when (mode) {
                DashboardMode.MONTH -> current.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ${current.year}"
                DashboardMode.QUARTER -> "Q${current.quarter()} ${current.year}"
                DashboardMode.YEAR -> current.year.toString()
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

            // PREVIOUS
            Text(
                "<",
                modifier = Modifier.clickable {
                    onChange(
                        when (mode) {
                            DashboardMode.MONTH -> current.minusMonths(1)
                            DashboardMode.QUARTER -> current.previousQuarter()
                            DashboardMode.YEAR -> current.previousYear()
                        }
                    )
                },
                style = MaterialTheme.typography.titleLarge
            )

            // NEXT
            Text(
                ">",
                modifier = Modifier.clickable {
                    onChange(
                        when (mode) {
                            DashboardMode.MONTH -> current.plusMonths(1)
                            DashboardMode.QUARTER -> current.nextQuarter()
                            DashboardMode.YEAR -> current.nextYear()
                        }
                    )
                },
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
