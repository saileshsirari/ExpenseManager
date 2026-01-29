package com.spendwise.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spendwise.app.MainActivity

@Composable
fun PermissionScreen(
    onPermissionRequest: () -> Unit,
    onNotNow: (() -> Unit)? = null
) {
    val activity = LocalContext.current as MainActivity
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        // -------- Scrollable content --------
        Column(
            modifier = Modifier
                .weight(1f) // takes remaining height
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Allow SMS access to import transactions",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "SpendWise processes SMS messages only to detect financial transaction alerts from banks, cards, wallets and UPI services.”",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "We never hide transactions.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Your SMS data is processed to detect:",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))

            Text("• debits / credits")
            Text("• internal transfers (like wallet top-ups)")
            Text("• credit card payments")

            Spacer(Modifier.height(16.dp))

            Text(
                text = "No advertising. No selling data.\nYou can revoke this permission anytime in Android Settings.",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // -------- Fixed bottom actions --------
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onPermissionRequest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Allow SMS Access")
        }

        if (onNotNow != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not Now")
            }
        }
    }
}
