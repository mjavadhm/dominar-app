package com.dominar.ride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dominar.ride.R
import com.dominar.ride.ui.theme.*

@Composable
fun HomeScreen(onStartRide: () -> Unit, onOpenBleTest: () -> Unit = {}) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp)
        ) {
            // Header
            HomeHeader(onOpenBleTest = onOpenBleTest)

            Spacer(modifier = Modifier.height(4.dp))

            // Vehicle Card
            VehicleCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Start Ride Button
            Button(
                onClick = onStartRide,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications, // placeholder
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Ride",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Smart Maintenance Section
            MaintenanceSection()

            Spacer(modifier = Modifier.height(20.dp))

            // Find My Bike Section
            FindMyBikeCard()

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom Navigation
        HomeBottomNav(
            modifier = Modifier.align(Alignment.BottomCenter),
            onStartRide = onStartRide
        )
    }
}

@Composable
private fun HomeHeader(onOpenBleTest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bluetooth status button
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 18.sp)
                // Animated dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StatusGood)
                        .align(Alignment.TopEnd)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // BLE Test button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.15f))
                    .clickable { onOpenBleTest() },
                contentAlignment = Alignment.Center
            ) {
                Text("📡", fontSize = 18.sp)
            }

            // Notifications button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun VehicleCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            PrimaryBlue.copy(alpha = 0.08f)
                        )
                    )
                )
        )

        Column(modifier = Modifier.padding(20.dp)) {
            // Top row: bike name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Dominar 250",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StatusGood)
                        )
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusGood,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bike image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dominar),
                    contentDescription = "Dominar 250",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ODOMETER",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSubtleDark,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "12,450",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "km",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSubtleDark,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "FUEL RANGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSubtleDark,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "140",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "km",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSubtleDark,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceSection() {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Smart Maintenance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryBlue,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MaintenanceCard(
                icon = "🛢",
                label = "Engine Oil",
                value = "700km left",
                status = StatusGood
            )
            MaintenanceCard(
                icon = "⛓",
                label = "Chain",
                value = "Needs Lube",
                status = StatusWarning
            )
            MaintenanceCard(
                icon = "🛑",
                label = "Brake Pads",
                value = "Good",
                status = StatusGood
            )
            MaintenanceCard(
                icon = "🔋",
                label = "Battery",
                value = "12.6V",
                status = StatusGood
            )
        }
    }
}

@Composable
private fun MaintenanceCard(
    icon: String,
    label: String,
    value: String,
    status: Color
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = BorderDark,
                shape = RoundedCornerShape(14.dp)
            )
            // Left colored accent border
            .padding(start = 1.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status stripe
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(status)
        )
        Text(text = icon, fontSize = 22.sp)
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FindMyBikeCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 16.sp)
                }
                Text(
                    text = "Find My Bike",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Updated 2m ago",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Abstract map placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A2438),
                            Color(0xFF0D1520)
                        )
                    )
                )
        ) {
            // Grid overlay to mimic map
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                PrimaryBlue.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Location info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("📍", fontSize = 12.sp)
                    Text(
                        text = "124, Marina Bay Sands, Singapore",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Location dot
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = BorderDark
            )
        ) {
            Text("🧭", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Navigate to Bike",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HomeBottomNav(modifier: Modifier = Modifier, onStartRide: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            )
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .border(
                    width = 1.dp,
                    color = BorderDark,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(icon = "🏠", label = "Home", active = true)
                NavItem(icon = "🗺", label = "Map", active = false)

                // FAB center button
                Box(
                    modifier = Modifier
                        .offset(y = (-20).dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Shadow ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏍", fontSize = 22.sp)
                    }
                }

                NavItem(icon = "📜", label = "History", active = false)
                NavItem(icon = "⚙", label = "Settings", active = false)
            }
        }
    }
}


