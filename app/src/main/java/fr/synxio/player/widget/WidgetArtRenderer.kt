package fr.synxio.player.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.util.PlaceholderPalette
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prépare les images des widgets : pochette arrondie, halo lumineux et accents.
 *
 * Tout passe par un `RemoteViews`, dont la transaction Binder plafonne autour du
 * mégaoctet : une pochette 1000×1000 décodée en ARGB pèse quatre mégaoctets et ferait
 * disparaître le widget sans erreur. Les images sont donc réduites ici, une seule fois
 * par pochette, et écrites en fichiers que chaque widget relit.
 *
 * Rien ici ne dépend du thème : le widget suit le mode sombre du système sans être
 * republié (couleurs jour/nuit résolues par le lanceur), il lui faut donc des images
 * valables sur l'encre comme sur le papier. Le halo est un calque transparent posé sur
 * le fond uni, et l'accent est calculé pour les deux scènes.
 */
@Singleton
class WidgetArtRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * [accentDark] se lit sur l'encre, [accentLight] sur le papier ; 0 quand la pochette
     * est trop terne pour donner un accent (le widget garde alors la braise Synxio).
     */
    data class Prepared(
        val artPath: String,
        val glowPath: String,
        val accentDark: Int,
        val accentLight: Int,
    )

    private val dir: File get() = File(context.filesDir, "widget").apply { mkdirs() }

    /**
     * Prépare les images pour [artworkUri], ou une pochette générée sans image.
     * [key] identifie la pochette : on ne refait rien si elle a déjà été préparée.
     */
    fun prepare(artworkUri: Uri?, key: String): Prepared {
        val safeKey = key.hashCode().toUInt().toString(16) + VERSION
        val art = File(dir, "art_$safeKey.png")
        val glow = File(dir, "glow_$safeKey.png")
        val accentFile = File(dir, "accent_$safeKey")
        if (art.exists() && glow.exists() && accentFile.exists()) {
            val (dark, light) = accentFile.readText().split(',').map { it.toIntOrNull() ?: 0 }
                .let { (it.getOrNull(0) ?: 0) to (it.getOrNull(1) ?: 0) }
            return Prepared(art.path, glow.path, dark, light)
        }

        // Sans pochette : le même dégradé que dans l'application, plutôt qu'un carré gris.
        val source = artworkUri?.let { decode(it, ART_PIXELS) }
            ?: placeholder(artworkUri ?: key)

        return runCatching {
            val square = centerCrop(source, ART_PIXELS)
            val palette = Palette.from(square).maximumColorCount(16).generate()

            // Coins arrondis cuits dans l'image : `cornerRadius` n'agit qu'à partir
            // d'Android 12, et la pochette s'affiche toujours carrée, donc sans déformation.
            val rounded = roundCorners(square)
            art.outputStream().use { rounded.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val halo = renderGlow(palette)
            glow.outputStream().use { halo.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val dark = accentFor(palette, INK, dark = true)
            val light = accentFor(palette, PAPER, dark = false)
            accentFile.writeText("$dark,$light")

            if (square !== source) square.recycle()
            source.recycle()
            rounded.recycle()
            halo.recycle()
            cleanup(keep = setOf(art.name, glow.name, accentFile.name))
            Prepared(art.path, glow.path, dark, light)
        }.getOrElse {
            android.util.Log.w(TAG, "Pochette du widget non préparée", it)
            Prepared("", "", 0, 0)
        }
    }

    /** Pochette générée : dégradé de [PlaceholderPalette] et une note discrète. */
    private fun placeholder(seed: Any): Bitmap {
        val (start, end) = PlaceholderPalette.pairFor(seed)
        val side = ART_PIXELS
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, side.toFloat(), side.toFloat(), start, end, Shader.TileMode.CLAMP)
        })
        val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 140)
            textSize = side * 0.42f
            textAlign = Paint.Align.CENTER
        }
        val y = side / 2f - (note.descent() + note.ascent()) / 2f
        canvas.drawText("♪", side / 2f, y, note)
        return bitmap
    }

    /** Décode en réduisant avant lecture : jamais plus de deux fois [target] de côté. */
    private fun decode(uri: Uri, target: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun centerCrop(src: Bitmap, side: Int): Bitmap {
        val min = minOf(src.width, src.height)
        val x = (src.width - min) / 2
        val y = (src.height - min) / 2
        val cropped = Bitmap.createBitmap(src, x, y, min, min)
        if (min <= side) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, side, side, true)
        if (cropped !== src) cropped.recycle()
        return scaled
    }

    /** Rayon proportionnel au côté : la pochette garde la même silhouette à toute taille. */
    private fun roundCorners(square: Bitmap): Bitmap {
        val side = square.width.toFloat()
        val out = Bitmap.createBitmap(square.width, square.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = side * ART_CORNER_FRACTION
        Canvas(out).drawRoundRect(RectF(0f, 0f, side, square.height.toFloat()), radius, radius, paint)
        return out
    }

    /**
     * Halo « Lumen » : la couleur de la pochette éclaire la scène depuis le coin de la
     * pochette, une seconde teinte répond en bas à droite. Calque transparent, d'une
     * intensité modérée pour rester lisible sous du texte clair (encre) comme foncé
     * (papier). Une pochette sans couleur reçoit la signature braise → magenta.
     */
    private fun renderGlow(palette: Palette): Bitmap {
        val primary = glowTone(
            palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch,
            fallback = BRAND_START,
        )
        val secondary = glowTone(
            palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch,
            fallback = BRAND_END,
        )
        val w = GLOW_W.toFloat()
        val h = GLOW_H.toFloat()
        val out = Bitmap.createBitmap(GLOW_W, GLOW_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        fun spot(cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(color, alpha),
                        ColorUtils.setAlphaComponent(color, alpha / 3),
                        ColorUtils.setAlphaComponent(color, 0),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        spot(w * 0.12f, h * 0.20f, w * 0.95f, primary, GLOW_PRIMARY_ALPHA)
        spot(w * 1.0f, h * 1.0f, w * 0.70f, secondary, GLOW_SECONDARY_ALPHA)
        return out
    }

    /** Teinte du halo : saturée et de luminosité moyenne, pour colorer sans éblouir. */
    private fun glowTone(swatch: Palette.Swatch?, fallback: Int): Int {
        val hsl = swatch?.hsl?.copyOf() ?: return fallback
        if (hsl[1] < MIN_SATURATION) return fallback
        hsl[1] = hsl[1].coerceAtLeast(0.5f)
        hsl[2] = 0.55f
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Accent tiré de la pochette, contrasté d'au moins 4,5:1 sur [background] : il sert
     * aux icônes actives (aléatoire, répétition, favori) et à la progression.
     * 0 quand la pochette est trop terne : le widget garde alors l'accent Synxio.
     */
    private fun accentFor(palette: Palette, background: Int, dark: Boolean): Int {
        val candidate = if (dark) {
            palette.lightVibrantSwatch ?: palette.vibrantSwatch ?: palette.lightMutedSwatch
        } else {
            palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.darkMutedSwatch
        } ?: return 0
        val hsl = candidate.hsl.copyOf()
        if (hsl[1] < MIN_SATURATION) return 0
        hsl[1] = hsl[1].coerceAtLeast(0.45f)
        hsl[2] = if (dark) hsl[2].coerceIn(0.62f, 0.78f) else hsl[2].coerceIn(0.30f, 0.45f)
        // Un jaune à 45 % de luminosité reste illisible sur papier : on assombrit (ou
        // éclaircit sur l'encre) jusqu'au contraste exigé.
        var color = ColorUtils.HSLToColor(hsl)
        var guard = 0
        while (ColorUtils.calculateContrast(color, background) < MIN_CONTRAST && guard++ < 20) {
            hsl[2] = (hsl[2] + if (dark) 0.03f else -0.03f).coerceIn(0f, 1f)
            color = ColorUtils.HSLToColor(hsl)
        }
        return color
    }

    private fun cleanup(keep: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    private companion object {
        const val TAG = "WidgetArt"
        /** Change quand le rendu change : les anciens fichiers ne sont pas réutilisés. */
        const val VERSION = "l1"
        const val ART_PIXELS = 300
        /** ≈ Radius.artworkMd rapporté à une pochette de widget moyenne. */
        const val ART_CORNER_FRACTION = 0.16f
        const val GLOW_W = 160
        const val GLOW_H = 160
        const val GLOW_PRIMARY_ALPHA = 92
        const val GLOW_SECONDARY_ALPHA = 56
        const val MIN_SATURATION = 0.18f
        const val MIN_CONTRAST = 4.5

        /** Scènes Lumen, mêmes valeurs que le préréglage de l'application. */
        const val INK = 0xFF0C0A10.toInt()
        const val PAPER = 0xFFFAF6F1.toInt()
        const val BRAND_START = 0xFFFF8A4C.toInt()
        const val BRAND_END = 0xFFFF4F8B.toInt()
    }
}
