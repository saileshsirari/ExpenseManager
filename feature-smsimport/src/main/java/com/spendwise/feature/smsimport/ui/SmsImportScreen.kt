
package com.spendwise.feature.smsimport.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.feature.smsimport.ui.SmsImportViewModel

@Composable
fun SmsImportScreen(viewModel: SmsImportViewModel = hiltViewModel()) {
    val items = viewModel.items.collectAsState().value
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { /* Trigger permission -> import handled by Activity */ }) {
            Text("Import SMS (requires permission)")
        }
        items.forEach { tx ->
            Card(modifier = Modifier.padding(vertical = 6.dp)) {
                Text("${'$'}{tx.merchant ?: tx.sender} — ₹${'$'}{tx.amount}", modifier = Modifier.padding(8.dp))
            }
        }
    }
}
