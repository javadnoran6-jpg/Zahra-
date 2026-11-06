package ir.zahra.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF04100A)
val BgTop = Color(0xFF0C2E1D)
val Card = Color(0xFF0E2218)
val CardSel = Color(0xFF123626)
val Emerald = Color(0xFF2EE59D)
val EmeraldDeep = Color(0xFF0F9D58)
val Amber = Color(0xFFFFC24D)
val Danger = Color(0xFFFF6B6B)
val Muted = Color(0xFF8FB5A2)

@Composable
fun ZahraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Emerald,
            onPrimary = Color.Black,
            background = Bg,
            surface = Card,
            onSurface = Color.White,
        ),
        content = content,
    )
}
