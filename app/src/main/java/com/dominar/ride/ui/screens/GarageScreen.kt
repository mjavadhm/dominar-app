package com.dominar.ride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominar.ride.ui.theme.PrimaryBlue
import com.dominar.ride.ui.theme.TextSubtleDark

@Composable
fun GarageScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Garage",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(
            text = "Keep your Dominar healthy — maintenance, fuel and paperwork in one place.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSubtleDark
        )
        Spacer(Modifier.height(16.dp))
        ComingSoonCard(
            emoji = "\uD83D\uDD27",
            title = "Service log",
            description = "Track engine oil, coolant, filters, chain and brake pads with km + time reminders."
        )
        Spacer(Modifier.height(10.dp))
        ComingSoonCard(
            emoji = "\u26FD",
            title = "Fuel log",
            description = "Log every fill-up and see real consumption and monthly cost."
        )
        Spacer(Modifier.height(10.dp))
        ComingSoonCard(
            emoji = "\uD83D\uDCC4",
            title = "Documents",
            description = "Insurance and technical inspection expiry reminders."
        )
        Spacer(Modifier.height(10.dp))
        ComingSoonCard(
            emoji = "\uD83C\uDD7F\uFE0F",
            title = "Parking",
            description = "Save where you parked — automatically when the bike disconnects."
        )
    }
}

@Composable
private fun ComingSoonCard(emoji: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "SOON",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimaryBlue.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtleDark
            )
        }
    }
}
