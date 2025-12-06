
package com.spendwise.app.ui
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.spendwise.app.MainActivity
import kotlinx.coroutines.launch

@Composable
fun PermissionScreen(onPermissionGranted: () -> Unit) {
    val activity = LocalContext.current as MainActivity
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("SMS Permission Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("SpendWise needs SMS permission to detect expenses. We ignore OTPs and private chats.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            activity.requestSmsPermission()
        }) {
            Text("Grant Permission")
        }
    }
}
