package com.dominar.ride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominar.ride.ui.theme.PrimaryBlue

/**
 * Shared bottom navigation item used in both HomeScreen and ActiveRideScreen.
 */
@Composable
fun NavItem(icon: String, label: String, active: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (active) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = icon,
                fontSize = 20.sp,
                color = if (active) PrimaryBlue else Color.Unspecified
            )
        }
    }
}
