package com.dominar.ride.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dominar.ride.ui.permissions.PermissionStep
import com.dominar.ride.ui.permissions.isStepGranted
import com.dominar.ride.ui.permissions.permissionSteps
import com.dominar.ride.ui.permissions.specialAccessIntent
import com.dominar.ride.ui.theme.PrimaryBlue
import com.dominar.ride.ui.theme.StatusGood
import com.dominar.ride.ui.theme.TextSubtleDark

/**
 * One UI-style first-run screen: a big airy header up top, then one card per
 * permission explaining WHY it's needed, each with its own Allow button.
 * Nothing is requested behind the user's back, optional steps can be skipped,
 * and the screen can be reopened later from Settings.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val steps = remember { permissionSteps() }
    var refresh by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    // Re-check grants after returning from system settings screens.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val granted = remember(refresh) {
        steps.associate { it.key to isStepGranted(context, it) }
    }
    val requiredMissing = steps.count { it.required && granted[it.key] != true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- One UI-style header: generous whitespace in the top third ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(top = 56.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("\uD83C\uDFCD", fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Welcome to Dominar Ride",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Here's what the app needs and why. You're in control \u2014 " +
                    "optional ones can wait until you want the feature.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubtleDark,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            steps.forEach { step ->
                PermissionCard(
                    step = step,
                    granted = granted[step.key] == true,
                    onAllow = {
                        val special = step.special
                        if (special != null) {
                            runCatching {
                                context.startActivity(specialAccessIntent(context, special))
                            }
                        } else {
                            launcher.launch(step.permissions.toTypedArray())
                        }
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    text = if (requiredMissing == 0) "Get started" else "Continue anyway",
                    fontWeight = FontWeight.Bold
                )
            }
            if (requiredMissing > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Features stay off until their permission is granted. " +
                        "You can come back anytime from Settings \u2192 Permission guide.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSubtleDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    step: PermissionStep,
    granted: Boolean,
    onAllow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(step.emoji, fontSize = 20.sp) }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (!step.required) {
                    Text(
                        text = "Optional",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSubtleDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TextSubtleDark.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtleDark
            )
        }
        if (granted) {
            Text(
                text = "\u2713",
                color = StatusGood,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            TextButton(onClick = onAllow) {
                Text("Allow", fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
            }
        }
    }
}
