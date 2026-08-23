package com.dominar.ride

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
import com.dominar.ride.data.DevicePrefs
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.permissions.PermissionGate
import com.dominar.ride.ui.screens.ActiveRideScreen
import com.dominar.ride.ui.screens.GarageScreen
import com.dominar.ride.ui.screens.HomeScreen
import com.dominar.ride.ui.screens.OnboardingScreen
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
                val prefs = remember { DevicePrefs(applicationContext) }
                var currentTab by rememberSaveable { mutableStateOf("home") }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showOnboarding by rememberSaveable {
                    mutableStateOf(!prefs.onboardingDone)
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        // First run (or reopened from Settings): explain every
                        // permission and let the user grant them one by one.
                        showOnboarding -> OnboardingScreen(
                            onDone = {
                                prefs.onboardingDone = true
                                showOnboarding = false
                            }
                        )
                        showSettings -> SettingsScreen(
                            app = appState,
                            onBack = { showSettings = false },
                            onOpenPermissions = { showOnboarding = true }
                        )
                        else -> Scaffold(
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
                                    "ride" -> PermissionGate(
                                        permissions = listOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        ),
                                        emoji = "\uD83D\uDCCD",
                                        title = "Location powers the Ride screen",
                                        description = "It's used for the map, navigation, GPS " +
                                            "speed and your parking spot \u2014 nothing is " +
                                            "shared anywhere."
                                    ) {
                                        ActiveRideScreen(
                                            app = appState,
                                            onStopRide = { currentTab = "home" }
                                        )
                                    }
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
    }
}
