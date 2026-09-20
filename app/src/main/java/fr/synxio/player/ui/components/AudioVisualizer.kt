package fr.synxio.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun AudioVisualizer(
    fft: ByteArray,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 32
) {
    // Transformer les données FFT complexes en magnitudes (0..1)
    val magnitudes = remember(fft) {
        if (fft.isEmpty() || fft.size < 2) return@remember FloatArray(barCount) { 0f }
        
        val result = FloatArray(barCount)
        val usableBands = minOf(fft.size / 2, 128) // On se limite aux fréquences pertinentes
        val pointsPerBar = usableBands / barCount
        
        if (pointsPerBar == 0) return@remember FloatArray(barCount) { 0f }

        for (i in 0 until barCount) {
            var sum = 0f
            for (j in 0 until pointsPerBar) {
                val index = (i * pointsPerBar + j) * 2
                if (index + 1 < fft.size) {
                    val real = fft[index].toFloat()
                    val imag = fft[index + 1].toFloat()
                    // Magnitude = sqrt(real^2 + imag^2)
                    val magnitude = kotlin.math.hypot(real, imag)
                    sum += magnitude
                }
            }
            // Normaliser approximativement (max typique autour de 100-200)
            val avg = sum / pointsPerBar
            result[i] = (avg / 128f).coerceIn(0f, 1f)
        }
        result
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val totalWidth = (barWidth + spacing) * barCount - spacing
        val startX = (size.width - totalWidth) / 2f
        val maxBarHeight = size.height * 0.8f

        for (i in 0 until barCount) {
            val magnitude = magnitudes[i]
            val height = (magnitude * maxBarHeight).coerceAtLeast(4f.dp.toPx()) // Hauteur minimale

            val x = startX + i * (barWidth + spacing)
            val y = size.height - height

            drawRoundRect(
                color = barColor.copy(alpha = 0.6f + (magnitude * 0.4f)),
                topLeft = Offset(x, y),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
