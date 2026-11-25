package fr.synxio.player.data.media.dsp

import fr.synxio.player.data.model.TrackTraits
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tout ce qui a été décodé pour un titre : tête, fenêtres centrales, queue. Échantillons mono PCM 16 bits. */
data class DecodedTrack(
    val sampleRate: Int,
    val durationMs: Long,
    val head: ShortArray,
    val middle: List<ShortArray>,
    val tail: ShortArray,
    val tailStartMs: Long,
)

/** Enveloppe d'onsets : flux spectral demi-onde, trames 1 024, saut 512. */
object OnsetEnvelope {
    const val FRAME = 1024
    const val HOP = 512

    class Result(val values: FloatArray, val frameRate: Float)

    fun compute(samples: ShortArray, sampleRate: Int): Result {
        val fft = Fft(FRAME)
        val frame = FloatArray(FRAME)
        var previous = FloatArray(FRAME / 2)
        var current = FloatArray(FRAME / 2)
        val count = max(0, (samples.size - FRAME) / HOP + 1)
        val values = FloatArray(count)
        for (f in 0 until count) {
            val start = f * HOP
            for (i in 0 until FRAME) frame[i] = samples[start + i] / 32768f
            fft.magnitudes(frame, current)
            var flux = 0f
            for (i in 1 until FRAME / 2) {
                val d = current[i] - previous[i]
                if (d > 0f) flux += d
            }
            values[f] = flux
            val swap = previous; previous = current; current = swap
        }
        return Result(values, sampleRate.toFloat() / HOP)
    }
}

/** Tempo par autocorrélation de l'enveloppe d'onsets, 60–200 BPM, préférence perceptive autour de 120. */
object TempoEstimator {
    const val MIN_BPM = 60f
    const val MAX_BPM = 200f

    class Estimate(val bpm: Float, val confidence: Float)

    fun estimate(onsets: OnsetEnvelope.Result): Estimate {
        val x = onsets.values
        if (x.size < 32) return Estimate(0f, 0f)
        val mean = x.average().toFloat()
        val centered = FloatArray(x.size) { x[it] - mean }
        if (centered.all { abs(it) < 1e-6f }) return Estimate(0f, 0f)
        val minLag = (60f * onsets.frameRate / MAX_BPM).roundToInt().coerceAtLeast(1)
        val maxLag = (60f * onsets.frameRate / MIN_BPM).roundToInt().coerceAtMost(x.size / 2)
        if (maxLag <= minLag) return Estimate(0f, 0f)

        var bestLag = -1; var best = Float.NEGATIVE_INFINITY; var sum = 0f; var n = 0
        val scores = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var acc = 0f
            for (i in 0 until x.size - lag) acc += centered[i] * centered[i + lag]
            acc /= (x.size - lag)
            val bpm = 60f * onsets.frameRate / lag
            val weight = exp(-0.5f * (log2(bpm / 120f) / 0.9f).let { it * it })
            val score = acc * weight
            scores[lag] = score
            sum += score; n++
            if (score > best) { best = score; bestLag = lag }
        }
        if (bestLag < 0 || best <= 0f) return Estimate(0f, 0f)
        // Interpolation parabolique autour du pic : à 43 trames/s, un retard entier vaut
        // ±2,5 BPM autour de 120 ; le sommet de la parabole rend la fraction de trame.
        var refinedLag = bestLag.toFloat()
        if (bestLag - 1 >= minLag && bestLag + 1 <= maxLag) {
            val l = scores[bestLag - 1]; val c = scores[bestLag]; val r = scores[bestLag + 1]
            val denominator = l - 2f * c + r
            if (abs(denominator) > 1e-9f) refinedLag += 0.5f * (l - r) / denominator
        }
        var bpm = 60f * onsets.frameRate / refinedLag
        // Correction d'octave : un tempo très lent ou très rapide dont le double ou la moitié
        // explique presque aussi bien l'enveloppe est ramené vers 70–180.
        val half = bestLag * 2; val double = bestLag / 2
        if (bpm < 70f && double >= minLag && scores[double] >= 0.6f * best) bpm *= 2f
        else if (bpm > 180f && half <= maxLag && scores[half] >= 0.6f * best) bpm /= 2f
        val meanScore = sum / n
        val confidence = ((best - meanScore) / best).coerceIn(0f, 1f)
        return Estimate(bpm, confidence)
    }

    /** Médiane des estimations non nulles ; confiance moyenne. */
    fun combine(estimates: List<Estimate>): Estimate {
        val valid = estimates.filter { it.bpm > 0f }
        if (valid.isEmpty()) return Estimate(0f, 0f)
        val sorted = valid.map { it.bpm }.sorted()
        val median = sorted[sorted.size / 2]
        return Estimate(median, valid.map { it.confidence }.average().toFloat())
    }
}

