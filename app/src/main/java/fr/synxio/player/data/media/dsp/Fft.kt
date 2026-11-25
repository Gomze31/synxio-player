package fr.synxio.player.data.media.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** FFT réelle radix-2 itérative, sans allocation par appel au-delà des tampons fournis. Pur. */
class Fft(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "taille puissance de 2 attendue : $size" }
    }

    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    val window = FloatArray(size) { (0.5 - 0.5 * cos(2.0 * PI * it / (size - 1))).toFloat() }

    /** Magnitudes des `size / 2` premiers bins de [frame] (fenêtre de Hann appliquée). */
    fun magnitudes(frame: FloatArray, out: FloatArray = FloatArray(size / 2)): FloatArray {
        for (i in 0 until size) {
            re[i] = frame[i] * window[i]
            im[i] = 0f
        }
        // Renversement de bits.
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val step = size / len
            var i = 0
            while (i < size) {
                var k = 0
                for (m in 0 until len / 2) {
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val ur = re[i + m]; val ui = im[i + m]
                    val vr = re[i + m + len / 2] * wr - im[i + m + len / 2] * wi
                    val vi = re[i + m + len / 2] * wi + im[i + m + len / 2] * wr
                    re[i + m] = ur + vr; im[i + m] = ui + vi
                    re[i + m + len / 2] = ur - vr; im[i + m + len / 2] = ui - vi
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
        for (i in 0 until size / 2) out[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        return out
    }
}
