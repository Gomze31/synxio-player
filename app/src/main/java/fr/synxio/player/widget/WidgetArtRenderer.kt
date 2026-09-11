package fr.synxio.player.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Dessine le fond du widget : pochette floutée, voile de verre et onde spectrale.
 *
 * ## Pourquoi une image et pas des composants
 *
 * Glance se résout en `RemoteViews` : ni shader, ni flou, ni dessin libre. Tout effet
 * visuel doit donc être produit côté application, sous forme de bitmap, puis affiché
 * comme une simple image. C'est la seule échappatoire, et elle est totale.
 *
 * ## L'onde n'est pas décorative
 *
 * Sa silhouette vient des énergies de bande calculées pour la recherche de morceaux
 * similaires : elle dessine le spectre réel du morceau affiché. Un titre sourd donne une
 * onde basse et ramassée, un titre brillant une onde étalée vers la droite. Sans
 * empreinte disponible, on retombe sur un profil neutre plutôt que d'afficher une ligne
 * plate qui ferait croire à un bug.
 */
@Singleton
class WidgetArtRenderer @Inject constructor() {

    /**
     * @param artwork pochette du morceau, ou `null`
     * @param bands énergies de bande du morceau, ou `null` si non analysé
     * @param dark palette sombre demandée par le thème de l'application
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        artwork: Bitmap?,
        bands: FloatArray?,
        dark: Boolean,
    ): Bitmap? = runCatching {
        val width = widthPx.coerceIn(MIN_SIDE, MAX_WIDTH)
        val height = heightPx.coerceIn(MIN_SIDE, MAX_HEIGHT)
        val canvas: Canvas
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            .also { canvas = Canvas(it) }

        drawBackdrop(canvas, width, height, artwork, dark)
        drawGlass(canvas, width, height, dark)
        drawWave(canvas, width, height, bands, dark)

        output
    }.getOrNull()

    /**
     * Pochette étirée puis floutée, ou dégradé uni à défaut.
     *
     * Le flou est obtenu en réduisant l'image à quelques dizaines de pixels puis en la
     * réétirant avec filtrage bilinéaire. C'est grossier comparé à un vrai flou
     * gaussien, mais sur un fond destiné à passer sous du texte la différence est
     * invisible — et cela évite RenderScript, déprécié, comme RenderEffect, réservé aux
     * vues et indisponible ici.
     */
    private fun drawBackdrop(
        canvas: Canvas,
        width: Int,
        height: Int,
        artwork: Bitmap?,
        dark: Boolean,
    ) {
        if (artwork == null) {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    if (dark) 0xFF241F33.toInt() else 0xFFE8E2F5.toInt(),
                    if (dark) 0xFF0E0C14.toInt() else 0xFFF8F5FF.toInt(),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return
        }

        val tiny = Bitmap.createScaledBitmap(artwork, BLUR_SIDE, BLUR_SIDE, true)
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(tiny, Rect(0, 0, BLUR_SIDE, BLUR_SIDE), Rect(0, 0, width, height), smooth)
        tiny.recycle()

        // Voile sombre : sans lui, une pochette claire rendrait le texte illisible, et
        // c'est le cas le plus fréquent dans une bibliothèque de pop.
        canvas.drawColor(if (dark) SCRIM_DARK else SCRIM_LIGHT)
    }

    /** Panneau de verre : léger éclaircissement et liseré supérieur. */
    private fun drawGlass(canvas: Canvas, width: Int, height: Int, dark: Boolean) {
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 34 else 120),
                ColorUtils.setAlphaComponent(Color.WHITE, 0),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.6f, sheen)

        // Le liseré est ce qui fait lire la surface comme du verre plutôt que comme un
        // simple aplat translucide.
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, width / 400f)
            color = ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 46 else 150)
        }
        val inset = edge.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            CORNER_FRACTION * height,
            CORNER_FRACTION * height,
            edge,
        )
    }

    /**
     * Onde spectrale, tracée en courbes lissées.
     *
     * Les points de contrôle sont placés à mi-chemin entre deux sommets : c'est ce qui
     * donne une ligne continue et « liquide » là où un tracé droit produirait une silhouette
     * en dents de scie.
     */
    private fun drawWave(
        canvas: Canvas,
        width: Int,
        height: Int,
        bands: FloatArray?,
        dark: Boolean,
    ) {
        val profile = normalise(bands)
        // L'onde reste confinée au bas du widget. Tracée plus haut, elle passait derrière
        // les boutons de lecture et les rendait illisibles : un fond ne doit pas disputer
        // la lisibilité à ce qu'il porte.
        val baseline = height * 0.94f
        val amplitude = height * 0.18f
        val step = width.toFloat() / (profile.size - 1)

        val path = Path().apply { moveTo(0f, baseline - profile[0] * amplitude) }
        for (i in 0 until profile.size - 1) {
            val x1 = i * step
            val y1 = baseline - profile[i] * amplitude
            val x2 = (i + 1) * step
            val y2 = baseline - profile[i + 1] * amplitude
            val midX = (x1 + x2) / 2f
            path.cubicTo(midX, y1, midX, y2, x2, y2)
        }

        val fill = Path(path).apply {
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }

        val tint = if (dark) 0xFF8B7CF0.toInt() else 0xFF6C5CE7.toInt()
        canvas.drawPath(
            fill,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, baseline - amplitude, 0f, height.toFloat(),
                    ColorUtils.setAlphaComponent(tint, 90),
                    ColorUtils.setAlphaComponent(tint, 0),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(2f, height / 60f)
                strokeCap = Paint.Cap.ROUND
                color = ColorUtils.setAlphaComponent(tint, 150)
            },
        )
    }

    /**
     * Ramène les énergies de bande dans [0, 1], relativement à ce morceau.
     *
     * Une normalisation absolue produirait des ondes quasi identiques d'un titre à
     * l'autre : ce qui distingue visuellement deux morceaux, c'est le *relief* entre
     * leurs bandes, pas leur niveau global — que la normalisation du volume gomme déjà.
     */
    private fun normalise(bands: FloatArray?): FloatArray {
        val source = bands?.takeIf { it.size >= BAND_COUNT * 2 }
            ?.let { vector -> FloatArray(BAND_COUNT) { vector[it * 2] } }
            ?: return NEUTRAL_PROFILE

        val low = source.min()
        val high = source.max()
        val span = high - low
        // Morceau au spectre parfaitement plat : on retombe sur le profil neutre plutôt
        // que de diviser par zéro.
        if (span < 1e-4f) return NEUTRAL_PROFILE
        return FloatArray(source.size) { ((source[it] - low) / span).coerceIn(0f, 1f) }
    }

    private companion object {
        const val BAND_COUNT = 10
        const val BLUR_SIDE = 12
        const val MIN_SIDE = 64
        /** Un `RemoteViews` plafonne autour du mégaoctet : on reste très en deçà. */
        const val MAX_WIDTH = 520
        const val MAX_HEIGHT = 260
        const val CORNER_FRACTION = 0.16f
        const val SCRIM_DARK = 0xB3000000.toInt()
        const val SCRIM_LIGHT = 0x59FFFFFF
        /** Silhouette douce utilisée tant qu'un morceau n'a pas été analysé. */
        val NEUTRAL_PROFILE =
            floatArrayOf(0.25f, 0.45f, 0.7f, 0.55f, 0.8f, 0.6f, 0.85f, 0.5f, 0.65f, 0.3f)
    }
}
