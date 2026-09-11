package com.pofu.rider.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Surus modu her zaman koyu: gece kaskin icinden bakarken beyaz ekran gozu aliyor.
val Orange = Color(0xFFFF7A18)
val Ink = Color(0xFF0B0F14)
val Surface1 = Color(0xFF141B23)
val Surface2 = Color(0xFF1D2733)
val TextHi = Color(0xFFF2F6FA)
val TextLo = Color(0xFF93A2B3)
val Good = Color(0xFF2ECC71)
val Bad = Color(0xFFE74C3C)

private val scheme = darkColorScheme(
    primary = Orange,
    onPrimary = Ink,
    secondary = Orange,
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    error = Bad
)

@Composable
fun PofuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
