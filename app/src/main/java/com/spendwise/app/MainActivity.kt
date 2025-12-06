package com.spendwise.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.spendwise.app.ui.theme.SpendWiseTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // navController will be initialized inside setContent
    private lateinit var navController: NavHostController

    // If permission result arrives before navController is ready, remember it
    private var pendingNavigateToDashboard: Boolean = false

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // If navController already initialized -> navigate immediately,
                // otherwise set flag so we navigate as soon as navController is ready.
                if (::navController.isInitialized) {
                    navigateToDashboard()
                } else {
                    pendingNavigateToDashboard = true
                }
            } else {
                // handle permission denied if needed
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SpendWiseTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    // create navController inside compose
                    navController = rememberNavController()

                    // attach nav graph
                    com.spendwise.app.navigation.AppNavGraph(navController = navController)

                    // If permission result already arrived earlier, navigate now.
                    LaunchedEffect(Unit) {
                        if (pendingNavigateToDashboard) {
                            // clear flag and navigate on main thread
                            pendingNavigateToDashboard = false
                            navigateToDashboard()
                        }
                    }
                }
            }
        }
    }

    // called from composable (PermissionScreen) -> request permission
    fun requestSmsPermission() {
        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
    }

    // single place to navigate safely
    private fun navigateToDashboard() {
        // ensure we run navigation on main thread
        MainScope().launch {
            if (::navController.isInitialized) {
                navController.navigate(com.spendwise.app.navigation.Routes.DASHBOARD) {
                    popUpTo(com.spendwise.app.navigation.Routes.PERMISSION) { inclusive = true }
                }
            } else {
                // fallback: set pending flag if somehow called before initialization
                pendingNavigateToDashboard = true
            }
        }
    }
}
