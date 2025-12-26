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
import kotlin.math.min

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
    data: List<Double>,
    labels: List<String>,
    colors: List<Color>,
    selectedLabel: String?,
    onSliceClick: ((String?) -> Unit)? = null
) {
    if (data.isEmpty()) return

    val total = data.sum()
    if (total == 0.0) return

    val proportions = remember(data) {
        data.map { it / total }
    }

    Canvas(
        modifier = Modifier
            .size(260.dp)
            .pointerInput(labels, selectedLabel) {
                detectTapGestures { tapOffset ->

                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = tapOffset.x - center.x
                    val dy = tapOffset.y - center.y
                    val radius = min(size.width, size.height) / 2f

                    // Outside circle
                    if (dx * dx + dy * dy > radius * radius) return@detectTapGestures

                    var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    angle = (angle + 450) % 360

                    var startAngle = 0f

                    proportions.forEachIndexed { i, prop ->
                        val sweep = (prop * 360f).toFloat()
                        val end = startAngle + sweep

                        if (angle in startAngle..end) {
                            val clicked = labels[i]
                            val newSelection =
                                if (clicked == selectedLabel) null else clicked
                            onSliceClick?.invoke(newSelection)
                            return@detectTapGestures
                        }

                        startAngle += sweep
                    }
                }
            }
    ) {

        var startAngle = 0f

        proportions.forEachIndexed { i, prop ->
            val sweep = (prop * 360f).toFloat()
            val label = labels[i]

            val isSelected = selectedLabel == null || selectedLabel == label
            val alpha = if (selectedLabel == null || selectedLabel == label) 1f else 0.35f
            val expansion = if (selectedLabel == label) 8.dp.toPx() else 0f

            val arcSize = Size(
                width = size.width + expansion,
                height = size.height + expansion
            )

            val topLeft = Offset(
                x = (size.width - arcSize.width) / 2f,
                y = (size.height - arcSize.height) / 2f
            )

            drawArc(
                color = colors[i % colors.size].copy(alpha = alpha),
                startAngle = startAngle - 90,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )

            startAngle += sweep
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
    val barCount = sortedKeys.size
    val spacingFactor = 1.4f   // good balance of spacing

    var barWidthPx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(barCount, sortedKeys, barWidthPx) {
                detectTapGestures { offset ->

                    if (barWidthPx == 0f) return@detectTapGestures

                    // Compute index based on bar+spacing width
                    val slotWidth = barWidthPx * spacingFactor
                    val index = (offset.x / slotWidth).toInt()

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







