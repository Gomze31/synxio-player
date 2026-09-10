package fr.synxio.player.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import fr.synxio.player.data.model.AccentSource
import fr.synxio.player.data.model.ThemeMode

/** Couleurs de la pochette en cours, accessibles partout (mini-lecteur, listes…). */
val LocalArtworkColors = staticCompositionLocalOf { ArtworkColors() }

@Composable
fun SynxioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentSource: AccentSource = AccentSource.ARTWORK,
    artworkColors: ArtworkColors = ArtworkColors(),
    themePreset: ThemePreset = ThemePreset.SYNXIO,
    customAccent: String = "",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val amoled = themeMode == ThemeMode.AMOLED

    val base: ColorScheme = when {
        accentSource == AccentSource.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        // CUSTOM n'était pas géré : la couleur choisie dans les réglages n'avait
        // jusqu'ici aucun effet visible.
        accentSource == AccentSource.CUSTOM && customAccent.toColorOrNull() != null ->
            ThemePreset.customScheme(customAccent.toColorOrNull()!!, dark, amoled)

        else -> themePreset.scheme(dark, amoled)
    }

    val seed = artworkColors.seed.takeIf { accentSource == AccentSource.ARTWORK }
    val target = if (seed != null) base.tintedWith(seed, dark) else base

    // On anime la transition : changer de morceau repeint l'app en douceur
    // au lieu de faire clignoter toute l'interface.
    val scheme = target.animated()

    CompositionLocalProvider(LocalArtworkColors provides artworkColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SynxioTypography,
            shapes = SynxioShapes,
            content = content,
        )
    }
}

/**
 * Reteinte un schéma à partir d'une couleur de pochette.
 *
 * Plutôt que de régénérer un schéma Material complet (qui demanderait HCT), on
 * remplace les accents et on infuse une trace de la couleur dans les surfaces :
 * le résultat reste lisible tout en donnant l'impression que l'app « prend »
 * la couleur de l'album.
 */
private fun ColorScheme.tintedWith(seed: Color, dark: Boolean): ColorScheme {
    val accent = if (dark) seed.shift(0.15f) else seed.shift(-0.25f)
    val onAccent = accent.contrastingOn()
    val surfaceTintAmount = if (dark) 0.06f else 0.04f

    fun tint(color: Color, amount: Float = surfaceTintAmount) = lerpColor(color, seed, amount)

    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerpColor(surfaceContainerHigh, seed, if (dark) 0.35f else 0.25f),
        onPrimaryContainer = if (dark) Color.White else Color.Black,
        secondary = accent.shift(if (dark) -0.15f else 0.15f),
        onSecondary = onAccent,
        tertiary = accent.shift(if (dark) 0.3f else -0.1f),
        onTertiary = onAccent,
        surfaceTint = accent,
        background = tint(background, surfaceTintAmount * 0.6f),
        surface = tint(surface, surfaceTintAmount * 0.6f),
        surfaceContainerLowest = tint(surfaceContainerLowest),
        surfaceContainerLow = tint(surfaceContainerLow),
        surfaceContainer = tint(surfaceContainer),
        surfaceContainerHigh = tint(surfaceContainerHigh),
        surfaceContainerHighest = tint(surfaceContainerHighest),
        outlineVariant = tint(outlineVariant, surfaceTintAmount * 2),
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(durationMillis = 600)

    @Composable
    fun animate(color: Color) = animateColorAsState(color, spec, label = "scheme").value

    return copy(
        primary = animate(primary),
        onPrimary = animate(onPrimary),
        primaryContainer = animate(primaryContainer),
        onPrimaryContainer = animate(onPrimaryContainer),
        secondary = animate(secondary),
        onSecondary = animate(onSecondary),
        tertiary = animate(tertiary),
        onTertiary = animate(onTertiary),
        background = animate(background),
        onBackground = animate(onBackground),
        surface = animate(surface),
        onSurface = animate(onSurface),
        surfaceTint = animate(surfaceTint),
        surfaceContainerLowest = animate(surfaceContainerLowest),
        surfaceContainerLow = animate(surfaceContainerLow),
        surfaceContainer = animate(surfaceContainer),
        surfaceContainerHigh = animate(surfaceContainerHigh),
        surfaceContainerHighest = animate(surfaceContainerHighest),
        outlineVariant = animate(outlineVariant),
    )
}
