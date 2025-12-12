package com.spendwise.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spendwise.feature.smsimport.ui.SmsImportViewModel.ImportProgress

@Composable
fun ImportProgressScreen(progress: ImportProgress) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        if (progress.total > 0) {
            val fraction = progress.processed.toFloat() / progress.total.toFloat()

            LinearProgressIndicator(progress = fraction)
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Scanning messages… ${progress.processed}/${progress.total}",
                style = MaterialTheme.typography.titleLarge
            )
        } else {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Preparing import…")
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = progress.message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}
