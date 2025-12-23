package com.spendwise.domain.com.spendwise.feature.smsimport.data

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

private val categoryColors = listOf(
    Color(0xFFE57373), // Red
    Color(0xFFF06292), // Pink
    Color(0xFFBA68C8), // Purple
    Color(0xFF9575CD), // Deep purple
    Color(0xFF7986CB), // Indigo
    Color(0xFF64B5F6), // Blue
    Color(0xFF4FC3F7), // Light blue
    Color(0xFF4DD0E1), // Cyan
    Color(0xFF4DB6AC), // Teal
    Color(0xFF81C784), // Green
    Color(0xFFAED581), // Light green
    Color(0xFFFFB74D), // Orange
    Color(0xFFFF8A65), // Deep orange
)

fun categoryColorProvider(category: String): Color {
    val index = (category.hashCode().absoluteValue) % categoryColors.size
    return categoryColors[index]
}
