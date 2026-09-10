package fr.synxio.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palette Synxio : un violet électrique qui vire au magenta, sur des gris très froids.
val SynxioViolet = Color(0xFF9B6BFF)
val SynxioMagenta = Color(0xFFC56BFF)
val SynxioPink = Color(0xFFFF6BB8)
val SynxioCoral = Color(0xFFFF8A5C)
val SynxioCyan = Color(0xFF5CE1E6)

val Ink900 = Color(0xFF0B0B0F)
val Ink800 = Color(0xFF121218)
val Ink700 = Color(0xFF1A1A22)
val Ink600 = Color(0xFF23232D)
val Ink500 = Color(0xFF2F2F3B)

val SynxioDarkScheme = darkColorScheme(
    primary = SynxioViolet,
    onPrimary = Color(0xFF14002F),
    primaryContainer = Color(0xFF3A1F6B),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = SynxioMagenta,
    onSecondary = Color(0xFF2A0033),
    secondaryContainer = Color(0xFF4A1F58),
    onSecondaryContainer = Color(0xFFF6DDFF),
    tertiary = SynxioPink,
    onTertiary = Color(0xFF3A0020),
    tertiaryContainer = Color(0xFF5E1F42),
    onTertiaryContainer = Color(0xFFFFD9E9),
    background = Ink900,
    onBackground = Color(0xFFE8E6EE),
    surface = Ink900,
    onSurface = Color(0xFFE8E6EE),
    surfaceVariant = Ink600,
    onSurfaceVariant = Color(0xFFB9B6C6),
    surfaceContainerLowest = Color(0xFF060609),
    surfaceContainerLow = Ink800,
    surfaceContainer = Ink700,
    surfaceContainerHigh = Ink600,
    surfaceContainerHighest = Ink500,
    outline = Color(0xFF4A4A58),
    outlineVariant = Color(0xFF32323E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

val SynxioLightScheme = lightColorScheme(
    primary = Color(0xFF6A3CD1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF23005C),
    secondary = Color(0xFF8B3FBF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7D9FF),
    onSecondaryContainer = Color(0xFF31004A),
    tertiary = Color(0xFFC03A7E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E7),
    onTertiaryContainer = Color(0xFF3E001F),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F1FA),
    surfaceContainer = Color(0xFFEFEBF4),
    surfaceContainerHigh = Color(0xFFE9E5EF),
    surfaceContainerHighest = Color(0xFFE3DFE9),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF),
)

/** Variante AMOLED : noir absolu, les pixels éteints économisent réellement la batterie. */
val SynxioAmoledScheme = SynxioDarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF17171B),
    surfaceContainerHighest = Color(0xFF1E1E23),
)
