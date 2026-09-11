package fr.synxio.player.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Dessine le fond d'écran d'accueil du widget Synxio en pur style « Liquid Glass ».
 *
 * Combine réfraction optique multicouche, halo chromatique diffus extrait de la pochette,
 * liseré prismatique 3D et double vague fluide harmonique réagissant au spectre audio réel.
 */
@Singleton
class WidgetArtRenderer @Inject constructor() {

    /**
     * @param artwork pochette du morceau, ou `null`
     * @param bands énergies de bande du morceau, ou `null` si non analysé
     * @param dark palette sombre demandée par le thème de l'application
     * @param showWave afficher les vagues liquides spectrales
     * @param glassOpacity intensité d'opacité du verre (0.2f à 1.0f)
     * @param waveTint style de couleur néon du fluide ("ACCENT", "NEON_CYAN", "AMETHYST", "EMERALD")
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        artwork: Bitmap?,
        bands: FloatArray?,
        dark: Boolean,
        showWave: Boolean = true,
        glassOpacity: Float = 0.75f,
        waveTint: String = "ACCENT",
    ): Bitmap? = runCatching {
        val width = widthPx.coerceIn(MIN_SIDE, MAX_WIDTH)
        val height = heightPx.coerceIn(MIN_SIDE, MAX_HEIGHT)
        val canvas: Canvas
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            .also { canvas = Canvas(it) }

        val opacity = glassOpacity.coerceIn(0.2f, 1.0f)

        drawBackdrop(canvas, width, height, artwork, dark, opacity)
        drawLiquidGlass(canvas, width, height, dark, opacity)
        if (showWave) {
            drawLiquidWaves(canvas, width, height, bands, dark, waveTint)
        }

        output
    }.getOrNull()

    /**
     * Pochette étirée et floutée avec halo chromatique d'ambiance projeté.
     */
    private fun drawBackdrop(
        canvas: Canvas,
        width: Int,
        height: Int,
        artwork: Bitmap?,
        dark: Boolean,
        opacity: Float,
    ) {
        if (artwork == null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    if (dark) 0xFF181524.toInt() else 0xFFECE6F8.toInt(),
                    if (dark) 0xFF0A0910.toInt() else 0xFFF9F7FD.toInt(),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return
        }

        // Flou progressif bilinéaire
        val tiny = Bitmap.createScaledBitmap(artwork, BLUR_SIDE, BLUR_SIDE, true)
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(tiny, Rect(0, 0, BLUR_SIDE, BLUR_SIDE), Rect(0, 0, width, height), smooth)
        tiny.recycle()

        // Halo d'ambiance chromatique projeté depuis la pochette
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.25f, height * 0.5f, width * 0.6f,
                ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 28 else 60),
                0,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

