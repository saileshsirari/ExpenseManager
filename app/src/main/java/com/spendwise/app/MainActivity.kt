package com.spendwise.app
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.spendwise.app.ui.SpendWiseApp
import com.spendwise.app.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for SMS permission immediately
        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)

        setContent {
            SpendWiseTheme {
                Surface (color = MaterialTheme.colorScheme.background) {
                    SpendWiseApp()     // 👈 This now controls ALL navigation
                }
            }
        }
    }
}
