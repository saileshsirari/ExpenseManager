package com.spendwise.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.core.extensions.toQuarterTitle
import com.spendwise.domain.com.spendwise.feature.smsimport.data.DashboardMode
import java.time.YearMonth

@Composable
fun PeriodNavigator(
    mode: DashboardMode,
    period: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val title = when (mode) {
        DashboardMode.MONTH ->
            period.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
        DashboardMode.QUARTER ->
            period.toQuarterTitle()
        DashboardMode.YEAR ->
            period.year.toString()
    }
    Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures { _, dragAmount ->
            if (dragAmount > 0) onPrev() else onNext()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "<",
            modifier = Modifier
                .clickable { onPrev() }
                .padding(12.dp),
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            ">",
            modifier = Modifier
                .clickable { onNext() }
                .padding(12.dp),
            style = MaterialTheme.typography.titleLarge
        )
    }
}