        // Voile de verre teinté adaptatif
        val scrimAlpha = if (dark) {
            ((0xB8 * opacity).toInt()).coerceIn(120, 230)
        } else {
            ((0x65 * opacity).toInt()).coerceIn(60, 160)
        }
        val scrimColor = if (dark) {
            ColorUtils.setAlphaComponent(0xFF000000.toInt(), scrimAlpha)
        } else {
            ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), scrimAlpha)
        }
        canvas.drawColor(scrimColor)
    }

    /**
     * Panneau Liquid Glass : brillance caustique, réflexion diagonale et biseau 3D.
     */
    private fun drawLiquidGlass(
        canvas: Canvas,
        width: Int,
        height: Int,
        dark: Boolean,
        opacity: Float,
    ) {
        val cornerRadius = CORNER_FRACTION * height

        // 1. Reflet supérieur caustique (éclat de verre courbé)
        val topSheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.65f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 55 else 140),
                    ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 18 else 45),
                    ColorUtils.setAlphaComponent(Color.WHITE, 0),
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height * 0.7f),
            cornerRadius,
            cornerRadius,
            topSheen
        )

        // 2. Faisceau de réfraction liquide diagonal
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                -width * 0.1f, -height * 0.2f,
                width * 0.8f, height * 0.9f,
                ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 25 else 50),
                0,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            cornerRadius,
            cornerRadius,
            beamPaint
        )

        // 3. Capsule en verre dépoli intérieure pour les commandes
        val controlsPlateRect = RectF(
            width * 0.45f,
            height * 0.52f,
            width * 0.96f,
            height * 0.92f
        )
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ColorUtils.setAlphaComponent(
                if (dark) Color.WHITE else 0xFF4A3B69.toInt(),
                if (dark) 16 else 14
            )
        }
        val plateStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 32 else 70)
        }
        canvas.drawRoundRect(controlsPlateRect, height * 0.2f, height * 0.2f, platePaint)
        canvas.drawRoundRect(controlsPlateRect, height * 0.2f, height * 0.2f, plateStroke)

        // 4. Liseré prismatique 3D (biseau lumineux supérieur, ombre inférieure)
        val strokeWidth = max(1.2f, width / 360f)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 120 else 220),
                    ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 40 else 90),
                    ColorUtils.setAlphaComponent(if (dark) 0xFF8B7CF0.toInt() else 0xFF6C5CE7.toInt(), 50),
                    ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 20 else 50),
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val inset = strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            cornerRadius,
            cornerRadius,
            edge,
        )
    }

    /**
     * Vagues de fluide harmonique liquide à double couche réactives au spectre audio.
     */
    private fun drawLiquidWaves(
        canvas: Canvas,
        width: Int,
        height: Int,
        bands: FloatArray?,
        dark: Boolean,
        waveTint: String,
    ) {
        val profile = normalise(bands)
        val tintColor = resolveWaveTint(waveTint, dark)

        // 1. Première couche : vague fluide d'arrière-plan (douce et enveloppante)
        val bgBaseline = height * 0.95f
        val bgAmplitude = height * 0.22f
        val bgStep = width.toFloat() / (profile.size - 1)
        val bgPath = Path().apply {
            val startY = bgBaseline - (profile[0] * 0.6f + 0.2f) * bgAmplitude
            moveTo(0f, startY)
        }
        for (i in 0 until profile.size - 1) {
            val currentVal = profile[i] * 0.6f + 0.2f
            val nextVal = profile[i + 1] * 0.6f + 0.2f
            val x1 = i * bgStep
            val y1 = bgBaseline - currentVal * bgAmplitude
            val x2 = (i + 1) * bgStep
            val y2 = bgBaseline - nextVal * bgAmplitude
            val midX = (x1 + x2) / 2f
            bgPath.cubicTo(midX, y1, midX, y2, x2, y2)
        }
        val bgFill = Path(bgPath).apply {
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        canvas.drawPath(
            bgFill,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, bgBaseline - bgAmplitude, 0f, height.toFloat(),
                    ColorUtils.setAlphaComponent(tintColor, if (dark) 45 else 35),
                    0,
                    Shader.TileMode.CLAMP
                )
            }
        )

        // 2. Seconde couche : vague fluide de premier plan (précise, luminescente avec crête néon)
        val fgBaseline = height * 0.93f
        val fgAmplitude = height * 0.17f
        val fgStep = width.toFloat() / (profile.size - 1)

        val fgPath = Path().apply {
            moveTo(0f, fgBaseline - profile[0] * fgAmplitude)
        }
        for (i in 0 until profile.size - 1) {
            val x1 = i * fgStep
            val y1 = fgBaseline - profile[i] * fgAmplitude
            val x2 = (i + 1) * fgStep
            val y2 = fgBaseline - profile[i + 1] * fgAmplitude
            val midX = (x1 + x2) / 2f
            fgPath.cubicTo(midX, y1, midX, y2, x2, y2)
        }

        val fgFill = Path(fgPath).apply {
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }

        // Remplissage dégradé lumineux
        canvas.drawPath(
            fgFill,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, fgBaseline - fgAmplitude, 0f, height.toFloat(),
                    ColorUtils.setAlphaComponent(tintColor, if (dark) 100 else 75),
                    ColorUtils.setAlphaComponent(tintColor, 0),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        // Ligne de crête néon
        val crestStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2.2f, height / 55f)
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                ColorUtils.setAlphaComponent(tintColor, 120),
                ColorUtils.setAlphaComponent(tintColor, 240),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fgPath, crestStroke)

        // Gouttes de lumière / Perles liquides aux pics de fréquence
        val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ColorUtils.setAlphaComponent(Color.WHITE, if (dark) 230 else 255)
        }
        for (i in 1 until profile.size - 1) {
            if (profile[i] > 0.65f && profile[i] >= profile[i - 1] && profile[i] >= profile[i + 1]) {
                val beadX = i * fgStep
                val beadY = fgBaseline - profile[i] * fgAmplitude
                canvas.drawCircle(beadX, beadY, max(2f, height / 70f), beadPaint)
            }
        }
    }

    private fun resolveWaveTint(tintName: String, dark: Boolean): Int = when (tintName) {
        "NEON_CYAN" -> if (dark) 0xFF00E5FF.toInt() else 0xFF00A8B5.toInt()
        "AMETHYST" -> if (dark) 0xFFD946EF.toInt() else 0xFFA21CAF.toInt()
        "EMERALD" -> if (dark) 0xFF10B981.toInt() else 0xFF059669.toInt()
        else -> if (dark) 0xFF8B7CF0.toInt() else 0xFF6C5CE7.toInt()
    }

    private fun normalise(bands: FloatArray?): FloatArray {
        val source = bands?.takeIf { it.size >= BAND_COUNT * 2 }
            ?.let { vector -> FloatArray(BAND_COUNT) { vector[it * 2] } }
            ?: return NEUTRAL_PROFILE

        val low = source.min()
        val high = source.max()
        val span = high - low
        if (span < 1e-4f) return NEUTRAL_PROFILE
        return FloatArray(source.size) { ((source[it] - low) / span).coerceIn(0f, 1f) }
    }

    private companion object {
        const val BAND_COUNT = 10
        const val BLUR_SIDE = 14
        const val MIN_SIDE = 64
        const val MAX_WIDTH = 540
        const val MAX_HEIGHT = 280
        const val CORNER_FRACTION = 0.18f
        val NEUTRAL_PROFILE =
            floatArrayOf(0.25f, 0.45f, 0.7f, 0.55f, 0.8f, 0.6f, 0.85f, 0.5f, 0.65f, 0.3f)
    }
}
