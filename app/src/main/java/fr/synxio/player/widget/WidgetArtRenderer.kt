package fr.synxio.player.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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
 * Prépare les images des widgets : pochette réduite, fond flouté et accent de couleur.
 *
 * Tout passe par un `RemoteViews`, dont la transaction Binder plafonne autour du
 * mégaoctet : une pochette 1000×1000 décodée en ARGB pèse quatre mégaoctets et ferait
 * disparaître le widget sans erreur. Les images sont donc réduites ici, une seule fois
 * par morceau, et écrites en fichiers que chaque widget relit.
 *
 * Le « verre » du widget est un vrai flou de la pochette : réduite à quelques pixels
 * puis agrandie avec filtrage, elle donne un champ de couleurs doux sur lequel on pose
 * un voile pour garantir la lisibilité du texte.
 */
@Singleton
class WidgetArtRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Prepared(val artPath: String, val backdropPath: String, val accent: Int)

    private val dir: File get() = File(context.filesDir, "widget").apply { mkdirs() }

    /**
     * Prépare les images pour [artworkUri], ou rend un résultat vide sans pochette.
     * [key] identifie le morceau et le thème : on ne refait rien si rien n'a changé.
     */
    fun prepare(artworkUri: Uri?, key: String, dark: Boolean): Prepared {
        val safeKey = key.hashCode().toUInt().toString(16) + if (dark) "d" else "l"
        val art = File(dir, "art_$safeKey.jpg")
        val backdrop = File(dir, "bg_$safeKey.jpg")
        val accentFile = File(dir, "accent_$safeKey")
        if (art.exists() && backdrop.exists() && accentFile.exists()) {
            val accent = accentFile.readText().toIntOrNull() ?: 0
            return Prepared(art.path, backdrop.path, accent)
        }

        // Sans pochette : le même dégradé que dans l'application, plutôt qu'un carré gris.
        val source = artworkUri?.let { decode(it, ART_PIXELS) }
            ?: placeholder(artworkUri ?: key)

        return runCatching {
            val square = centerCrop(source, ART_PIXELS)
            art.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }

            val blurred = renderBackdrop(square, dark)
            backdrop.outputStream().use { blurred.compress(Bitmap.CompressFormat.JPEG, 92, it) }

            val accent = accentOf(square, dark)
            accentFile.writeText(accent.toString())

            if (square !== source) square.recycle()
            source.recycle()
            blurred.recycle()
            cleanup(keep = setOf(art.name, backdrop.name, accentFile.name))
            Prepared(art.path, backdrop.path, accent)
        }.getOrElse {
            android.util.Log.w(TAG, "Pochette du widget non préparée", it)
            Prepared("", "", 0)
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

    /**
     * Flou par réductions successives : chaque division par deux moyenne quatre pixels,
     * ce qui évite le crénelage d'une réduction directe vers une toute petite taille.
     */
    private fun renderBackdrop(art: Bitmap, dark: Boolean): Bitmap {
        var tiny = art
        while (tiny.width > BLUR_SIDE) {
            val next = Bitmap.createScaledBitmap(tiny, tiny.width / 2, tiny.height / 2, true)
            if (tiny !== art) tiny.recycle()
            tiny = next
        }
        val out = Bitmap.createBitmap(BACKDROP_W, BACKDROP_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // Agrandi deux fois : un seul agrandissement laisse voir la grille des pixels.
        val mid = Bitmap.createScaledBitmap(tiny, BACKDROP_W / 4, BACKDROP_H / 4, true)
        canvas.drawBitmap(mid, null, android.graphics.Rect(0, 0, BACKDROP_W, BACKDROP_H), paint)
        mid.recycle()
        if (tiny !== art) tiny.recycle()

        // Voile de lisibilité : plus dense en bas, où se trouvent les commandes.
        val top = if (dark) ColorUtils.setAlphaComponent(0xFF06060A.toInt(), 110)
        else ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 150)
        val bottom = if (dark) ColorUtils.setAlphaComponent(0xFF06060A.toInt(), 185)
        else ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 205)
        val scrim = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, BACKDROP_H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, BACKDROP_W.toFloat(), BACKDROP_H.toFloat(), scrim)
        return out
    }

    /**
     * Accent tiré de la pochette, assez contrasté pour un bouton sur le fond du widget.
     * 0 quand la pochette est trop terne : le widget garde alors l'accent Synxio.
     */
    private fun accentOf(art: Bitmap, dark: Boolean): Int {
        val palette = Palette.from(art).maximumColorCount(16).generate()
        val candidate = if (dark) {
            palette.lightVibrantSwatch ?: palette.vibrantSwatch ?: palette.lightMutedSwatch
        } else {
            palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.darkMutedSwatch
        } ?: return 0
        val hsl = candidate.hsl.copyOf()
        if (hsl[1] < 0.18f) return 0
        // Clair sur fond sombre, sombre sur fond clair : le bouton doit se détacher.
        hsl[2] = if (dark) hsl[2].coerceIn(0.62f, 0.78f) else hsl[2].coerceIn(0.32f, 0.45f)
        hsl[1] = hsl[1].coerceAtLeast(0.45f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun cleanup(keep: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    private companion object {
        const val TAG = "WidgetArt"
        const val ART_PIXELS = 300
        const val BLUR_SIDE = 10
        const val BACKDROP_W = 160
        const val BACKDROP_H = 160
    }
}
