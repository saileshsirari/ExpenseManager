package com.spendwise.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.spendwise.app.ui.SpendWiseApp
import com.spendwise.app.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SpendWiseTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SpendWiseApp()   // All app logic lives inside Compose, including SMS import
                }
            }
        }
    }
}
