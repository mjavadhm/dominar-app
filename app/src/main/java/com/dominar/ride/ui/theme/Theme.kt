package com.dominar.ride.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = TextOnDark,
    onBackground = TextOnDark,
    onSurface = TextOnDark,
    secondary = PrimaryBlueLight,
    onSecondary = TextOnDark,
    tertiary = StatusGood,
    error = StatusDanger,
    outline = BorderDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = TextOnDark,
    onBackground = TextOnLight,
    onSurface = TextOnLight,
    secondary = PrimaryBlueLight,
    onSecondary = TextOnDark,
    tertiary = StatusGood,
    error = StatusDanger,
    outline = BorderLight,
)

@Composable
fun DominarRideTheme(
    darkTheme: Boolean = true, // Default to dark for Minimal OS feel
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
