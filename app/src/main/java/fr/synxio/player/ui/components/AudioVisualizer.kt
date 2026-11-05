package fr.synxio.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.synxio.player.playback.pulse.AudioPulse
import fr.synxio.player.ui.theme.LocalReduceMotion

/**
 * Spectre en barres tiré du pouls de la musique (`AudioPulse.bands`, 32 bandes 0..1).
 *
 * Animation ambiante par nature : avec « Réduire les animations », les barres restent
 * couchées.
 *
 * Le pouls arrive en [State] et n'est lu que dans la phase de dessin : un tick invalide le
 * dessin, jamais la composition.
 */
@Composable
fun AudioVisualizer(
    pulse: State<AudioPulse>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val reduceMotion = LocalReduceMotion.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val bands = if (reduceMotion) AudioPulse.SILENT.bands else pulse.value.bands
        val barCount = bands.size
        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val totalWidth = (barWidth + spacing) * barCount - spacing
        val startX = (size.width - totalWidth) / 2f
        val maxBarHeight = size.height * 0.8f
        val minHeight = 4.dp.toPx()
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        for (i in 0 until barCount) {
            val magnitude = bands[i]
            // Silence ou pause : rien plutôt qu'une rangée de points au ras de l'écran.
            if (magnitude < 0.02f) continue
            val height = (magnitude * maxBarHeight).coerceAtLeast(minHeight)
            drawRoundRect(
                color = barColor.copy(alpha = 0.6f + magnitude * 0.4f),
                topLeft = Offset(startX + i * (barWidth + spacing), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = cornerRadius,
            )
        }
    }
}
