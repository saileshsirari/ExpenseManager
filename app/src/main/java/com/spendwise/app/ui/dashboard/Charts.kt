package com.spendwise.app.ui.dashboard

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.atan2

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MonthSelector(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Text("< Prev")
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
            Text("Next >")
        }
    }
}

@Composable
fun CategoryPieChart(
    data: Map<String, Double>,
    onSliceClick: (String) -> Unit
){
    if (data.isEmpty()) return

    val colors = listOf(
        Color(0xffE57373), Color(0xff64B5F6), Color(0xff81C784),
        Color(0xffFFD54F), Color(0xffBA68C8), Color(0xff4DB6AC)
    )

    val total = data.values.sum().toFloat()

    // Convert angles to Float
    val sweepAngles = data.values.map { value ->
        ((value.toFloat() / total) * 360f)
    }
    val touchPoint = remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = Modifier
            .size(220.dp)
            .pointerInput(true) {
                detectTapGestures { offset -> touchPoint.value = offset }
            }
    ) {
        val sliceAngles = data.mapValues { (it.value.toFloat() / total) * 360f }
        var startAngle = -90f


        val radius = size.minDimension / 2

        sliceAngles.entries.forEachIndexed { index, entry ->
            val cat = entry.key
            val angle = entry.value

            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = angle,
                useCenter = true
            )

            // handle tap:
            touchPoint.value?.let { pt ->
                val dx = pt.x - size.width / 2
                val dy = pt.y - size.height / 2
                val touchAngle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    .let { if (it < -90) it + 360 else it }

                val sectorStart = startAngle
                val sectorEnd = startAngle + angle

                if (touchAngle in sectorStart..sectorEnd) {
                    onSliceClick(cat)
                }
            }

            startAngle += angle
        }
    }
}



@Composable
fun DailyBarChart(
    data: Map<Int, Double>,
    onBarClick: (Int) -> Unit
) {
    if (data.isEmpty()) return

    val sortedKeys = data.keys.sorted()
    val sortedValues = sortedKeys.map { data[it] ?: 0.0 }

    val max = sortedValues.maxOrNull()?.toFloat() ?: 1f

    // Precompute bar width *outside* Canvas
    val barCount = sortedKeys.size
    val spacingFactor = 1.5f

    var barWidthPx by remember { mutableStateOf(0f) }  // updated once canvas is known

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(barCount, sortedKeys) {
                detectTapGestures { offset ->

                    if (barWidthPx == 0f) return@detectTapGestures

                    val index = (offset.x / (barWidthPx * spacingFactor)).toInt()

                    val day = sortedKeys.getOrNull(index)
                    if (day != null) {
                        onBarClick(day)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {

            // compute bar width now that size is known
            barWidthPx = size.width / (barCount * spacingFactor)

            sortedValues.forEachIndexed { index, amount ->
                val barHeight = (amount.toFloat() / max) * size.height

                drawRect(
                    color = Color(0xff64B5F6),
                    topLeft = Offset(
                        x = index * (barWidthPx * spacingFactor),
                        y = size.height - barHeight
                    ),
                    size = Size(barWidthPx, barHeight)
                )
            }
        }
    }
}