/** Chroma 12 classes sur trames de 4 096, bins 55 Hz–4 kHz, moyenne des trames. */
object Chroma {
    const val FRAME = 4096
    const val HOP = 2048

    fun compute(samples: ShortArray, sampleRate: Int, accumulate: FloatArray = FloatArray(12)): FloatArray {
        val fft = Fft(FRAME)
        val frame = FloatArray(FRAME)
        val mags = FloatArray(FRAME / 2)
        val count = max(0, (samples.size - FRAME) / HOP + 1)
        val binHz = sampleRate.toFloat() / FRAME
        for (f in 0 until count) {
            val start = f * HOP
            for (i in 0 until FRAME) frame[i] = samples[start + i] / 32768f
            fft.magnitudes(frame, mags)
            for (i in 1 until FRAME / 2) {
                val hz = i * binHz
                if (hz < 55f || hz > 4000f) continue
                val midi = 69.0 + 12.0 * log2(hz / 440.0)
                val pc = ((midi.roundToInt() % 12) + 12) % 12
                accumulate[pc] += mags[i] * mags[i]
            }
        }
        return accumulate
    }
}

/** Tonalité par corrélation du chroma avec les profils de Krumhansl-Schmuckler. */
object KeyEstimator {
    private val major = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val minor = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    class Estimate(val keyIndex: Int, val confidence: Float)

    fun estimate(chroma: FloatArray): Estimate {
        if (chroma.size != 12 || chroma.all { it <= 0f }) return Estimate(-1, 0f)
        val scores = FloatArray(24)
        for (root in 0 until 12) {
            scores[root] = correlation(chroma, major, root)
            scores[12 + root] = correlation(chroma, minor, root)
        }
        var best = 0
        for (i in 1 until 24) if (scores[i] > scores[best]) best = i
        val second = scores.withIndex().filter { it.index != best }.maxOf { it.value }
        val confidence = ((scores[best] - second) / max(abs(scores[best]), 1e-6f)).coerceIn(0f, 1f)
        return Estimate(best, confidence)
    }

    private fun correlation(chroma: FloatArray, profile: FloatArray, root: Int): Float {
        val n = 12
        var mx = 0f; var my = 0f
        for (i in 0 until n) { mx += chroma[i]; my += profile[i] }
        mx /= n; my /= n
        var sxy = 0f; var sxx = 0f; var syy = 0f
        for (i in 0 until n) {
            val x = chroma[i] - mx
            val y = profile[(i - root + 12) % 12] - my
            sxy += x * y; sxx += x * x; syy += y * y
        }
        val d = sqrt(sxx * syy)
        return if (d <= 0f) 0f else sxy / d
    }
}

/** Énergie 0–1 : 0,6 × niveau RMS (−35 → 0, −8 → 1) + 0,4 × densité d'onsets (0 → 0, 6/s → 1). */
object EnergyScore {
    fun rmsDbfs(samples: ShortArray): Float {
        if (samples.isEmpty()) return -70f
        var acc = 0.0
        for (s in samples) { val v = s / 32768.0; acc += v * v }
        val rms = sqrt(acc / samples.size)
        return if (rms <= 1e-9) -70f else (20 * log10(rms)).toFloat()
    }

