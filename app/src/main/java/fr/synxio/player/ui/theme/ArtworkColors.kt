package fr.synxio.player.ui.theme

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Couleurs extraites d'une pochette, prêtes à teinter l'interface. */
data class ArtworkColors(
    val seed: Color? = null,
    val vibrant: Color? = null,
    val darkMuted: Color? = null,
    val dominant: Color? = null,
) {
    val isEmpty: Boolean get() = seed == null

    /** Dégradé de fond du plein écran « Lecture en cours ». */
    fun gradient(fallback: Color): List<Color> {
        val top = vibrant ?: seed ?: fallback
        val bottom = darkMuted ?: dominant ?: fallback
        return listOf(top.copy(alpha = 0.85f), bottom.copy(alpha = 0.6f), fallback)
    }
}

/**
 * Extrait les couleurs dominantes de [uri] via Palette.
 *
 * L'image est décodée en petit (128 px) et en logiciel : Palette ne sait pas lire
 * un bitmap matériel, et 128 px suffisent largement pour une moyenne de couleurs.
 */
@Composable
fun rememberArtworkColors(uri: Uri?): State<ArtworkColors> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(ArtworkColors()) }

    LaunchedEffect(uri) {
        if (uri == null) {
            state.value = ArtworkColors()
            return@LaunchedEffect
        }

        val colors = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(128)
                .scale(Scale.FILL)
                .allowHardware(false)
                .build()

            val drawable = context.imageLoader.execute(request).image?.asDrawable(context.resources)
                ?: return@withContext null
            val bitmap = runCatching {
                (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                    }
            }.getOrNull() ?: return@withContext null

            runCatching { Palette.from(bitmap).clearFilters().maximumColorCount(24).generate() }
                .getOrNull()
                ?.toArtworkColors()
        }

        state.value = colors ?: ArtworkColors()
    }

    return state
}

/** Saturation HSL du swatch, 0 = gris pur. */
private val Palette.Swatch.saturation: Float get() = hsl[1]

private fun Palette.toArtworkColors(): ArtworkColors {
    val darkMuted = darkMutedSwatch?.rgb?.let(::Color)
    val muted = mutedSwatch?.rgb?.let(::Color)
    val dominant = dominantSwatch?.rgb?.let(::Color)

    // Le seed doit être à la fois lisible (luminance moyenne) et réellement coloré.
    // Une pochette en noir et blanc renvoyait un gris qui repeignait toute l'app en
    // terne : dans ce cas on préfère garder l'accent Synxio (seed = null).
    val seedSwatch = listOfNotNull(
        vibrantSwatch,
        lightVibrantSwatch,
        darkVibrantSwatch,
        mutedSwatch,
        dominantSwatch,
    ).firstOrNull { swatch ->
        val color = Color(swatch.rgb)
        swatch.saturation >= MIN_SEED_SATURATION && color.luminance() in 0.05f..0.85f
    }

    val seed = seedSwatch?.rgb?.let(::Color)

    return ArtworkColors(
        seed = seed,
        vibrant = seed,
        darkMuted = darkMuted ?: muted,
        dominant = dominant,
    )
}

/**
 * En dessous de ce seuil, la couleur est trop proche du gris pour servir d'accent.
 * 0,25 laisse passer les pochettes pastel sans laisser passer le noir et blanc.
 */
private const val MIN_SEED_SATURATION = 0.25f

/** Éclaircit ou assombrit une couleur, utile pour dériver des variantes cohérentes. */
fun Color.shift(factor: Float): Color = if (factor >= 0f) {
    lerpColor(this, Color.White, factor)
} else {
    lerpColor(this, Color.Black, -factor)
}

fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}

/** Rapport de contraste WCAG 2.x entre deux couleurs opaques (1 à 21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * Noir ou blanc, selon ce qui contraste le mieux avec [this].
 *
 * Comparaison des deux rapports WCAG plutôt qu'un seuil de luminance : l'ancien seuil
 * à 0,45 posait du blanc sur le violet Synxio (3,5:1), sous le minimum de 4,5:1 pour
 * le texte des boutons.
 */
fun Color.contrastingOn(): Color =
    if (contrastRatio(this, Color.Black) >= contrastRatio(this, Color.White)) Color.Black else Color.White

/**
 * Ajuste [this] jusqu'à atteindre [minRatio] contre [background].
 *
 * Éclaircit sur fond sombre, assombrit sur fond clair, par petits pas pour garder la
 * teinte. Sert aux accents tirés des pochettes, imprévisibles par nature.
 */
fun Color.ensureContrast(background: Color, minRatio: Float = 4.5f): Color {
    if (contrastRatio(this, background) >= minRatio) return this
    val towards = if (background.luminance() < 0.5f) Color.White else Color.Black
    var fraction = 0f
    var candidate = this
    while (fraction < 1f && contrastRatio(candidate, background) < minRatio) {
        fraction += 0.04f
        candidate = lerpColor(this, towards, fraction)
    }
    return candidate
}

internal fun Color.argb(): Int = toArgb()
