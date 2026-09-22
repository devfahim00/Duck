package com.devfahim00.duck.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Duck "Midnight Glass" palette - deep blue-black glass surfaces with warm
// duck-amber gradients and teal speed accents.
// ---------------------------------------------------------------------------

// Brand
val DuckAmber = Color(0xFFFFC94D) // primary - warm duck yellow
val DuckAmberDeep = Color(0xFFF59E0B)
val DuckOrange = Color(0xFFFF9F2E) // gradient end
val DuckOrangeSoft = Color(0xFFFFB259)
val DuckBeak = Color(0xFFF97316)

// Accents
val SpeedTeal = Color(0xFF2DD4BF)
val SpeedCyan = Color(0xFF22D3EE)
val SuccessGreen = Color(0xFF4ADE80)
val DangerRed = Color(0xFFF87171)
val DangerRedDeep = Color(0xFFEF4444)

// Dark glass surfaces
val Night0 = Color(0xFF070A10) // deepest background
val Night1 = Color(0xFF0A0E16) // app background
val Night2 = Color(0xFF10151F) // card surface
val Night3 = Color(0xFF161C28) // elevated surface
val Night4 = Color(0xFF1D2432) // pressed / chips
val OutlineDark = Color(0xFF273042)
val OutlineDarkVariant = Color(0xFF202838)

// Content
val InkHigh = Color(0xFFF2F5FA) // primary text
val InkMedium = Color(0xFFA5AFC4) // secondary text
val InkLow = Color(0xFF6C7690) // tertiary text / hints

// Dark scheme container tones
val AmberContainer = Color(0xFF3B2A06)
val OnAmberContainer = Color(0xFFFFE2A6)
val TealContainer = Color(0xFF0C4640)
val OnTealContainer = Color(0xFFB5F2E9)
val ErrorContainerDark = Color(0xFF511E20)
val OnErrorContainerDark = Color(0xFFF9D6D5)

// ---------------------------------------------------------------------------
// Brand gradients (ordered color ramps used by buttons, bars and badges).
// ---------------------------------------------------------------------------

val DuckGradient = listOf(DuckAmber, DuckOrange)
val DuckGradientSoft = listOf(DuckOrangeSoft, DuckAmber)
val SpeedGradient = listOf(SpeedTeal, SpeedCyan)
val SuccessGradient = listOf(SuccessGreen, Color(0xFF22C55E))
val DangerGradient = listOf(DangerRed, DangerRedDeep)
val NightGradient = listOf(Night1, Night0)
