package com.dominar.ride

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.screens.ActiveRideScreen
import com.dominar.ride.ui.screens.GarageScreen
import com.dominar.ride.ui.screens.HomeScreen
import com.dominar.ride.ui.screens.PerformanceScreen
import com.dominar.ride.ui.screens.SettingsScreen
import com.dominar.ride.ui.theme.DominarRideTheme
import dagger.hilt.android.AndroidEntryPoint

private data class BottomTab(val route: String, val label: String, val emoji: String)

private val bottomTabs = listOf(
    BottomTab("home", "Home", "\uD83C\uDFE0"),
    BottomTab("ride", "Ride", "\uD83D\uDDFA\uFE0F"),
    BottomTab("garage", "Garage", "\uD83D\uDD27"),
    BottomTab("performance", "Perf", "\u26A1")
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DominarRideTheme {
                val appState = remember { AppState(applicationContext) }
                var currentTab by rememberSaveable { mutableStateOf("home") }
                var showSettings by rememberSaveable { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(app = appState, onBack = { showSettings = false })
                    } else {
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    bottomTabs.forEach { tab ->
                                        NavigationBarItem(
                                            selected = currentTab == tab.route,
                                            onClick = { currentTab = tab.route },
                                            icon = { Text(tab.emoji, fontSize = 18.sp) },
                                            label = { Text(tab.label) }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .fillMaxSize()
                            ) {
                                when (currentTab) {
                                    "ride" -> ActiveRideScreen(
                                        app = appState,
                                        onStopRide = { currentTab = "home" }
                                    )
                                    "garage" -> GarageScreen()
                                    "performance" -> PerformanceScreen()
                                    else -> HomeScreen(
                                        app = appState,
                                        onStartRide = { currentTab = "ride" },
                                        onOpenSettings = { showSettings = true },
                                        onOpenGarage = { currentTab = "garage" },
                                        onOpenPerformance = { currentTab = "performance" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        requestRuntimeEssentials()
        requestPhonePermissions()
        requestBluetoothAndLocationPermissions()
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

    private fun requestBluetoothAndLocationPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(android.Manifest.permission.BLUETOOTH_SCAN)
                add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            // Needed for the map's my-location feature on every API level.
            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 102)
    }
}
