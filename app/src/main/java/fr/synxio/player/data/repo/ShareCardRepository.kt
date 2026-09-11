package fr.synxio.player.data.repo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Génère une carte « en écoute » partageable.
 *
 * Dessinée au Canvas plutôt que capturée depuis Compose : une capture dépendrait de la
 * taille de l'écran et du thème courant, alors qu'on veut une image au format fixe,
 * lisible partout, et produite même si le lecteur n'est pas affiché.
 */
@Singleton
class ShareCardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Les échecs sont tracés, pas avalés.
     *
     * Une carte qui ne se génère pas se manifeste par « rien ne se passe » à l'écran :
     * sans trace dans le journal, la cause est introuvable.
     */
    suspend fun createShareIntent(song: Song): Intent? = withContext(Dispatchers.IO) {
        try {
            val bitmap = render(song)
            val uri = persist(bitmap)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.displayArtist}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Génération de la carte de partage impossible", error)
            null
        }
    }

    private fun render(song: Song): Bitmap {
        val artwork = loadArtwork(song)
        val accent = artwork?.let { dominantColor(it) } ?: FALLBACK_ACCENT

        val card = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)

        drawBackground(canvas, accent)
        val artBottom = drawArtwork(canvas, artwork, accent)
        drawText(canvas, song, artBottom)

        return card
    }

    /**
     * Fond dégradé dérivé de la pochette, assombri.
     *
     * L'accent brut est trop lumineux pour servir de fond : le texte blanc par-dessus
     * deviendrait illisible sur une pochette claire. On force donc la luminosité dans
     * une plage sombre avant de construire le dégradé.
     */
    private fun drawBackground(canvas: Canvas, accent: Int) {
        val top = darken(accent, 0.26f)
        val bottom = darken(accent, 0.08f)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                top, bottom,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    /** Dessine la pochette carrée arrondie et retourne l'ordonnée de son bas. */
    private fun drawArtwork(canvas: Canvas, artwork: Bitmap?, accent: Int): Float {
        val size = WIDTH - 2 * MARGIN
        val rect = RectF(
            MARGIN.toFloat(),
            MARGIN.toFloat(),
            (MARGIN + size).toFloat(),
            (MARGIN + size).toFloat(),
        )

        if (artwork != null) {
            // On découpe un carré centré dans la source : une pochette non carrée serait
            // sinon étirée, ce qui se voit immédiatement sur un visage.
            val side = minOf(artwork.width, artwork.height)
            val src = Rect(
                (artwork.width - side) / 2,
                (artwork.height - side) / 2,
                (artwork.width + side) / 2,
                (artwork.height + side) / 2,
            )
            val rounded = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            Canvas(rounded).apply {
                val clip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                drawRoundRect(
                    RectF(0f, 0f, size.toFloat(), size.toFloat()),
                    RADIUS, RADIUS, clip,
                )
                drawBitmap(
                    artwork,
                    src,
                    Rect(0, 0, size, size),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = android.graphics.PorterDuffXfermode(
                            android.graphics.PorterDuff.Mode.SRC_IN
                        )
                    },
                )
            }
            canvas.drawBitmap(rounded, MARGIN.toFloat(), MARGIN.toFloat(), null)
        } else {
            canvas.drawRoundRect(
                rect, RADIUS, RADIUS,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darken(accent, 0.4f) },
            )
        }

        return rect.bottom
    }

    private fun drawText(canvas: Canvas, song: Song, artBottom: Float) {
        var y = artBottom + 88f

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 66f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(ellipsize(song.title, title), MARGIN.toFloat(), y, title)

        y += 62f
        val artist = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            textSize = 46f
        }
        canvas.drawText(ellipsize(song.displayArtist, artist), MARGIN.toFloat(), y, artist)

        y += 52f
        val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255)
            textSize = 36f
        }
        val details = buildString {
            append(song.displayAlbum)
            if (song.year > 0) append(" · ${song.year}")
            append(" · ${song.durationMs.asDuration()}")
        }
        canvas.drawText(ellipsize(details, meta), MARGIN.toFloat(), y, meta)

        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("SYNXIO", MARGIN.toFloat(), HEIGHT - MARGIN.toFloat(), brand)
    }

    /** Tronque au mot près pour tenir dans la largeur utile. */
    private fun ellipsize(text: String, paint: Paint): String {
        val available = (WIDTH - 2 * MARGIN).toFloat()
        if (paint.measureText(text) <= available) return text
        var cut = text
        while (cut.isNotEmpty() && paint.measureText("$cut…") > available) {
            cut = cut.dropLast(1)
        }
        return "${cut.trimEnd()}…"
    }

    private fun loadArtwork(song: Song): Bitmap? = runCatching {
        context.contentResolver.openInputStream(song.artworkUri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }.getOrNull()

    private fun dominantColor(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).clearFilters().generate()
        return palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: FALLBACK_ACCENT
    }

    /** Ramène une couleur à une luminosité donnée en conservant sa teinte. */
    private fun darken(color: Int, luminance: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = luminance
        hsl[1] = hsl[1].coerceAtMost(0.7f)
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Écrit la carte dans le cache partagé.
     *
     * Nom de fichier fixe : la carte n'a pas à survivre au partage, et l'écraser évite
     * d'accumuler des PNG dans le cache au fil des partages.
     */
    private fun persist(bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, "now-playing.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        const val TAG = "ShareCard"
        const val WIDTH = 1080
        const val HEIGHT = 1440
        const val MARGIN = 90
        const val RADIUS = 48f
        const val SHARE_DIR = "share"
        const val FALLBACK_ACCENT = 0xFF6C5CE7.toInt()
    }
}
