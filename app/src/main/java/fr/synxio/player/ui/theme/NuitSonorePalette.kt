package fr.synxio.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Les cinq rôles de couleur de la palette « Nuit sonore » (spec §1.1), dérivés de la
 * pochette en cours de lecture, plus les deux teintes de texte qui vont avec `base`.
 *
 * Aucun écran ne choisit plus une couleur à la main : tout part d'ici.
 */
data class NuitSonorePalette(
    val base: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val accent: Color,
    val glow: Color,
    val onBase: Color,
    val onBaseMuted: Color,
)

/** Ratio de contraste minimal imposé par la spec §1.2 pour `accent`, `onBase` et `onBaseMuted`. */
private const val MIN_CONTRAST = 4.5f

/** Saturation HSL cible de `base` (spec §1.1 : « désaturée à ~8 % »). */
private const val BASE_SATURATION = 0.08f

/** Luminosité HSL cible de `base` (spec §1.1 : « ramenée à ~6 % »). */
private const val BASE_LIGHTNESS = 0.06f

/** Saturation HSL plancher de `accent` une fois remontée (spec §1.1). */
private const val ACCENT_MIN_SATURATION = 0.55f

/** Plage de luminosité HSL dans laquelle `accent` est recentré avant vérification du contraste. */
private const val ACCENT_MIN_LIGHTNESS = 0.45f
private const val ACCENT_MAX_LIGHTNESS = 0.72f

/** Opacité de `glow`, dérivé d'`accent` (spec §1.1). */
private const val GLOW_ALPHA = 0.32f

/** Opacités de `onBase` et `onBaseMuted` avant garde-fou de contraste (spec §1.2). */
private const val ON_BASE_ALPHA = 0.92f
private const val ON_BASE_MUTED_ALPHA = 0.58f

/**
 * Dérive la palette « Nuit sonore » depuis la pochette en cours.
 *
 * @param artworkColors couleurs extraites de la pochette par [rememberArtworkColors]. Le
 *   garde-fou « pochette inexploitable » (spec §1.2) est déjà appliqué en amont par
 *   [ArtworkColors] : `seed == null` signale une pochette trop terne ou absente, auquel cas
 *   on retombe ici sur [ThemePreset.SYNXIO]`.accentDark`, comme le veut la spec. On ne
 *   réévalue pas de second seuil de saturation.
 * @param preset preset de thème courant : fournit le pas de luminosité des surfaces (0.055f
 *   normal, 0.025f en variante plate) et l'accent de repli.
 */
fun deriveNuitSonorePalette(
    artworkColors: ArtworkColors,
    preset: ThemePreset,
): NuitSonorePalette {
    val step = if (preset.flat) 0.025f else 0.055f
    val dominant = artworkColors.seed

    val hue = dominant?.let { toHsl(it).first } ?: 0f
    val base = fromHsl(hue, BASE_SATURATION, BASE_LIGHTNESS)

    val surface = base.shift(step)
    val surfaceRaised = base.shift(step * 2)

    val accentSeed = if (dominant != null) vividAccent(dominant) else ThemePreset.SYNXIO.accentDark
    val accent = ensureContrast(accentSeed, base)
    val glow = accent.copy(alpha = GLOW_ALPHA)

    val onBase = ensureContrastAlpha(Color.White.copy(alpha = ON_BASE_ALPHA), base)
    val onBaseMuted = ensureContrastAlpha(Color.White.copy(alpha = ON_BASE_MUTED_ALPHA), base)

    return NuitSonorePalette(
        base = base,
        surface = surface,
        surfaceRaised = surfaceRaised,
        accent = accent,
        glow = glow,
        onBase = onBase,
        onBaseMuted = onBaseMuted,
    )
}

