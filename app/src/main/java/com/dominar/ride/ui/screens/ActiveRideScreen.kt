package com.dominar.ride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.view.ContextThemeWrapper
import com.dominar.ride.ui.theme.*
import org.neshan.mapsdk.MapView

@Composable
fun ActiveRideScreen(onStopRide: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Edge-to-Edge Neshan Map
        NeshanMapView(modifier = Modifier.fillMaxSize())

        // 2. Glassmorphism Top Search Bar
        TopSearchBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        )

        // 3. FAB Controls (Right side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassFab(icon = "📍")
            GlassFab(icon = "⚙\uFE0F")
        }

        // 4. Floating Status Bento Panel & Bottom Nav
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            FloatingRideStatus()
            Spacer(modifier = Modifier.height(16.dp))
            GlassBottomNav(onStopRide = onStopRide)
        }
    }
}

@Composable
fun NeshanMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        val themedContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light)
        MapView(themedContext).apply {
            setZoom(14f, 0f)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Neshan MapView doesn't have standard onResume/onPause in their latest standard AndroidView API
            // but if they do, we'd call them here. For now, just rendering it is enough.
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )
}

@Composable
fun TopSearchBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x99000000)) // Semi-transparent black
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🔍", fontSize = 20.sp)
            Text(
                text = "Search destination...",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun GlassFab(icon: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .border(1.dp, Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, fontSize = 24.sp)
    }
}

@Composable
fun FloatingRideStatus() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x99000000))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusItem(label = "SPEED", value = "124", unit = "km/h")
        Divider(modifier = Modifier.height(40.dp).width(1.dp), color = Color(0x33FFFFFF))
        StatusItem(label = "ETA", value = "14", unit = "min")
        Divider(modifier = Modifier.height(40.dp).width(1.dp), color = Color(0x33FFFFFF))
        StatusItem(label = "DIST", value = "4.2", unit = "km")
    }
}

@Composable
fun StatusItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
fun GlassBottomNav(onStopRide: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Color(0xE6000000)) // Darker for nav
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(36.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏠", fontSize = 24.sp)
            Text("🧭", fontSize = 24.sp)
            
            // Stop button in center
            Button(
                onClick = onStopRide,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("🛑", fontSize = 20.sp)
            }
            
            Text("🏍\uFE0F", fontSize = 24.sp)
            Text("👤", fontSize = 24.sp)
        }
    }
}
