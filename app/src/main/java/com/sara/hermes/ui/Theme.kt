package com.sara.hermes.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BackgroundBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF13131A)
val SurfaceCard = Color(0xFF1E1E28)
val SiriCyan = Color(0xFF00F5D4)
val SiriBlue = Color(0xFF00BBF9)
val SiriPurple = Color(0xFF9B5DE5)
val SiriPink = Color(0xFFF15BB5)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = SiriCyan,
    secondary = SiriPurple,
    tertiary = SiriPink,
    background = BackgroundBlack,
    surface = SurfaceDark,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun SaraHermesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
