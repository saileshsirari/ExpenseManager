
package com.spendwise.app.ui
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("SpendWise", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Track expenses automatically from bank SMS. We do NOT read OTPs or personal chats.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue) { Text("Continue") }
    }
}
