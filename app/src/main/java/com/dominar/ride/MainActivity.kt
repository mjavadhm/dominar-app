package com.dominar.ride

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.screens.ActiveRideScreen
import com.dominar.ride.ui.screens.BleTestScreen
import com.dominar.ride.ui.screens.ClusterTestScreen
import com.dominar.ride.ui.screens.BleTestViewModel
import com.dominar.ride.ui.screens.HomeScreen
import com.dominar.ride.ui.theme.DominarRideTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DominarRideTheme {
                var currentScreen by remember { mutableStateOf("home") }
                val appState = remember { AppState(applicationContext) }
                val bleTestViewModel = remember { BleTestViewModel() }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            "ride" -> ActiveRideScreen(
                                app = appState,
                                onStopRide = { currentScreen = "home" }
                            )
                            "bletest" -> BleTestScreen(
                                viewModel = bleTestViewModel,
                                onBack = { currentScreen = "home" }
                            )
                            "clustertest" -> ClusterTestScreen(
                                onBack = { currentScreen = "home" }
                            )
                            else -> HomeScreen(
                                app = appState,
                                onStartRide = { currentScreen = "ride" },
                                onOpenBleTest = { currentScreen = "bletest" },
                                onOpenClusterTest = { currentScreen = "clustertest" }
                            )
                        }
                    }
                }
            }
        }
        requestRuntimeEssentials()
        requestPhonePermissions()
        requestBluetoothPermissions()
    }

    private fun requestRuntimeEssentials() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun requestPhonePermissions() {
        val needed = arrayOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ANSWER_PHONE_CALLS
        ).filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 101)

        val enabled = androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(this)
        if (!enabled.contains(packageName)) {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun requestBluetoothPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 102)
    }
}
