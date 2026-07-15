package com.dominar.ride

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
import android.content.Intent
import android.os.Build
import com.dominar.ride.ui.screens.ActiveRideScreen
import com.dominar.ride.ui.screens.BleTestScreen
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
                // Screen: "home", "ride", "bletest"
                var currentScreen by remember { mutableStateOf("home") }
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
                            "ride" -> ActiveRideScreen(onStopRide = { currentScreen = "home" })
                            "bletest" -> BleTestScreen(
                                viewModel = bleTestViewModel,
                                onBack = { currentScreen = "home" }
                            )
                            else -> HomeScreen(
                                onStartRide = { currentScreen = "ride" },
                                onOpenBleTest = { currentScreen = "bletest" }
                            )
                        }
                    }
                }
            }
        }
        requestRuntimeEssentials()
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
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName"))
            )
        }
    }
}