/**
 * Remonte saturation et luminosité de la dominante pour en faire un accent exploitable
 * (spec §1.1 : « dominante, saturation et luminosité remontées »), avant vérification du
 * contraste. Une pochette déjà vive n'est pas dénaturée ; une pochette terne ou trop sombre
 * est recentrée dans une plage lisible.
 */
private fun vividAccent(color: Color): Color {
    val (hue, saturation, lightness) = toHsl(color)
    val boostedSaturation = max(saturation, ACCENT_MIN_SATURATION)
    val boostedLightness = lightness.coerceIn(ACCENT_MIN_LIGHTNESS, ACCENT_MAX_LIGHTNESS)
    return fromHsl(hue, boostedSaturation, boostedLightness)
}

/**
 * Remonte [color] (opaque) en luminosité, par pas de 2 % de luminosité HSL, jusqu'à
 * atteindre [minRatio] de contraste sur [background]. Garde-fou de la spec §1.2.
 */
private fun ensureContrast(color: Color, background: Color, minRatio: Float = MIN_CONTRAST): Color {
    if (contrastRatio(color, background) >= minRatio) return color
    val (hue, saturation, _) = toHsl(color)
    var lightness = toHsl(color).third
    var result = color
    var steps = 0
    while (contrastRatio(result, background) < minRatio && lightness < 1f && steps < 100) {
        lightness = (lightness + 0.02f).coerceAtMost(1f)
        result = fromHsl(hue, saturation, lightness)
        steps++
    }
    return result
}

/**
 * Variante d'[ensureContrast] pour une couleur translucide (`onBase`, `onBaseMuted`) : on
 * remonte l'opacité plutôt que la luminosité, le contraste étant évalué sur la couleur
 * composée avec [background].
 */
private fun ensureContrastAlpha(color: Color, background: Color, minRatio: Float = MIN_CONTRAST): Color {
    var alpha = color.alpha
    var result = color
    var steps = 0
    while (contrastRatio(result.compositeOver(background), background) < minRatio && alpha < 1f && steps < 100) {
        alpha = (alpha + 0.02f).coerceAtMost(1f)
        result = color.copy(alpha = alpha)
        steps++
    }
    return result
}

/** Ratio de contraste WCAG entre deux couleurs opaques : `(L1 + 0.05) / (L2 + 0.05)`. */
private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance() + 0.05f
    val l2 = b.luminance() + 0.05f
    return max(l1, l2) / min(l1, l2)
}

/** Aplatit une couleur translucide sur [background], pour en calculer le contraste réel. */
private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1f - a),
        green = green * a + background.green * (1f - a),
        blue = blue * a + background.blue * (1f - a),
        alpha = 1f,
    )
}

/**
 * Conversion RGB -> HSL en Kotlin pur (teinte/saturation/luminosité, chacune dans [0, 1]).
 * Évite toute dépendance à `android.graphics.Color`, injoignable en test unitaire JVM.
 */
private fun toHsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxChannel = max(r, max(g, b))
    val minChannel = min(r, min(g, b))
    val lightness = (maxChannel + minChannel) / 2f

    if (maxChannel == minChannel) return Triple(0f, 0f, lightness)

    val delta = maxChannel - minChannel
    val saturation = if (lightness > 0.5f) {
        delta / (2f - maxChannel - minChannel)
    } else {
        delta / (maxChannel + minChannel)
    }
    val hue = when (maxChannel) {
        r -> ((g - b) / delta + (if (g < b) 6f else 0f))
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } / 6f

    return Triple(hue, saturation, lightness)
}

/** Conversion HSL -> RGB en Kotlin pur, symétrique de [toHsl]. */
private fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
    if (saturation == 0f) return Color(lightness, lightness, lightness)

    fun hueToChannel(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    val q = if (lightness < 0.5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
    val p = 2f * lightness - q
    val r = hueToChannel(p, q, hue + 1f / 3f)
    val g = hueToChannel(p, q, hue)
    val b = hueToChannel(p, q, hue - 1f / 3f)
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}
