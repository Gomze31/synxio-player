package fr.synxio.player.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Un thème complet, pas seulement une couleur d'accent.
 *
 * Ce qui distingue vraiment deux lecteurs de musique, c'est la **tonalité de base** et
 * l'écart entre les surfaces : un thème « noir plat » façon BlackPlayer n'est pas un
 * thème sombre avec un accent différent, c'est un traitement de surfaces différent.
 *
 * @param accentDark accent utilisé sur fond sombre (plus clair pour rester lisible)
 * @param accentLight accent utilisé sur fond clair (plus saturé/foncé)
 * @param baseDark tonalité de fond en mode sombre
 * @param flat surfaces quasi indifférenciées : look minimal, sans empilement de cartes
 */
enum class ThemePreset(
    val label: String,
    val description: String,
    val accentDark: Color,
    val accentLight: Color,
    val baseDark: Color,
    val flat: Boolean = false,
) {
    SYNXIO(
        label = "Synxio",
        description = "Violet électrique sur gris froid",
        accentDark = Color(0xFF9B6BFF),
        accentLight = Color(0xFF6A3CD1),
        baseDark = Color(0xFF0B0B0F),
    ),
    NOIR(
        label = "Noir",
        description = "Noir absolu, surfaces plates, accent blanc — minimal",
        accentDark = Color(0xFFF2F2F5),
        accentLight = Color(0xFF1A1A1A),
        baseDark = Color(0xFF000000),
        flat = true,
    ),
    CARBONE(
        label = "Carbone",
        description = "Anthracite mat et ambre chaud",
        accentDark = Color(0xFFFFB74D),
        accentLight = Color(0xFFB25E00),
        baseDark = Color(0xFF121212),
        flat = true,
    ),
    NUIT(
        label = "Nuit",
        description = "Bleu profond et cyan lumineux",
        accentDark = Color(0xFF5CE1E6),
        accentLight = Color(0xFF00707A),
        baseDark = Color(0xFF080D14),
    ),
    BRAISE(
        label = "Braise",
        description = "Noir charbon traversé d'orange",
        accentDark = Color(0xFFFF7043),
        accentLight = Color(0xFFC1391A),
        baseDark = Color(0xFF0E0A09),
    ),
    FORET(
        label = "Forêt",
        description = "Vert végétal sur fond profond",
        accentDark = Color(0xFF6FE39B),
        accentLight = Color(0xFF00713C),
        baseDark = Color(0xFF08110C),
    ),
    ROSE(
        label = "Rose",
        description = "Magenta doux et surfaces tièdes",
        accentDark = Color(0xFFFF6BB8),
        accentLight = Color(0xFFB4275F),
        baseDark = Color(0xFF140A10),
    ),
    OR(
        label = "Or",
        description = "Doré sobre, contraste élevé",
        accentDark = Color(0xFFE3C46B),
        accentLight = Color(0xFF7A5C00),
        baseDark = Color(0xFF0F0D08),
    );

    /**
     * Construit le schéma Material correspondant.
     *
     * Les surfaces sont dérivées de [baseDark] par éclaircissements successifs. En mode
     * [flat] le pas est minuscule : les cartes ne « flottent » plus, ce qui donne le
     * rendu épuré recherché.
     */
    fun scheme(dark: Boolean, amoled: Boolean): ColorScheme =
        if (dark) darkScheme(amoled) else lightScheme()

    private fun darkScheme(amoled: Boolean): ColorScheme {
        val base = if (amoled) Color.Black else baseDark
        val step = if (flat) 0.025f else 0.055f
        fun surface(level: Int) = lerpColor(base, Color.White, step * level)

        val onAccent = accentDark.contrastingOn()

        return darkColorScheme(
            primary = accentDark,
            onPrimary = onAccent,
            primaryContainer = lerpColor(base, accentDark, 0.28f),
            onPrimaryContainer = Color.White,
            secondary = accentDark.shift(-0.18f),
            onSecondary = onAccent,
            secondaryContainer = lerpColor(base, accentDark, 0.18f),
            onSecondaryContainer = Color.White,
            tertiary = accentDark.shift(0.25f),
            onTertiary = onAccent,
            background = base,
            onBackground = Color(0xFFE8E6EE),
            surface = base,
            onSurface = Color(0xFFE8E6EE),
            surfaceVariant = surface(3),
            onSurfaceVariant = Color(0xFFB4B1BE),
            surfaceContainerLowest = lerpColor(base, Color.Black, 0.4f),
            surfaceContainerLow = surface(1),
            surfaceContainer = surface(2),
            surfaceContainerHigh = surface(3),
            surfaceContainerHighest = surface(4),
            surfaceTint = accentDark,
            outline = surface(7),
            outlineVariant = surface(4),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        )
    }

    private fun lightScheme(): ColorScheme {
        val base = if (flat) Color(0xFFFFFFFF) else Color(0xFFFBF8FF)
        val step = if (flat) 0.018f else 0.035f
        fun surface(level: Int) = lerpColor(base, Color.Black, step * level)

        return lightColorScheme(
            primary = accentLight,
            onPrimary = Color.White,
            primaryContainer = lerpColor(base, accentLight, 0.16f),
            onPrimaryContainer = lerpColor(accentLight, Color.Black, 0.45f),
            secondary = accentLight.shift(0.15f),
            onSecondary = Color.White,
            secondaryContainer = lerpColor(base, accentLight, 0.1f),
            onSecondaryContainer = lerpColor(accentLight, Color.Black, 0.45f),
            tertiary = accentLight.shift(-0.15f),
            onTertiary = Color.White,
            background = base,
            onBackground = Color(0xFF1B1B21),
            surface = base,
            onSurface = Color(0xFF1B1B21),
            surfaceVariant = surface(3),
            onSurfaceVariant = Color(0xFF4A4650),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = surface(1),
            surfaceContainer = surface(2),
            surfaceContainerHigh = surface(3),
            surfaceContainerHighest = surface(4),
            surfaceTint = accentLight,
            outline = surface(10),
            outlineVariant = surface(5),
        )
    }

    companion object {
        fun fromName(name: String?): ThemePreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYNXIO

        /** Schéma dérivé d'une couleur libre choisie par l'utilisateur, combiné avec le preset actuel. */
        fun customScheme(accent: Color, preset: ThemePreset, dark: Boolean, amoled: Boolean): ColorScheme {
            val base = preset.darkScheme(amoled)
            return if (dark) {
                base.copy(
                    primary = accent.shift(0.1f),
                    onPrimary = accent.contrastingOn(),
                    secondary = accent.shift(-0.15f),
                    tertiary = accent.shift(0.3f),
                    surfaceTint = accent,
                )
            } else {
                preset.lightScheme().copy(
                    primary = accent.shift(-0.2f),
                    onPrimary = Color.White,
                    secondary = accent,
                    tertiary = accent.shift(-0.35f),
                    surfaceTint = accent,
                )
            }
        }
    }
}

/** Convertit `#RRGGBB` en [Color]. Retourne `null` si la chaîne est invalide. */
fun String.toColorOrNull(): Color? = runCatching {
    Color(android.graphics.Color.parseColor(if (startsWith("#")) this else "#$this"))
}.getOrNull()
