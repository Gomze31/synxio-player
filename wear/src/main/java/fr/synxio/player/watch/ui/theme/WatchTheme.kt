package fr.synxio.player.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val Ink = Color(0xFF0C0A10)
val Ember = Color(0xFFFF6A3D)

/** Lumen sur montre : encre partout, accent de la pochette, texte clair. */
@Composable
fun WatchTheme(accent: Int, content: @Composable () -> Unit) {
    val primary = if (accent == 0) Ember else Color(accent)
    val onPrimary = if (primary.luminance() > 0.5f) Ink else Color.White
    val scheme = ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.24f),
        onPrimaryContainer = Color.White,
        secondary = primary,
        onSecondary = onPrimary,
        background = Ink,
        onBackground = Color.White,
        surfaceContainer = Color(0xFF1A1620),
        surfaceContainerLow = Color(0xFF13101A),
        surfaceContainerHigh = Color(0xFF221D2A),
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        outline = Color.White.copy(alpha = 0.3f),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
