package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.core.ml.CategoryType
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.time.LocalDate

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AddExpenseScreen(
    viewModel: SmsImportViewModel = hiltViewModel(),
    onDone: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CategoryType.OTHER) }
    var note by remember { mutableStateOf("") }

    val now = LocalDate.now()
    var date by remember { mutableStateOf(now) }

    val openCategoryPicker = remember { mutableStateOf(false) }

    if (openCategoryPicker.value) {
        CategoryPickerDialog(
            selected = category,
            onSelect = {
                category = it
                openCategoryPicker.value = false
            },
            onDismiss = { openCategoryPicker.value = false }
        )
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        item {
            Text("Add Expense", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
        }

        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") }
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") }
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            TextButton(onClick = { openCategoryPicker.value = true }) {
                Text("Category: ${category.name}")
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            Text("Date: $date")
            Spacer(Modifier.height(12.dp))
        }

        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull() ?: 0.0
                    if (amountValue > 0) {
                        viewModel.addManualExpense(
                            amount = amountValue,
                            merchant = merchant,
                            category = category,
                            date = date,
                            note = note
                        )
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Expense")
            }
        }
    }
}
