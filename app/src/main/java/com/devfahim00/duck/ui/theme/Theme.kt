package com.devfahim00.duck.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Duck ships a single, deliberately dark "midnight glass" identity.
 * Deep blue-black surfaces + warm duck-amber gradients + teal speed accents.
 */
private val MidnightGlassColors = darkColorScheme(
    primary = DuckAmber,
    onPrimary = Color(0xFF2A1D00),
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = SpeedTeal,
    onSecondary = Color(0xFF003733),
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    tertiary = SpeedCyan,
    onTertiary = Color(0xFF00323C),
    tertiaryContainer = Color(0xFF084552),
    onTertiaryContainer = Color(0xFFB8EDF8),
    background = Night1,
    onBackground = InkHigh,
    surface = Night2,
    onSurface = InkHigh,
    surfaceVariant = Night3,
    onSurfaceVariant = InkMedium,
    surfaceContainerLowest = Night0,
    surfaceContainerLow = Night1,
    surfaceContainer = Night2,
    surfaceContainerHigh = Night3,
    surfaceContainerHighest = Night4,
    inverseSurface = InkHigh,
    inverseOnSurface = Night1,
    error = DangerRed,
    onError = Color(0xFF41090B),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineDarkVariant,
    scrim = Color(0xFF04060A),
)

private val DuckShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun DuckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidnightGlassColors,
        typography = DuckTypography,
        shapes = DuckShapes,
        content = content,
    )
}