    fun onsetsPerSecond(onsets: OnsetEnvelope.Result): Float {
        val x = onsets.values
        if (x.size < 4) return 0f
        val mean = x.average().toFloat()
        val std = sqrt(x.map { (it - mean) * (it - mean) }.average()).toFloat()
        val threshold = mean + std
        var count = 0
        for (i in 1 until x.size - 1) if (x[i] > threshold && x[i] >= x[i - 1] && x[i] > x[i + 1]) count++
        return count / (x.size / onsets.frameRate)
    }

    fun score(dbfs: Float, onsetsPerSecond: Float): Float {
        val level = ((dbfs + 35f) / 27f).coerceIn(0f, 1f)
        val density = (onsetsPerSecond / 6f).coerceIn(0f, 1f)
        return (0.6f * level + 0.4f * density).coerceIn(0f, 1f)
    }
}

/** Intro et outro : premier/dernier moment où le RMS glissant (200 ms) dépasse −30 dBFS. */
object SilenceBounds {
    const val THRESHOLD_DBFS = -30f
    private const val WINDOW_MS = 200
    private const val SUSTAIN_WINDOWS = 3

    /** Début réel (ms depuis 0) sur la tête du titre ; 0 si ça joue tout de suite. */
    fun introMs(head: ShortArray, sampleRate: Int): Long {
        val loud = loudWindows(head, sampleRate)
        for (i in 0..loud.size - SUSTAIN_WINDOWS) {
            if ((0 until SUSTAIN_WINDOWS).all { loud[i + it] }) return i.toLong() * WINDOW_MS
        }
        return 0L
    }

    /** Début de la queue silencieuse ; [durationMs] si la fin joue encore. */
    fun outroMs(tail: ShortArray, sampleRate: Int, tailStartMs: Long, durationMs: Long): Long {
        val loud = loudWindows(tail, sampleRate)
        val last = loud.lastIndexOf(true)
        if (last < 0) return tailStartMs
        if (last >= loud.size - 1) return durationMs
        return (tailStartMs + (last + 1).toLong() * WINDOW_MS).coerceAtMost(durationMs)
    }

    private fun loudWindows(samples: ShortArray, sampleRate: Int): BooleanArray {
        val window = sampleRate * WINDOW_MS / 1000
        if (window <= 0 || samples.size < window) return BooleanArray(0)
        val count = samples.size / window
        return BooleanArray(count) { w ->
            var acc = 0.0
            val start = w * window
            for (i in start until start + window) { val v = samples[i] / 32768.0; acc += v * v }
            val rms = sqrt(acc / window)
            rms > 1e-9 && 20 * log10(rms) > THRESHOLD_DBFS
        }
    }
}

/** Assemble les traits d'un titre à partir de son décodage. Pur, testé en JVM. */
fun analyseTraits(track: DecodedTrack): TrackTraits {
    val tempo = TempoEstimator.combine(track.middle.map { TempoEstimator.estimate(OnsetEnvelope.compute(it, track.sampleRate)) })
    val chroma = FloatArray(12)
    track.middle.forEach { Chroma.compute(it, track.sampleRate, chroma) }
    val key = KeyEstimator.estimate(chroma)
    val dbfs = if (track.middle.isEmpty()) -70f else track.middle.map { EnergyScore.rmsDbfs(it) }.average().toFloat()
    val onsetRate = if (track.middle.isEmpty()) 0f else track.middle.map { EnergyScore.onsetsPerSecond(OnsetEnvelope.compute(it, track.sampleRate)) }.average().toFloat()
    return TrackTraits(
        bpm = tempo.bpm,
        bpmConfidence = tempo.confidence,
        keyIndex = key.keyIndex,
        keyConfidence = key.confidence,
        energy = EnergyScore.score(dbfs, onsetRate),
        introMs = SilenceBounds.introMs(track.head, track.sampleRate),
        outroMs = SilenceBounds.outroMs(track.tail, track.sampleRate, track.tailStartMs, track.durationMs),
    )
}

/** Logarithme naturel exposé pour les tests de confiance. */
internal fun safeLn(x: Float): Float = if (x <= 0f) 0f else ln(x)
