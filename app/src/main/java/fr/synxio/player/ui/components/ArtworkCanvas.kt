package fr.synxio.player.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.theme.LocalScenePulse
import fr.synxio.player.ui.theme.shaders.AGSL_MIN_SDK
import fr.synxio.player.ui.theme.shaders.LocalShaderSupport
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

const val CANVAS_TWO_PI = (2.0 * PI).toFloat()

/** Phase du canvas : un tour toutes les 8 s à vitesse 1, accumulée pour qu'un changement de vitesse ne saute pas. */
class CanvasPhase {
    private var lastNanos = Long.MIN_VALUE
    private var phase = 0f

    fun advance(nowNanos: Long, speed: Float): Float {
        if (lastNanos != Long.MIN_VALUE) {
            val dt = (nowNanos - lastNanos) / 1_000_000_000f
            phase = (phase + dt * speed * CANVAS_TWO_PI / LOOP_SECONDS) % CANVAS_TWO_PI
            if (phase < 0f) phase += CANVAS_TWO_PI
        }
        lastNanos = nowNanos
        return phase
    }

    private companion object { const val LOOP_SECONDS = 8f }
}

/**
 * Canvas génératif derrière la pochette : trois nappes qui ondulent aux couleurs de la
 * palette, vitesse `0.5 + rms`, contraste `0.6 + 0.4 × bass`. Rien n'est précalculé ni
 * stocké. AGSL sur API 33+, repli en dégradés animés sinon. Figé quand [animated] est faux.
 */
@Composable
fun ArtworkCanvas(animated: Boolean, modifier: Modifier = Modifier, shape: Shape) {
    val art = LocalArtworkColors.current
    val pulse = LocalScenePulse.current
    val support = LocalShaderSupport.current
    val colA = art.vibrant ?: art.seed ?: Color(0xFFC56BFF)
    val colB = art.darkMuted ?: art.dominant ?: Color(0xFF3A2A5A)
    val colC = art.dominant ?: colA
    val shader = remember(support) { support?.canvas() }
    val brush = remember(shader) { (shader as? Shader)?.let { ShaderBrush(it) } }
    val phase = remember { CanvasPhase() }
    // Horloge : une image par trame tant que le canvas est animé ; lue dans le dessin.
    val frame = remember { mutableIntStateOf(0) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        while (isActive) withFrameNanos { frame.intValue++ }
    }
    Canvas(modifier.clip(shape)) {
        frame.intValue
        val p = pulse?.value
        val speed = 0.5f + (p?.rms ?: 0f)
        val contrast = 0.6f + 0.4f * (p?.bass ?: 0f)
        val ph = phase.advance(System.nanoTime(), if (animated) speed else 0f)
        if (brush != null && shader != null && Build.VERSION.SDK_INT >= AGSL_MIN_SDK) {
            CanvasAgsl.apply(shader as android.graphics.RuntimeShader, size.width, size.height, ph, contrast, colA, colB, colC)
            drawRect(brush)
        } else {
            drawRect(colB)
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    listOf(colA.copy(alpha = 0.9f), Color.Transparent),
                    start = Offset(w * (0.5f + 0.5f * cos(ph)), 0f),
                    end = Offset(w * (0.5f - 0.5f * cos(ph)), h),
                )
            )
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, colC.copy(alpha = 0.7f * contrast)),
                    start = Offset(0f, h * (0.5f + 0.5f * sin(ph * 0.7f))),
                    end = Offset(w, h * (0.5f - 0.5f * sin(ph * 0.7f))),
                )
            )
        }
    }
}

@RequiresApi(AGSL_MIN_SDK)
private object CanvasAgsl {
    fun apply(s: android.graphics.RuntimeShader, w: Float, h: Float, phase: Float, contrast: Float, a: Color, b: Color, c: Color) {
        s.setFloatUniform("resolution", w, h)
        s.setFloatUniform("phase", phase)
        s.setFloatUniform("contrast", contrast)
        s.setFloatUniform("colA", a.red, a.green, a.blue, 1f)
        s.setFloatUniform("colB", b.red, b.green, b.blue, 1f)
        s.setFloatUniform("colC", c.red, c.green, c.blue, 1f)
    }
}
