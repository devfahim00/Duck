package com.devfahim00.duck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF825500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF291800),
    secondary = Color(0xFF6C5C3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E0BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF00696D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF0F5),
    onTertiaryContainer = Color(0xFF002022)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC148),
    onPrimary = Color(0xFF442B00),
    primaryContainer = Color(0xFF624000),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD8C4A0),
    onSecondary = Color(0xFF3A2F15),
    secondaryContainer = Color(0xFF52452A),
    onSecondaryContainer = Color(0xFFF5E0BB),
    tertiary = Color(0xFF54D6DD),
    onTertiary = Color(0xFF002023),
    tertiaryContainer = Color(0xFF004F54),
    onTertiaryContainer = Color(0xFF9CF0F5)
)

@Composable
fun DuckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DuckTypography,
        content = content
    )
}
