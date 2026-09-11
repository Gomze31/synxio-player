package fr.synxio.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10

@Composable
fun VisualizerView(
    fftBytes: ByteArray,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    // Calcul de l'amplitude (magnitude) de la FFT.
    // L'API Visualizer renvoie : [DC, Nyquist, Re(f1), Im(f1), Re(f2), Im(f2), ...]
    val magnitudes = remember(fftBytes, barCount) {
        val levels = FloatArray(barCount)
        if (fftBytes.size > 2) {
            val maxIndex = (fftBytes.size / 2) - 1
            val itemsPerBar = maxIndex / barCount
            
            for (i in 0 until barCount) {
                var maxMag = 0f
                for (j in 0 until itemsPerBar) {
                    val index = (i * itemsPerBar + j) * 2 + 2
                    if (index + 1 < fftBytes.size) {
                        val real = fftBytes[index].toFloat()
                        val imaginary = fftBytes[index + 1].toFloat()
                        val mag = hypot(real, imaginary)
                        if (mag > maxMag) maxMag = mag
                    }
                }
                
                // Conversion en décibels pour un affichage plus naturel
                // 128.0 est la valeur maximale théorique d'une composante byte
                val db = 10 * log10(maxMag.coerceAtLeast(1f) / 128f)
                // Normalisation : -50 dB à 0 dB devient 0.0 à 1.0
                val normalized = ((db + 50) / 50).coerceIn(0f, 1f)
                levels[i] = normalized
            }
        }
        levels
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        val barWidth = canvasWidth / (barCount * 1.5f)
        val spacing = (canvasWidth - (barWidth * barCount)) / (barCount - 1)
        
        val brush = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor)
        )

        for (i in 0 until barCount) {
            val magnitude = magnitudes.getOrElse(i) { 0f }
            val barHeight = magnitude * canvasHeight
            val minHeight = 4.dp.toPx() // Hauteur minimale d'une barre au repos
            val finalHeight = maxOf(barHeight, minHeight)
            
            val x = i * (barWidth + spacing)
            val y = canvasHeight - finalHeight
            
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, finalHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
