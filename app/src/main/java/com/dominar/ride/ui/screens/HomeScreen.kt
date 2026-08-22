package com.dominar.ride.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominar.ride.R
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.theme.*

@Composable
fun HomeScreen(
    app: AppState,
    onStartRide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenPerformance: () -> Unit
) {
    val state by app.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        HomeTopBar(onOpenSettings = onOpenSettings)
        VehicleHeader(app = app, state = state, onOpenSettings = onOpenSettings)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "OVERVIEW",
            style = MaterialTheme.typography.labelSmall,
            color = TextSubtleDark,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(8.dp))
        SummaryCard(
            emoji = "\uD83D\uDD27",
            title = "Next service",
            value = "No services logged yet",
            hint = "Set it up in Garage",
            onClick = onOpenGarage
        )
        Spacer(Modifier.height(10.dp))
        SummaryCard(
            emoji = "\u26A1",
            title = "Best 0–100",
            value = "No record yet",
            hint = "Measure it in Performance",
            onClick = onOpenPerformance
        )
        Spacer(Modifier.height(10.dp))
        SummaryCard(
            emoji = "\uD83C\uDD7F\uFE0F",
            title = "Parked location",
            value = "Not saved",
            hint = "Will be saved when the bike disconnects",
            onClick = onOpenGarage
        )
        Spacer(Modifier.height(20.dp))
        StartRideButton(onStartRide)
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DOMINAR",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        IconButton(onClick = onOpenSettings) {
            Text("\u2699\uFE0F", fontSize = 20.sp)
        }
    }
}

@Composable
private fun VehicleHeader(
    app: AppState,
    state: ConnectionState,
    onOpenSettings: () -> Unit
) {
    val (statusText, statusColor) = connectionStatusOf(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.savedDeviceName ?: "Dominar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.savedDeviceAddress ?: "No cluster paired",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSubtleDark
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.12f))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.ic_dominar),
            contentDescription = "Dominar",
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SummaryCard(
    emoji: String,
    title: String,
    value: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSubtleDark
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }
        Text("\u203A", fontSize = 22.sp, color = TextSubtleDark)
    }
}

@Composable
private fun StartRideButton(onStartRide: () -> Unit) {
    Button(
        onClick = onStartRide,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
    ) {
        Text("\uD83C\uDFCD", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text("Start Ride", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
