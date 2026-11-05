package fr.synxio.player.playback.pulse

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Analyse d'une trame PCM mono : FFT réelle 1024 points, fenêtre de Hann, 32 bandes
 * logarithmiques de 20 Hz à 16 kHz, magnitudes en dB sur 60 dB puis normalisées,
 * lissage par bande (attaque rapide, relâchement lent), détection d'attaque.
 *
 * Pur et sans Android : appelé à 30 Hz par [PulseSource]. Les tableaux sont alloués
 * une fois ; chaque appel réutilise tout.
 */
class AudioAnalyser(
    sampleRate: Int = 44_100,
    private val frameSize: Int = 1024,
) {
    private var sampleRate = sampleRate
    private val window = FloatArray(frameSize) { 0.5f * (1f - cos(2.0 * PI * it / (frameSize - 1)).toFloat()) }
    private val re = FloatArray(frameSize)
    private val im = FloatArray(frameSize)
    private val magnitudes = FloatArray(frameSize / 2)
    private val bandEdges = IntArray(AudioPulse.BAND_COUNT + 1)
    private val raw = FloatArray(AudioPulse.BAND_COUNT)
    private val smoothed = FloatArray(AudioPulse.BAND_COUNT)
    private var previousRms = 0f
    private var onset = 0f

    init { computeBandEdges() }

    fun setSampleRate(rate: Int) {
        if (rate > 0 && rate != sampleRate) {
            sampleRate = rate
            computeBandEdges()
        }
    }

    private fun computeBandEdges() {
        val binHz = sampleRate.toFloat() / frameSize
        val ratio = (MAX_HZ / MIN_HZ).toDouble().pow(1.0 / AudioPulse.BAND_COUNT)
        for (b in 0..AudioPulse.BAND_COUNT) {
            val hz = MIN_HZ * ratio.pow(b.toDouble())
            bandEdges[b] = (hz / binHz).toInt().coerceIn(1, frameSize / 2 - 1)
        }
    }

    fun analyse(frame: FloatArray): AudioPulse {
        require(frame.size == frameSize) { "trame de ${frame.size}, attendu $frameSize" }
        var sumSquares = 0f
        for (i in 0 until frameSize) {
            re[i] = frame[i] * window[i]
            im[i] = 0f
            sumSquares += frame[i] * frame[i]
        }
        fft(re, im)
        // Pleine échelle avec Hann : |X| ≈ N/4. On ramène à 1.0, puis en dB sur 60 dB.
        val scale = 4f / frameSize
        for (k in 0 until frameSize / 2) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
            val db = 20f * log10(max(m, 1e-6f))
            magnitudes[k] = ((db + RANGE_DB) / RANGE_DB).coerceIn(0f, 1f)
        }
        for (b in 0 until AudioPulse.BAND_COUNT) {
            val from = bandEdges[b]
            val to = max(bandEdges[b + 1], from + 1)
            var peak = 0f
            for (k in from until to) if (magnitudes[k] > peak) peak = magnitudes[k]
            raw[b] = peak
            val s = smoothed[b]
            smoothed[b] = if (peak > s) s + (peak - s) * ATTACK else s + (peak - s) * RELEASE
        }
        val rms = (sqrt(sumSquares / frameSize) * RMS_GAIN).coerceIn(0f, 1f)
        val flux = (rms - previousRms).coerceAtLeast(0f)
        previousRms = rms
        onset = max(onset * ONSET_DECAY, (flux * ONSET_GAIN).coerceAtMost(1f))
        return AudioPulse(
            bass = peakIn(MIN_HZ, 150f),
            mid = peakIn(150f, 2_000f),
            treble = peakIn(2_000f, MAX_HZ),
            rms = rms,
            onset = onset,
            bands = smoothed.copyOf(),
        )
    }

    /** Valeur la plus haute parmi les bandes lissées dont la borne basse est dans [lowHz, highHz[. */
    private fun peakIn(lowHz: Float, highHz: Float): Float {
        val ratio = (MAX_HZ / MIN_HZ).toDouble().pow(1.0 / AudioPulse.BAND_COUNT)
        var peak = 0f
        for (b in 0 until AudioPulse.BAND_COUNT) {
            val hz = (MIN_HZ * ratio.pow(b.toDouble())).toFloat()
            if (hz >= lowHz && hz < highHz && smoothed[b] > peak) peak = smoothed[b]
        }
        return peak
    }

    /** FFT itérative radix-2 en place ; `re.size` doit être une puissance de deux. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe; im[b] = im[a] - tIm
                    re[a] += tRe; im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object {
        const val MIN_HZ = 20f
        const val MAX_HZ = 16_000f
        const val RANGE_DB = 60f
        /** Lissage par image à 30 Hz : attaque ≈ 50 ms, relâchement ≈ 400 ms. */
        const val ATTACK = 0.6f
        const val RELEASE = 0.08f
        /** Un sinus pleine échelle a un rms de 0,707 : on le ramène à 1. */
        const val RMS_GAIN = 1.4f
        const val ONSET_GAIN = 3f
        /** ≈ 300 ms pour retomber à 10 % à 30 images par seconde. */
        const val ONSET_DECAY = 0.78f
    }
}
