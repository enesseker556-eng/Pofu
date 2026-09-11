package com.pofu.rider.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Surus modu her zaman koyu: gece kaskin icinden bakarken beyaz ekran gozu aliyor.
// Mor palet, panda simgesinin koyu cercevesiyle ayni aileden.
val Purple = Color(0xFFB57BFF)
val PurpleDeep = Color(0xFF7B3FE4)
val PurpleGlow = Color(0xFFE0C2FF)
val Ink = Color(0xFF12081C)
val Surface1 = Color(0xFF1E1030)
val Surface2 = Color(0xFF2C1A45)
val TextHi = Color(0xFFF4EDFB)
val TextLo = Color(0xFFA793BD)
val Good = Color(0xFF4ADE80)
val Bad = Color(0xFFFF6B81)

// Eski kodun kirilmamasi icin turuncu adi mor renge baglandi.
val Orange = Purple

private val scheme = darkColorScheme(
    primary = Purple,
    onPrimary = Ink,
    secondary = PurpleDeep,
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
