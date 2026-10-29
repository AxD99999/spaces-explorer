package com.hfspaces.explorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Slate950,
    primaryContainer = Color(0xFF4A3000),
    onPrimaryContainer = AmberLight,
    secondary = AmberLight,
    onSecondary = Slate950,
    secondaryContainer = Slate800,
    onSecondaryContainer = Slate100,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate950,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    surfaceContainerLowest = Color(0xFF070B16),
    surfaceContainerLow = Slate900,
    surfaceContainer = Color(0xFF131C31),
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    outline = Slate600,
    outlineVariant = Slate800,
    error = Red300,
    onError = Slate950,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Red300
)

private val LightColors = lightColorScheme(
    // Bright amber fails contrast as text on white (≈2:1); amber-700 passes for both
    // text buttons and white-on-amber filled buttons.
    primary = AmberDark,
    onPrimary = Color.White,
    primaryContainer = AmberContainer,
    onPrimaryContainer = Color(0xFF3D2600),
    secondary = AmberPrimary,
    onSecondary = Slate950,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Slate200,
    surfaceContainerHighest = Slate300,
    outline = Slate400,
    outlineVariant = Slate200,
    error = Red700,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Red700
)

/** Colours for status pills and badges, tuned per theme for contrast. */
@Immutable
data class StatusColors(
    val running: Color,
    val sleeping: Color,
    val building: Color,
    val error: Color,
    val neutral: Color,
    val category: Color,
    val sdk: Color
)

private val LightStatus = StatusColors(
    running = Emerald700,
    sleeping = Amber700,
    building = Blue700,
    error = Red700,
    neutral = Slate600,
    category = Violet700,
    sdk = Sky700
)

private val DarkStatus = StatusColors(
    running = Emerald300,
    sleeping = Amber300,
    building = Blue300,
    error = Red300,
    neutral = Slate300,
    category = Violet300,
    sdk = Sky300
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

@Composable
fun HfSpacesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content
        )
    }
}
