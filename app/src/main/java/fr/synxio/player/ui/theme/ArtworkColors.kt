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
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
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

            val drawable = context.imageLoader.execute(request).drawable ?: return@withContext null
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

/** Noir ou blanc, selon ce qui contraste le mieux avec [this]. */
fun Color.contrastingOn(): Color = if (luminance() > 0.45f) Color.Black else Color.White

internal fun Color.argb(): Int = toArgb()
