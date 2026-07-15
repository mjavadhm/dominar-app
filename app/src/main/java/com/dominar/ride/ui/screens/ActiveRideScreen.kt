package com.dominar.ride.ui.screens

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.theme.*
import org.neshan.mapsdk.MapView

@Composable
fun ActiveRideScreen(app: AppState, onStopRide: () -> Unit) {
    val state by app.connectionState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NeshanMapView(modifier = Modifier.fillMaxSize())

        ConnectionPill(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x99000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "NAVIGATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Turn-by-turn routing is coming next — directions will be mirrored to your cluster.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onStopRide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("End Ride", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConnectionPill(state: ConnectionState, modifier: Modifier = Modifier) {
    val (text, color) = when (state) {
        is ConnectionState.Connected -> "Cluster connected" to StatusGood
        is ConnectionState.Connecting -> "Connecting…" to StatusWarning
        is ConnectionState.Reconnecting -> "Reconnecting…" to StatusWarning
        is ConnectionState.Scanning -> "Scanning…" to StatusWarning
        is ConnectionState.Error -> "Connection error" to Color(0xFFEF5350)
        else -> "Cluster disconnected" to Color(0xFF8A93A5)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NeshanMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        val themedContext =
            ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light)
        MapView(themedContext).apply { setZoom(14f, 0f) }
    }
    AndroidView(modifier = modifier, factory = { mapView })
}
