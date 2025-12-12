package com.spendwise.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spendwise.app.MainActivity

@Composable
fun PermissionScreen(onPermissionRequest: () -> Unit) {
    val activity = LocalContext.current as MainActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("SMS Permission Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        Text(
            "SpendWise needs SMS permission to detect expenses.\n" +
                    "We ignore OTPs, promotionals, private messages."
        )
        Spacer(Modifier.height(24.dp))

        Button(onClick = { onPermissionRequest() }) {
            Text("Grant Permission")
        }
    }
}
