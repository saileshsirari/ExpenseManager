package com.spendwise.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.spendwise.core.ml.CategoryType

@Composable
fun CategoryPickerDialog(
    selected: CategoryType,
    onSelect: (CategoryType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Category") },
        text = {
            Column {
                CategoryType.values().forEach { cat ->
                    TextButton(onClick = { onSelect(cat) }) {
                        Text(cat.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
