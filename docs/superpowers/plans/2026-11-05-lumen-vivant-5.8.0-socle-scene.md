# Lumen Vivant 5.8.0 — plan 1 : socle audio et scène réactive

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer le visualiseur à micro par une analyse du PCM d'ExoPlayer, et faire respirer la scène Lumen (aurore, barre de progression, paroles, mini-lecteur) sur la musique, sous un budget batterie explicite.

**Architecture:** Un `TeeAudioProcessor` Media3 alimente un tampon circulaire (`PulseSink`), lu à 30 Hz par `AudioAnalyser` (Kotlin pur) dans `PulseSource`, singleton Hilt exposant `pulse: StateFlow<AudioPulse>` qui ne tourne qu'avec des abonnés et en lecture. Côté UI, `rememberScenePulse()` applique les coupures et fournit `LocalScenePulse` ; `SceneMapper` (pur) traduit le pouls en paramètres de halos lus dans la phase de dessin, jamais en composition.

**Tech Stack:** Kotlin 2.4, Media3 1.11.1 (`TeeAudioProcessor`, `AudioProcessingPipeline` pour les tests), Compose BOM 2026.09, Hilt, DataStore Preferences, JUnit 4 + kotlinx-coroutines-test sur la JVM.

**Spec:** `docs/superpowers/specs/2026-11-05-lumen-vivant-5.8.0-design.md` (sections 1, 2 et la part « Scène réactive » de la section 6)

## Global Constraints

- Aucune nouvelle dépendance Gradle.
- Textes de l'interface en français, tutoiement. KDoc et commentaires en français, pas de TODO.
- Chemin chaud audio (`PulseSink.handleBuffer`) : aucune allocation, aucun verrou, aucun log.
- Analyse à 30 Hz (`delay(33)`), FFT 1024 points, fenêtre de Hann, 32 bandes logarithmiques de 20 Hz à 16 kHz, sorties dans 0..1.
- `pulse` partagé en `SharingStarted.WhileSubscribed(stopTimeoutMillis = 500)` ; en pause, `AudioPulse.SILENT` sans calcul.
- Scène coupée (pouls `null`, aucun abonnement) si : `LocalReduceMotion`, `LocalGlowEffects` faux, réglage `sceneReactive` faux, économie d'énergie, batterie ≤ 15 %, écran hors `STARTED`, lecture en pause, ni lecteur ouvert ni mini-lecteur visible.
- Lecture du pouls uniquement dans `drawBehind` / `drawWithContent` / `graphicsLayer {}` : un tick ne recompose rien.
- Bornes de `SceneMapper` : `haloScale ≤ 1.25`, `brightness` dans 0.85..1.15, lissage 0.5 par image, `frozen` après 1 000 ms de silence.
- `RECORD_AUDIO` retirée du manifeste ; plus aucune référence à `android.media.audiofx.Visualizer`.
- Réglage « Scène réactive » : `Settings.sceneReactive` (défaut `true`), clé `scene_reactive`, groupe « Mouvement et lumière », juste après « Effets lumineux ».
- Commandes Gradle lancées depuis PowerShell (`.\gradlew.bat`), jamais depuis Git Bash. Tests JVM : `.\gradlew.bat :app:testDebugUnitTest --tests "<classe>"`.
- Chaque tâche se termine par un commit sur `dev-5.5`. Pas de push.

---

## Structure des fichiers

| Fichier | Rôle |
|---|---|
| `playback/pulse/AudioPulse.kt` (créer) | Valeur immuable du pouls, `SILENT`, égalité par contenu. |
| `playback/pulse/PulseRingBuffer.kt` (créer) | Tampon circulaire mono, un écrivain (thread audio), un lecteur. |
| `playback/pulse/AudioAnalyser.kt` (créer) | FFT, bandes, lissage, onset. Pur. |
| `playback/pulse/PulseSink.kt` (créer) | `TeeAudioProcessor.AudioBufferSink` : PCM 16 bits ou float → mono → tampon. |
| `playback/pulse/PulseSource.kt` (créer) | Singleton Hilt : `sink`, `setPlaying`, `pulse`. |
| `playback/PlaybackService.kt` (modifier) | Chaîne de processeurs, `onIsPlayingChanged`. |
| `playback/PlayerConnection.kt` (modifier) | `pulse` réexposé. |
| `playback/EqualizerController.kt` (modifier) | Suppression du `Visualizer`. |
| `ui/viewmodel/AppViewModel.kt`, `ui/viewmodel/EqualizerViewModel.kt` (modifier) | `fftFlow` → `pulse`, suppression de `setVisualizerEnabled`. |
| `ui/components/AudioVisualizer.kt` (modifier) | Barres depuis `bands`. |
| `ui/components/VisualizerView.kt` (supprimer) | Doublon de l'écran EQ. |
| `ui/screens/EqualizerScreen.kt`, `ui/screens/nowplaying/NowPlayingScreen.kt` (modifier) | Consommateurs. |
| `app/src/main/AndroidManifest.xml` (modifier) | `RECORD_AUDIO` retirée. |
| `ui/theme/SceneMapper.kt` (créer) | `SceneParams`, `mapScene`, `sceneAllowed`. Pur. |
| `ui/theme/ScenePulse.kt` (créer) | `rememberScenePulse()`, `LocalScenePulse`, récepteurs énergie/batterie. |
| `ui/theme/Glass.kt` (modifier) | `AmbientBackdrop(pulse)`. |
| `ui/SynxioRoot.kt` (modifier) | Branchement du pouls, fourniture du local. |
| `ui/components/Lumen.kt`, `ui/components/MiniPlayer.kt`, `ui/screens/nowplaying/Sheets.kt` (modifier) | Signature qui pulse. |
| `core/prefs/SettingsRepository.kt`, `ui/viewmodel/SettingsViewModel.kt`, `ui/screens/SettingsScreen.kt` (modifier) | Réglage « Scène réactive ». |
| `docs/release-notes/v5.8.0.md` (créer) | Notes, section « Scène », checklist d'appareil. |

---

### Task 1 : `AudioPulse`, `PulseRingBuffer`, `AudioAnalyser`

**Files:**
- Create: `app/src/main/java/fr/synxio/player/playback/pulse/AudioPulse.kt`
- Create: `app/src/main/java/fr/synxio/player/playback/pulse/PulseRingBuffer.kt`
- Create: `app/src/main/java/fr/synxio/player/playback/pulse/AudioAnalyser.kt`
- Test: `app/src/test/java/fr/synxio/player/playback/pulse/PulseRingBufferTest.kt`
- Test: `app/src/test/java/fr/synxio/player/playback/pulse/AudioAnalyserTest.kt`

**Interfaces:**
- Produces: `class AudioPulse(bass, mid, treble, rms, onset: Float, bands: FloatArray)` avec `companion val SILENT`, `equals` par contenu ; `class PulseRingBuffer(capacity: Int = 2048)` avec `fun write(samples: FloatArray, count: Int)`, `fun readLast(out: FloatArray)`, `val written: Long` ; `class AudioAnalyser(sampleRate: Int = 44_100, frameSize: Int = 1024)` avec `fun analyse(frame: FloatArray): AudioPulse`, `fun setSampleRate(rate: Int)`.

- [ ] **Step 1 : Tests du tampon**

```kotlin
package fr.synxio.player.playback.pulse

import org.junit.Assert.assertEquals
import org.junit.Test

class PulseRingBufferTest {
    @Test
    fun `readLast rend les derniers echantillons dans l'ordre`() {
        val buffer = PulseRingBuffer(capacity = 8)
        buffer.write(floatArrayOf(1f, 2f, 3f, 4f, 5f), 5)
        buffer.write(floatArrayOf(6f, 7f, 8f, 9f, 10f), 5)
        val out = FloatArray(4)
        buffer.readLast(out)
        assertEquals(listOf(7f, 8f, 9f, 10f), out.toList())
        assertEquals(10L, buffer.written)
    }

    @Test
    fun `avant remplissage - zeros devant`() {
        val buffer = PulseRingBuffer(capacity = 8)
        buffer.write(floatArrayOf(1f, 2f), 2)
        val out = FloatArray(4)
        buffer.readLast(out)
        assertEquals(listOf(0f, 0f, 1f, 2f), out.toList())
    }

    @Test
    fun `count limite l'ecriture`() {
        val buffer = PulseRingBuffer(capacity = 4)
        buffer.write(floatArrayOf(1f, 2f, 3f), 2)
        assertEquals(2L, buffer.written)
    }
}
```

- [ ] **Step 2 : Tests de l'analyseur**

```kotlin
package fr.synxio.player.playback.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioAnalyserTest {
    private val rate = 44_100
    private fun sine(hz: Float, amplitude: Float = 0.8f, size: Int = 1024) =
        FloatArray(size) { (amplitude * sin(2.0 * PI * hz * it / rate)).toFloat() }
    private fun noise(size: Int = 1024, seed: Int = 7): FloatArray {
        val r = java.util.Random(seed.toLong())
        return FloatArray(size) { (r.nextFloat() * 2f - 1f) }
    }
    private fun settle(analyser: AudioAnalyser, frame: FloatArray, frames: Int = 20): AudioPulse {
        var pulse = AudioPulse.SILENT
        repeat(frames) { pulse = analyser.analyse(frame) }
        return pulse
    }

    @Test
    fun `silence - pouls muet`() {
        val pulse = AudioAnalyser(rate).analyse(FloatArray(1024))
        assertEquals(AudioPulse.SILENT, pulse)
    }

    @Test
    fun `sinus 60 Hz - basses hautes, aigus bas`() {
        val pulse = settle(AudioAnalyser(rate), sine(60f))
        assertTrue("bass=${pulse.bass}", pulse.bass > 0.7f)
        assertTrue("treble=${pulse.treble}", pulse.treble < 0.1f)
        assertTrue("rms=${pulse.rms}", pulse.rms > 0.7f)
    }

    @Test
    fun `sinus 8 kHz - aigus hauts, basses basses`() {
        val pulse = settle(AudioAnalyser(rate), sine(8_000f))
        assertTrue("treble=${pulse.treble}", pulse.treble > 0.7f)
        assertTrue("bass=${pulse.bass}", pulse.bass < 0.1f)
    }

    @Test
    fun `bruit blanc - toutes les bandes vivent`() {
        val pulse = settle(AudioAnalyser(rate), noise())
        assertTrue(pulse.bands.joinToString(), pulse.bands.all { it > 0.1f })
        assertEquals(32, pulse.bands.size)
    }

    @Test
    fun `attaque - onset proche de 1 puis retombe sous 0,1 en 400 ms`() {
        val analyser = AudioAnalyser(rate)
        settle(analyser, FloatArray(1024), frames = 10)
        val hit = analyser.analyse(sine(200f))
        assertTrue("onset=${hit.onset}", hit.onset > 0.8f)
        val after = settle(analyser, sine(200f), frames = 12)
        assertTrue("onset=${after.onset}", after.onset < 0.1f)
    }

    @Test
    fun `tout reste dans 0 a 1`() {
        val pulse = settle(AudioAnalyser(rate), noise(seed = 3).map { it * 3f }.toFloatArray())
        listOf(pulse.bass, pulse.mid, pulse.treble, pulse.rms, pulse.onset).forEach { assertTrue(it in 0f..1f) }
        assertTrue(pulse.bands.all { it in 0f..1f })
    }
}
```

- [ ] **Step 3 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.pulse.*"`
Attendu : échec de compilation, `AudioPulse`, `PulseRingBuffer`, `AudioAnalyser` inconnus.

- [ ] **Step 4 : `AudioPulse`**

```kotlin
package fr.synxio.player.playback.pulse

/**
 * Le pouls de la musique à un instant : niveaux par registre, niveau global, attaque,
 * spectre en 32 bandes. Toutes les valeurs sont dans 0..1. Égalité par contenu, pour
 * que les flux et les tests comparent des valeurs et non des références.
 */
class AudioPulse(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val rms: Float = 0f,
    val onset: Float = 0f,
    val bands: FloatArray = FloatArray(BAND_COUNT),
) {
    override fun equals(other: Any?): Boolean = other is AudioPulse &&
        bass == other.bass && mid == other.mid && treble == other.treble &&
        rms == other.rms && onset == other.onset && bands.contentEquals(other.bands)

    override fun hashCode(): Int =
        listOf(bass, mid, treble, rms, onset).hashCode() * 31 + bands.contentHashCode()

    override fun toString(): String =
        "AudioPulse(bass=$bass, mid=$mid, treble=$treble, rms=$rms, onset=$onset)"

    companion object {
        const val BAND_COUNT = 32
        val SILENT = AudioPulse()
    }
}
```

- [ ] **Step 5 : `PulseRingBuffer`**

```kotlin
package fr.synxio.player.playback.pulse

/**
 * Tampon circulaire mono. Un seul écrivain (le thread audio) et un seul lecteur
 * (l'analyseur) : pas de verrou, l'index d'écriture est volatile. Une lecture qui
 * chevauche une écriture mélange deux instants ; pour une animation, c'est sans effet.
 */
class PulseRingBuffer(private val capacity: Int = 2048) {
    private val data = FloatArray(capacity)

    @Volatile
    var written: Long = 0L
        private set

    /** Copie [count] échantillons de [samples] ; sans allocation. */
    fun write(samples: FloatArray, count: Int) {
        var index = (written % capacity).toInt()
        for (i in 0 until count) {
            data[index] = samples[i]
            index++
            if (index == capacity) index = 0
        }
        written += count
    }

    /** Remplit [out] avec les `out.size` derniers échantillons, zéros devant si trop tôt. */
    fun readLast(out: FloatArray) {
        val total = written
        val n = out.size
        val available = minOf(total, n.toLong()).toInt()
        val lead = n - available
        for (i in 0 until lead) out[i] = 0f
        var index = ((total - available) % capacity).toInt()
        for (i in 0 until available) {
            out[lead + i] = data[index]
            index++
            if (index == capacity) index = 0
        }
    }
}
```

- [ ] **Step 6 : `AudioAnalyser`**

```kotlin
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
```

- [ ] **Step 7 : Vérifier le succès**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.pulse.*"`
Attendu : 9 tests verts. Si « bruit blanc » échoue sur une bande, abaisser `ATTACK` n'aide pas : vérifier `computeBandEdges` (chaque bande doit couvrir au moins un bin, d'où `max(bandEdges[b + 1], from + 1)`).

- [ ] **Step 8 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/playback/pulse app/src/test/java/fr/synxio/player/playback/pulse
git commit -m "pouls: analyseur audio pur (FFT, bandes, onset) et tampon circulaire"
```

---

### Task 2 : `PulseSink` sur `TeeAudioProcessor`

**Files:**
- Create: `app/src/main/java/fr/synxio/player/playback/pulse/PulseSink.kt`
- Test: `app/src/test/java/fr/synxio/player/playback/pulse/PulseSinkTest.kt`

**Interfaces:**
- Consumes: `PulseRingBuffer` (Task 1).
- Produces: `class PulseSink(buffer: PulseRingBuffer) : TeeAudioProcessor.AudioBufferSink` avec `val sampleRate: Int` (volatile, 0 tant que rien n'est configuré) et `val supported: Boolean`.

- [ ] **Step 1 : Test**

```kotlin
package fr.synxio.player.playback.pulse

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PulseSinkTest {
    private fun pipelineWith(sink: PulseSink, format: AudioFormat): AudioProcessingPipeline =
        AudioProcessingPipeline(ImmutableList.of(TeeAudioProcessor(sink))).apply {
            configure(format)
            flush()
        }

    private fun feed(pipeline: AudioProcessingPipeline, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val input = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        input.flip()
        var guard = 0
        while (input.hasRemaining()) {
            pipeline.queueInput(input)
            drain(pipeline, out)
            check(++guard < 10_000)
        }
        pipeline.queueEndOfStream()
        drain(pipeline, out)
        return out.toByteArray()
    }

    private fun drain(pipeline: AudioProcessingPipeline, out: ByteArrayOutputStream) {
        var buffer = pipeline.output
        while (buffer.hasRemaining()) {
            val chunk = ByteArray(buffer.remaining())
            buffer.get(chunk)
            out.write(chunk)
            buffer = pipeline.output
        }
    }

    @Test
    fun `stereo 16 bits - sortie identique, tampon mono moyenne des canaux`() {
        val buffer = PulseRingBuffer(64)
        val sink = PulseSink(buffer)
        val pipeline = pipelineWith(sink, AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        // 4 trames : gauche = 16384, droite = -16384 → mono 0 ; puis gauche = droite = 8192 → 0,25.
        val samples = shortArrayOf(16384, -16384, 16384, -16384, 8192, 8192, 8192, 8192)
        val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder())
            .apply { samples.forEach { putShort(it) } }.array()
        val out = feed(pipeline, bytes)
        assertArrayEquals(bytes, out)
        assertTrue(sink.supported)
        assertEquals(48_000, sink.sampleRate)
        val last = FloatArray(4)
        buffer.readLast(last)
        assertEquals(listOf(0f, 0f, 0.25f, 0.25f), last.toList())
    }

    @Test
    fun `mono float - valeurs copiees telles quelles`() {
        val buffer = PulseRingBuffer(64)
        val sink = PulseSink(buffer)
        val pipeline = pipelineWith(sink, AudioFormat(44_100, 1, C.ENCODING_PCM_FLOAT))
        val values = floatArrayOf(0.5f, -0.5f, 1f)
        val bytes = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder()).apply { values.forEach { putFloat(it) } }.array()
        feed(pipeline, bytes)
        val last = FloatArray(3)
        buffer.readLast(last)
        assertEquals(values.toList(), last.toList())
    }

    @Test
    fun `encodage inconnu - passe-plat, rien d'ecrit`() {
        val buffer = PulseRingBuffer(64)
        val sink = PulseSink(buffer)
        val pipeline = pipelineWith(sink, AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT))
        val bytes = ByteArray(12) { it.toByte() }
        assertArrayEquals(bytes, feed(pipeline, bytes))
        assertFalse(sink.supported)
        assertEquals(0L, buffer.written)
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.pulse.PulseSinkTest"`
Attendu : échec de compilation, `PulseSink` inconnu.

- [ ] **Step 3 : Implémentation**

```kotlin
package fr.synxio.player.playback.pulse

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer

/**
 * Reçoit une copie du PCM qui sort d'ExoPlayer (via [TeeAudioProcessor], qui laisse le
 * flux inchangé), le mixe en mono et l'écrit dans [buffer]. Chemin chaud : aucune
 * allocation après la construction, aucun verrou, aucun log.
 *
 * Formats acceptés : PCM 16 bits et float. Les autres rendent le puits inactif ; le son
 * continue, le pouls reste muet.
 */
class PulseSink(private val buffer: PulseRingBuffer) : TeeAudioProcessor.AudioBufferSink {

    @Volatile
    var sampleRate: Int = 0
        private set

    @Volatile
    var supported: Boolean = false
        private set

    private var channels = 1
    private var encoding = C.ENCODING_INVALID
    private val scratch = FloatArray(SCRATCH_FRAMES)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        supported = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
    }

    override fun handleBuffer(input: ByteBuffer) {
        if (!supported) return
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * channels
        var position = input.position()
        val limit = input.limit()
        var count = 0
        while (position + frameBytes <= limit) {
            var sum = 0f
            for (c in 0 until channels) {
                val offset = position + c * bytesPerSample
                sum += if (bytesPerSample == 4) input.getFloat(offset) else input.getShort(offset) / 32_768f
            }
            scratch[count++] = sum / channels
            position += frameBytes
            if (count == SCRATCH_FRAMES) {
                buffer.write(scratch, count)
                count = 0
            }
        }
        if (count > 0) buffer.write(scratch, count)
    }

    private companion object {
        const val SCRATCH_FRAMES = 4096
    }
}
```

`TeeAudioProcessor.handleBuffer` reçoit le tampon positionné sur les données à copier et ne le consomme pas (lecture absolue par `getShort(offset)` / `getFloat(offset)`) : la position du tampon reste intacte pour le processeur.

- [ ] **Step 4 : Vérifier le succès**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.pulse.PulseSinkTest"`
Attendu : 3 tests verts.

- [ ] **Step 5 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/playback/pulse/PulseSink.kt app/src/test/java/fr/synxio/player/playback/pulse/PulseSinkTest.kt
git commit -m "pouls: puits PCM sur TeeAudioProcessor, mono vers le tampon"
```

---

### Task 3 : `PulseSource` et branchement dans le service

**Files:**
- Create: `app/src/main/java/fr/synxio/player/playback/pulse/PulseSource.kt`
- Modify: `app/src/main/java/fr/synxio/player/playback/PlaybackService.kt:89-98` (injections), `:151` (`vocalRemover`), `:244-254` (`buildAudioSink`), `:1047` (`onIsPlayingChanged`)
- Modify: `app/src/main/java/fr/synxio/player/playback/PlayerConnection.kt:98-109`
- Test: `app/src/test/java/fr/synxio/player/playback/pulse/PulseSourceTest.kt`

**Interfaces:**
- Consumes: `PulseSink`, `PulseRingBuffer`, `AudioAnalyser`, `AudioPulse`.
- Produces: `@Singleton class PulseSource` avec `val sink: PulseSink`, `fun setPlaying(playing: Boolean)`, `val pulse: StateFlow<AudioPulse>` ; `PlayerConnection.pulse: StateFlow<AudioPulse>`.

- [ ] **Step 1 : Test**

```kotlin
package fr.synxio.player.playback.pulse

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PulseSourceTest {
    private var analyses = 0
    private val loud = AudioPulse(rms = 0.9f)

    // backgroundScope : le partage `stateIn` reste actif après le test, runTest l'annule.
    private fun TestScope.source() = PulseSource(
        scope = backgroundScope,
        tickMs = 33,
        produce = { analyses++; loud },
        sink = PulseSink(PulseRingBuffer()),
    )

    @Test
    fun `sans abonne - aucune analyse`() = runTest {
        val source = source()
        source.setPlaying(true)
        advanceTimeBy(1_000)
        assertEquals(0, analyses)
        assertEquals(AudioPulse.SILENT, source.pulse.value)
    }

    @Test
    fun `abonne et en lecture - analyse a 30 Hz`() = runTest {
        val source = source()
        source.setPlaying(true)
        val job: Job = launch { source.pulse.collect { } }
        advanceTimeBy(1_000)
        assertTrue("analyses=$analyses", analyses in 28..32)
        assertEquals(loud, source.pulse.value)
        job.cancel()
    }

    @Test
    fun `abonne mais en pause - muet sans analyse`() = runTest {
        val source = source()
        source.setPlaying(false)
        val job = launch { source.pulse.collect { } }
        advanceTimeBy(500)
        assertEquals(0, analyses)
        assertEquals(AudioPulse.SILENT, source.pulse.value)
        job.cancel()
    }

    @Test
    fun `desabonnement - l'analyse s'arrete apres 500 ms`() = runTest {
        val source = source()
        source.setPlaying(true)
        val job = launch { source.pulse.collect { } }
        advanceTimeBy(330)
        job.cancel()
        val before = analyses
        advanceTimeBy(2_000)
        assertTrue(analyses - before <= 16)
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.pulse.PulseSourceTest"`
Attendu : échec de compilation.

- [ ] **Step 3 : `PulseSource`**

```kotlin
package fr.synxio.player.playback.pulse

import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source du pouls : le service branche [sink] dans la chaîne audio et dit quand la
 * lecture tourne ; l'interface s'abonne à [pulse]. Sans abonné, rien ne tourne ; en
 * pause, le flux émet [AudioPulse.SILENT] sans calculer.
 */
@Singleton
class PulseSource internal constructor(
    scope: CoroutineScope,
    private val tickMs: Long,
    private val produce: () -> AudioPulse,
    val sink: PulseSink,
) {
    @Inject
    constructor(@ApplicationScope scope: CoroutineScope) : this(Analysis(scope))

    private constructor(analysis: Analysis) : this(analysis.scope, TICK_MS, analysis::next, analysis.sink)

    private val playing = MutableStateFlow(false)

    val pulse: StateFlow<AudioPulse> = flow {
        while (currentCoroutineContext().isActive) {
            emit(if (playing.value) produce() else AudioPulse.SILENT)
            delay(tickMs)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MS), AudioPulse.SILENT)

    fun setPlaying(value: Boolean) {
        playing.value = value
    }

    /** Tampon, puits et analyseur de production, réunis pour que le constructeur Hilt reste simple. */
    private class Analysis(val scope: CoroutineScope) {
        private val buffer = PulseRingBuffer()
        val sink = PulseSink(buffer)
        private val analyser = AudioAnalyser()
        private val frame = FloatArray(FRAME_SIZE)

        fun next(): AudioPulse {
            analyser.setSampleRate(sink.sampleRate)
            buffer.readLast(frame)
            return analyser.analyse(frame)
        }
    }

    private companion object {
        const val TICK_MS = 33L
        const val STOP_TIMEOUT_MS = 500L
        const val FRAME_SIZE = 1024
    }
}
```

Le test appelle le constructeur `internal` à quatre arguments (`scope`, `tickMs`, `produce`, `sink`) ; Hilt n'utilise que celui marqué `@Inject`.

- [ ] **Step 4 : Vérifier le succès**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.pulse.PulseSourceTest"`
Attendu : 4 tests verts.

- [ ] **Step 5 : Branchement dans `PlaybackService`**

Injection, à côté de `equalizer` (`PlaybackService.kt:92`) :

```kotlin
    @Inject lateinit var pulseSource: fr.synxio.player.playback.pulse.PulseSource
```

Chaîne audio (`PlaybackService.kt:250-252`) :

```kotlin
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    // Le tee copie le PCM pour le pouls de la scène et le laisse passer tel quel.
                    // Ni offload ni tunneling ici : les activer couperait ce tee.
                    .setAudioProcessors(arrayOf(vocalRemover, androidx.media3.exoplayer.audio.TeeAudioProcessor(pulseSource.sink)))
                    .build()
```

Lecture (`PlaybackService.kt:1047`, dans `onIsPlayingChanged`), première ligne du corps :

```kotlin
            pulseSource.setPlaying(isPlaying)
```

- [ ] **Step 6 : `PlayerConnection.pulse`**

Constructeur (`PlayerConnection.kt:98-102`) :

```kotlin
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    @ApplicationScope private val scope: CoroutineScope,
    pulseSource: fr.synxio.player.playback.pulse.PulseSource,
) {
```

Après `val position` (`:109`) :

```kotlin
    /** Pouls de la musique à 30 Hz, muet sans abonné ou en pause. Voir `PulseSource`. */
    val pulse: StateFlow<fr.synxio.player.playback.pulse.AudioPulse> = pulseSource.pulse
```

- [ ] **Step 7 : Compiler**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin`
Attendu : succès. Hilt construit `PulseSource` par son constructeur `@Inject`.

- [ ] **Step 8 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/playback/pulse/PulseSource.kt app/src/test/java/fr/synxio/player/playback/pulse/PulseSourceTest.kt app/src/main/java/fr/synxio/player/playback/PlaybackService.kt app/src/main/java/fr/synxio/player/playback/PlayerConnection.kt
git commit -m "pouls: source partagée à 30 Hz, branchée dans la chaîne audio du service"
```

---

### Task 4 : Suppression du visualiseur à micro

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/playback/EqualizerController.kt:44-48,99-128,212-221`
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt:80,341,755-757`
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/EqualizerViewModel.kt:43,64-66`
- Modify: `app/src/main/java/fr/synxio/player/ui/components/AudioVisualizer.kt`
- Delete: `app/src/main/java/fr/synxio/player/ui/components/VisualizerView.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/EqualizerScreen.kt:132-140,228-238`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt:112,221-239,296-305`
- Modify: `app/src/main/AndroidManifest.xml:31`

**Interfaces:**
- Consumes: `AudioPulse`, `PlayerConnection.pulse`, `PulseSource.pulse`.
- Produces: `AppViewModel.pulse: StateFlow<AudioPulse>`, `EqualizerViewModel.pulse: StateFlow<AudioPulse>`, `AudioVisualizer(pulse: AudioPulse, modifier, barColor)`.

- [ ] **Step 1 : `EqualizerController`**

Retirer : l'import `android.media.audiofx.Visualizer`, les champs `visualizer`, `isVisualizerEnabled`, `_fftFlow`, `fftFlow`, le bloc `visualizer = runCatching { Visualizer(audioSessionId)... }` de `attach`, la fonction `setVisualizerEnabled`, et dans `release()` les lignes `runCatching { visualizer?.release() }`, `visualizer = null`, `_fftFlow.value = ByteArray(0)`.

- [ ] **Step 2 : ViewModels**

`AppViewModel.kt:341` devient :

```kotlin
    val pulse: StateFlow<fr.synxio.player.playback.pulse.AudioPulse> = playerConnection.pulse
```

(`playerConnection` est déjà injecté dans `AppViewModel` ; vérifier le nom exact du champ en tête de classe.) Supprimer `setVisualizerEnabled` (`:755-757`).

`EqualizerViewModel.kt:43` devient `val pulse: StateFlow<AudioPulse> = pulseSource.pulse` avec `pulseSource: PulseSource` ajouté au constructeur ; supprimer `setVisualizerEnabled` (`:64-66`).

- [ ] **Step 3 : `AudioVisualizer`**

Remplacer le fichier entier :

```kotlin
package fr.synxio.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 */
@Composable
fun AudioVisualizer(
    pulse: AudioPulse,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val reduceMotion = LocalReduceMotion.current
    val bands = if (reduceMotion) AudioPulse.SILENT.bands else pulse.bands
    Canvas(modifier = modifier.fillMaxSize()) {
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
```

Supprimer `VisualizerView.kt`.

- [ ] **Step 4 : `EqualizerScreen`**

`:132` → `val pulse by viewModel.pulse.collectAsStateWithLifecycle()`. Supprimer le `DisposableEffect(reduceMotion) { viewModel.setVisualizerEnabled(...) }` (`:137-140`). `:232` → `AudioVisualizer(pulse = pulse, barColor = MaterialTheme.colorScheme.primary, modifier = ...)` avec le même `modifier` qu'avant ; retirer l'import de `VisualizerView` et ajouter celui d'`AudioVisualizer`.

- [ ] **Step 5 : `NowPlayingScreen`**

Supprimer : `recordAudioPermission` (`:221`), `visualizerAsked` et son `rememberSaveable` (`:224`), le `LaunchedEffect(settings.showVisualizer, recordAudioPermission.status)` entier (`:226-236`), l'import `rememberPermissionState` (`:112`) s'il ne sert plus ailleurs dans le fichier (vérifier par recherche ; sinon le garder). `:239` → `val pulse by viewModel.pulse.collectAsStateWithLifecycle()`. `:296-305` :

```kotlin
        if (settings.showVisualizer) {
            AudioVisualizer(
                pulse = pulse,
                barColor = colors.brandEnd.copy(alpha = 0.42f),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.32f)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
```

- [ ] **Step 6 : Manifeste**

Supprimer la ligne `<uses-permission android:name="android.permission.RECORD_AUDIO" />` (`AndroidManifest.xml:31`). Vérifier qu'aucune autre référence à `RECORD_AUDIO` ne subsiste : `grep -rn "RECORD_AUDIO\|audiofx.Visualizer\|fftFlow\|setVisualizerEnabled" app/src/main` doit être vide.

- [ ] **Step 7 : Compiler et tests complets**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin` puis `.\gradlew.bat :app:testDebugUnitTest`
Attendu : succès, tous les tests verts.

- [ ] **Step 8 : Commit**

```bash
git add -A app/src/main
git commit -m "visualiseur: barres depuis le pouls, plus de Visualizer ni de RECORD_AUDIO"
```

---

### Task 5 : `SceneMapper`, `sceneAllowed`, réglage « Scène réactive »

**Files:**
- Create: `app/src/main/java/fr/synxio/player/ui/theme/SceneMapper.kt`
- Modify: `app/src/main/java/fr/synxio/player/core/prefs/SettingsRepository.kt:179,307,425,574`
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/SettingsViewModel.kt:222`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt:490-498`
- Test: `app/src/test/java/fr/synxio/player/ui/theme/SceneMapperTest.kt`

**Interfaces:**
- Consumes: `AudioPulse`.
- Produces: `class SceneParams(haloScale: FloatArray, brightness: Float, shimmer: Float, flash: Float, frozen: Boolean)` avec `SceneParams.REST` ; `fun mapScene(pulse: AudioPulse, silentForMs: Long, previous: SceneParams): SceneParams` ; `data class SceneConditions(reduceMotion, glowEffects, sceneReactive, powerSave, batteryLow, screenOn, playing, surfaceVisible: Boolean)` ; `fun sceneAllowed(c: SceneConditions): Boolean` ; `Settings.sceneReactive`, `SettingsRepository.setSceneReactive(Boolean)`, `SettingsViewModel.setSceneReactive(Boolean)`.

- [ ] **Step 1 : Test**

```kotlin
package fr.synxio.player.ui.theme

import fr.synxio.player.playback.pulse.AudioPulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMapperTest {
    private val loud = AudioPulse(bass = 1f, mid = 1f, treble = 1f, rms = 1f, onset = 1f)

    @Test
    fun `repos - parametres neutres`() {
        val p = mapScene(AudioPulse.SILENT, silentForMs = 0, previous = SceneParams.REST)
        assertEquals(listOf(1f, 1f, 1f), p.haloScale.toList())
        assertEquals(0.85f, p.brightness, 1e-4f)
        assertEquals(0f, p.flash, 1e-4f)
        assertFalse(p.frozen)
    }

    @Test
    fun `plein pouls - bornes atteintes apres lissage`() {
        var p = SceneParams.REST
        repeat(30) { p = mapScene(loud, 0, p) }
        assertEquals(1.25f, p.haloScale[0], 1e-3f)
        assertEquals(1.15f, p.haloScale[1], 1e-3f)
        assertEquals(1.10f, p.haloScale[2], 1e-3f)
        assertEquals(1.15f, p.brightness, 1e-3f)
        assertEquals(1f, p.shimmer, 1e-3f)
        assertEquals(1f, p.flash, 1e-3f)
    }

    @Test
    fun `continuite - pas de saut de plus de 0,2 par image`() {
        val p = mapScene(loud, 0, SceneParams.REST)
        assertTrue(p.haloScale[0] - 1f <= 0.2f)
        assertTrue(p.brightness - 0.85f <= 0.2f)
        assertTrue(p.flash <= 0.6f)
    }

    @Test
    fun `silence d'une seconde - fige`() {
        assertFalse(mapScene(AudioPulse.SILENT, 999, SceneParams.REST).frozen)
        assertTrue(mapScene(AudioPulse.SILENT, 1_000, SceneParams.REST).frozen)
    }

    @Test
    fun `coupures - une seule condition suffit`() {
        val ok = SceneConditions(
            reduceMotion = false, glowEffects = true, sceneReactive = true, powerSave = false,
            batteryLow = false, screenOn = true, playing = true, surfaceVisible = true,
        )
        assertTrue(sceneAllowed(ok))
        assertFalse(sceneAllowed(ok.copy(reduceMotion = true)))
        assertFalse(sceneAllowed(ok.copy(glowEffects = false)))
        assertFalse(sceneAllowed(ok.copy(sceneReactive = false)))
        assertFalse(sceneAllowed(ok.copy(powerSave = true)))
        assertFalse(sceneAllowed(ok.copy(batteryLow = true)))
        assertFalse(sceneAllowed(ok.copy(screenOn = false)))
        assertFalse(sceneAllowed(ok.copy(playing = false)))
        assertFalse(sceneAllowed(ok.copy(surfaceVisible = false)))
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.theme.SceneMapperTest"`
Attendu : échec de compilation.

- [ ] **Step 3 : `SceneMapper.kt`**

```kotlin
package fr.synxio.player.ui.theme

import fr.synxio.player.playback.pulse.AudioPulse

/**
 * Paramètres de la scène à une image : facteurs de rayon des trois halos, luminosité,
 * scintillement du troisième halo, éclat d'attaque, dérive figée.
 */
class SceneParams(
    val haloScale: FloatArray,
    val brightness: Float,
    val shimmer: Float,
    val flash: Float,
    val frozen: Boolean,
) {
    companion object {
        val REST = SceneParams(floatArrayOf(1f, 1f, 1f), 0.85f, 0f, 0f, false)
    }
}

/**
 * Pouls → paramètres de scène, lissés vers l'image précédente (facteur 0,5) pour ne
 * jamais sauter. Pur, appelé dans la phase de dessin.
 */
fun mapScene(pulse: AudioPulse, silentForMs: Long, previous: SceneParams): SceneParams {
    fun ease(from: Float, to: Float) = from + (to - from) * SMOOTHING
    val scale = FloatArray(3)
    scale[0] = ease(previous.haloScale[0], 1f + 0.25f * pulse.bass)
    scale[1] = ease(previous.haloScale[1], 1f + 0.15f * pulse.mid)
    scale[2] = ease(previous.haloScale[2], 1f + 0.10f * pulse.treble)
    return SceneParams(
        haloScale = scale,
        brightness = ease(previous.brightness, 0.85f + 0.30f * pulse.rms),
        shimmer = ease(previous.shimmer, pulse.treble),
        flash = ease(previous.flash, pulse.onset),
        frozen = silentForMs >= FREEZE_AFTER_MS,
    )
}

/** Tout ce qui peut couper la scène réactive. Une seule condition défavorable suffit. */
data class SceneConditions(
    val reduceMotion: Boolean,
    val glowEffects: Boolean,
    val sceneReactive: Boolean,
    val powerSave: Boolean,
    val batteryLow: Boolean,
    val screenOn: Boolean,
    val playing: Boolean,
    val surfaceVisible: Boolean,
)

fun sceneAllowed(c: SceneConditions): Boolean =
    !c.reduceMotion && c.glowEffects && c.sceneReactive && !c.powerSave &&
        !c.batteryLow && c.screenOn && c.playing && c.surfaceVisible

private const val SMOOTHING = 0.5f
private const val FREEZE_AFTER_MS = 1_000L
```

- [ ] **Step 4 : Réglage**

`SettingsRepository.kt` : après `val glowEffects: Boolean = true,` (`:179`) ajouter

```kotlin
    /** L'aurore et la signature respirent sur la musique (lecteur et mini-lecteur). */
    val sceneReactive: Boolean = true,
```

Après `val GLOW_EFFECTS = ...` (`:307`) : `val SCENE_REACTIVE = booleanPreferencesKey("scene_reactive")`. Après `glowEffects = p[Keys.GLOW_EFFECTS] ?: true,` (`:425`) : `sceneReactive = p[Keys.SCENE_REACTIVE] ?: true,`. Après `setGlowEffects` (`:574`) : `suspend fun setSceneReactive(value: Boolean) = put(Keys.SCENE_REACTIVE, value)`.

`SettingsViewModel.kt:222`, ligne suivante : `fun setSceneReactive(value: Boolean) = update { settings.setSceneReactive(value) }`.

`SettingsScreen.kt`, après le `SwitchSetting` « Effets lumineux » (`:498`) :

```kotlin
                            GroupDivider()
                            SwitchSetting(
                                title = "Scène réactive",
                                subtitle = "L'aurore et la signature respirent sur la musique",
                                icon = Icons.Rounded.GraphicEq,
                                checked = settings.sceneReactive,
                                onCheckedChange = settingsViewModel::setSceneReactive
                            )
```

(`Icons.Rounded.GraphicEq` est dans `material-icons-extended`, déjà utilisé par l'écran : vérifier l'import existant, sinon l'ajouter.)

- [ ] **Step 5 : Vérifier le succès**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.theme.SceneMapperTest"` puis `.\gradlew.bat :app:compileDebugKotlin`
Attendu : 5 tests verts, compilation OK.

- [ ] **Step 6 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/theme/SceneMapper.kt app/src/test/java/fr/synxio/player/ui/theme/SceneMapperTest.kt app/src/main/java/fr/synxio/player/core/prefs/SettingsRepository.kt app/src/main/java/fr/synxio/player/ui/viewmodel/SettingsViewModel.kt app/src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt
git commit -m "scène: mapper pur du pouls, règles de coupure et réglage « Scène réactive »"
```

---

### Task 6 : `rememberScenePulse`, `LocalScenePulse`, aurore réactive

**Files:**
- Create: `app/src/main/java/fr/synxio/player/ui/theme/ScenePulse.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/theme/Glass.kt:135-196`
- Modify: `app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt:261-266,289-291` et le bloc qui possède `expansion`

**Interfaces:**
- Consumes: `SceneConditions`, `sceneAllowed`, `mapScene`, `SceneParams`, `AppViewModel.pulse`, `Settings.sceneReactive`, `expansion` (Animatable, 5.7.1), `miniPlayerHidden`.
- Produces: `val LocalScenePulse: ProvidableCompositionLocal<State<AudioPulse>?>` ; `@Composable fun rememberScenePulse(pulse: StateFlow<AudioPulse>, sceneReactive: Boolean, playing: Boolean, surfaceVisible: Boolean): State<AudioPulse>?` ; `AmbientBackdrop(modifier, intensity, drifting, pulse: State<AudioPulse>? = null)`.

- [ ] **Step 1 : `ScenePulse.kt`**

```kotlin
package fr.synxio.player.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.playback.pulse.AudioPulse
import kotlinx.coroutines.flow.StateFlow

/** Pouls disponible pour la scène, ou `null` quand la scène réactive est coupée. */
val LocalScenePulse = staticCompositionLocalOf<State<AudioPulse>?> { null }

/**
 * Applique les règles de coupure (`sceneAllowed`) et ne s'abonne au pouls que si tout
 * est favorable. `collectAsStateWithLifecycle` se désabonne hors `STARTED` : l'écran
 * éteint coupe l'analyse sans code supplémentaire.
 */
@Composable
fun rememberScenePulse(
    pulse: StateFlow<AudioPulse>,
    sceneReactive: Boolean,
    playing: Boolean,
    surfaceVisible: Boolean,
): State<AudioPulse>? {
    val power = rememberPowerState()
    val allowed = sceneAllowed(
        SceneConditions(
            reduceMotion = LocalReduceMotion.current,
            glowEffects = LocalGlowEffects.current,
            sceneReactive = sceneReactive,
            powerSave = power.powerSave,
            batteryLow = power.batteryLow,
            screenOn = true,
            playing = playing,
            surfaceVisible = surfaceVisible,
        ),
    )
    return if (allowed) pulse.collectAsStateWithLifecycle() else null
}

class PowerState { var powerSave by mutableStateOf(false); var batteryLow by mutableStateOf(false) }

/** Économie d'énergie et batterie ≤ 15 %, suivis par diffusion système. */
@Composable
private fun rememberPowerState(): PowerState {
    val context = LocalContext.current
    val state = remember { PowerState() }
    DisposableEffect(context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        state.powerSave = pm.isPowerSaveMode
        state.batteryLow = batteryPercent(context) <= LOW_BATTERY_PERCENT
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> state.powerSave = pm.isPowerSaveMode
                    Intent.ACTION_BATTERY_LOW -> state.batteryLow = true
                    Intent.ACTION_BATTERY_OKAY -> state.batteryLow = batteryPercent(context) <= LOW_BATTERY_PERCENT
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

private fun batteryPercent(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 100
    val value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    return if (value in 0..100) value else 100
}

private const val LOW_BATTERY_PERCENT = 15
```

- [ ] **Step 2 : `AmbientBackdrop` réactif (`Glass.kt`)**

Signature :

```kotlin
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    drifting: Boolean = false,
    pulse: State<AudioPulse>? = null,
) {
```

Après `val animate = drifting && glow && !reduce` et son `LaunchedEffect`, ajouter l'état de scène (hors composition réactive : un simple objet mutable lu dans le dessin) :

```kotlin
    // Lu et écrit dans la phase de dessin seulement : un tick du pouls invalide le
    // dessin, jamais la composition.
    val scene = remember { SceneHolder() }
```

et la classe privée en bas du fichier :

```kotlin
/** Mémoire de la scène entre deux images : lissage, chrono de silence, phase figée. */
private class SceneHolder {
    var params: SceneParams = SceneParams.REST
    var lastLoudNanos: Long = 0L
    var frozenT: Float = 0f
}
```

Dans `drawBehind`, remplacer le calcul de `t` et les trois `drawRect` par :

```kotlin
                val current = pulse?.value
                val now = System.nanoTime()
                if (current != null) {
                    if (current.rms >= 0.02f) scene.lastLoudNanos = now
                    val silentFor = if (scene.lastLoudNanos == 0L) 0L else (now - scene.lastLoudNanos) / 1_000_000
                    scene.params = mapScene(current, silentFor, scene.params)
                } else if (scene.params !== SceneParams.REST) {
                    scene.params = mapScene(AudioPulse.SILENT, 0L, scene.params)
                }
                val p = scene.params
                val live = (phase.value % 1f) * 2f * PI.toFloat()
                val t = if (p.frozen) scene.frozenT else live.also { scene.frozenT = it }
                val w = size.width
                val h = size.height
                val k2 = p.brightness / 0.85f
                drawRect(base)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowA.copy(alpha = (alphaA * k2).coerceAtMost(1f)), Color.Transparent),
                        center = Offset(w * (0.18f + 0.10f * cos(t)), h * (0.04f + 0.04f * sin(t))),
                        radius = size.maxDimension * 0.78f * p.haloScale[0],
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowB.copy(alpha = (alphaB * k2).coerceAtMost(1f)), Color.Transparent),
                        center = Offset(w * (0.98f - 0.10f * sin(t)), h * (0.58f + 0.06f * cos(t))),
                        radius = size.maxDimension * 0.70f * p.haloScale[1],
                    )
                )
                // Scintillement à 4 Hz ; la phase est prise sur les millisecondes modulo 250
                // pour rester dans la précision d'un Float.
                val shimmerPhase = ((now / 1_000_000L) % 250L) / 250f * 2f * PI.toFloat()
                val shimmer = 1f - 0.3f * p.shimmer * (0.5f + 0.5f * sin(shimmerPhase))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowC.copy(alpha = (alphaC * k2 * shimmer).coerceAtMost(1f)), Color.Transparent),
                        center = Offset(w * (0.30f + 0.12f * sin(t + 2f)), h * (1.02f - 0.04f * cos(t))),
                        radius = size.maxDimension * 0.55f * p.haloScale[2],
                    )
                )
                if (p.flash > 0.01f) drawRect(Color.White.copy(alpha = 0.06f * p.flash))
```

Imports à ajouter dans `Glass.kt` : `androidx.compose.runtime.State`, `fr.synxio.player.playback.pulse.AudioPulse`. Sans pouls (`pulse == null`), le rendu est celui d'aujourd'hui : `REST` donne `k2 = 1`, `haloScale = 1`, `shimmer = 1`, `flash = 0`.

- [ ] **Step 3 : Branchement dans `SynxioRoot`**

L'aurore (`SynxioRoot.kt:265`) vit au-dessus de `MainScaffold`, qui possède `expansion` et `miniHidden`. Remonter la visibilité de surface par un état partagé : dans le composable racine, avant le `Box` :

```kotlin
    val sceneReactive by remember(viewModel) {
        viewModel.settings.map { it.sceneReactive }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = true)
    // Lecteur ouvert ou mini-lecteur visible : alimenté par MainScaffold.
    var surfaceVisible by remember { mutableStateOf(false) }
    val scenePulse = rememberScenePulse(
        pulse = viewModel.pulse,
        sceneReactive = sceneReactive,
        playing = isPlaying,
        surfaceVisible = surfaceVisible,
    )
```

puis :

```kotlin
    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(drifting = isPlaying, pulse = scenePulse)
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalScenePulse provides scenePulse,
        ) {
        if (permission.status.isGranted) {
            MainScaffold(viewModel, openPlayerRequest, onSurfaceVisible = { surfaceVisible = it })
        } else {
```

`MainScaffold(viewModel, openPlayerRequest, onSurfaceVisible: (Boolean) -> Unit)` : là où `expansion`, `miniHidden` et `playerState` sont connus, ajouter

```kotlin
    // snapshotFlow : lire `expansion.value` ici en composition recomposerait MainScaffold
    // à chaque image pendant le geste.
    val hasSong = playerState.currentSong != null
    LaunchedEffect(miniHidden, hasSong) {
        snapshotFlow { expansion.value > 0f }
            .distinctUntilChanged()
            .collect { open -> onSurfaceVisible(open || (!miniHidden && hasSong)) }
    }
```

(`playerState.currentSong` : vérifier le nom exact du champ « titre courant » de `PlayerUiState` ; adapter. Import : `androidx.compose.runtime.snapshotFlow`.)

- [ ] **Step 4 : Compiler, tests, lancer sur l'appareil**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin` puis `.\gradlew.bat :app:testDebugUnitTest`, puis `.\gradlew.bat :app:installDebug`.
Vérifier à l'œil : lecture d'un titre avec des basses, lecteur ouvert → les halos respirent ; pause → ils reviennent au repos ; « Réduire les animations » ou « Scène réactive » off → dérive lente seulement.

- [ ] **Step 5 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/theme/ScenePulse.kt app/src/main/java/fr/synxio/player/ui/theme/Glass.kt app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt
git commit -m "scène: l'aurore respire sur le pouls, coupures énergie et visibilité"
```

---

### Task 7 : La signature qui pulse (progression, paroles, mini-lecteur)

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/ui/components/Lumen.kt:553-579`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/nowplaying/Sheets.kt:324-350`
- Modify: `app/src/main/java/fr/synxio/player/ui/components/MiniPlayer.kt:111,148`

**Interfaces:**
- Consumes: `LocalScenePulse`.
- Produces: `Modifier.pulseAlpha(min: Float = 0.9f, span: Float = 0.1f)` dans `Lumen.kt`.

- [ ] **Step 1 : Modificateur commun (`Lumen.kt`, avant `BrandProgressBar`)**

```kotlin
/**
 * Fait respirer l'alpha d'un élément sur le niveau global du pouls (`rms`), lu dans
 * `graphicsLayer` : aucune recomposition par tick. Sans scène réactive, alpha = 1.
 */
@Composable
fun Modifier.pulseAlpha(min: Float = 0.9f, span: Float = 0.1f): Modifier {
    val pulse = LocalScenePulse.current ?: return this
    return graphicsLayer { alpha = (min + span * pulse.value.rms).coerceIn(0f, 1f) }
}
```

Imports : `androidx.compose.ui.graphics.graphicsLayer`, `fr.synxio.player.ui.theme.LocalScenePulse`.

- [ ] **Step 2 : `BrandProgressBar`**

Le `Box` intérieur (rempli par `brush`) reçoit `.pulseAlpha()` :

```kotlin
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .clip(SynxioShape.pill)
                    .pulseAlpha()
                    .background(brush)
            )
```

- [ ] **Step 3 : `LyricRow` (`Sheets.kt`)**

Sur le `Text` de la ligne, quand `active && synced`, ajouter au modificateur existant `.pulseAlpha(min = 0.9f, span = 0.1f)` (uniquement sur la ligne chantée : construire le modificateur avec `if (active && synced) Modifier.pulseAlpha() else Modifier` en tête de chaîne). Même traitement sur la ligne active de `LyricsStage` (`NowPlayingScreen.kt:1207` et suivantes) si elle a son propre `Text` ; sinon rien.

- [ ] **Step 4 : Mini-lecteur**

Après `.glass(SynxioShape.card, strength = 1f, tint = tint)` (`MiniPlayer.kt:148`), ajouter une lueur qui suit les basses :

```kotlin
            .drawWithContent {
                drawContent()
                val p = scenePulse?.value
                if (p != null && tint != null && p.bass > 0.02f) {
                    drawRect(tint.copy(alpha = 0.08f * p.bass))
                }
            }
```

avec, en tête du composable (`:111`), `val scenePulse = LocalScenePulse.current`. Import : `androidx.compose.ui.draw.drawWithContent`.

- [ ] **Step 5 : Compiler, tests, appareil**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :app:testDebugUnitTest`, `.\gradlew.bat :app:installDebug`.
Vérifier : la barre de progression et la ligne de paroles chantée respirent légèrement ; le mini-lecteur s'éclaire sur les basses ; rien ne bouge en pause.

- [ ] **Step 6 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/components/Lumen.kt app/src/main/java/fr/synxio/player/ui/screens/nowplaying/Sheets.kt app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt app/src/main/java/fr/synxio/player/ui/components/MiniPlayer.kt
git commit -m "scène: progression, paroles et mini-lecteur pulsent sur la signature"
```

---

### Task 8 : Notes, graphe, vérification finale

**Files:**
- Create: `docs/release-notes/v5.8.0.md`
- Modify: `docs/KEY_ROTATION.md` (aucune), `README.md` (ligne « Égaliseur & Effets » : rien) — seul le manifeste change de permissions.

- [ ] **Step 1 : Notes de version (début)**

```markdown
## Synxio Player 5.8.0 — Lumen Vivant

### Nouveautés
- **La lumière écoute** : dans le lecteur et derrière le mini-lecteur, l'aurore respire
  sur la musique (les basses dilatent les halos, les aigus scintillent, le silence
  fige), et la barre de progression comme la ligne de paroles chantée pulsent avec elle.
  Réglage « Scène réactive » dans Mouvement et lumière. Coupé automatiquement en
  économie d'énergie, sous 15 % de batterie ou avec « Réduire les animations ».
- **Plus de micro** : le visualiseur lit désormais le son directement dans le lecteur.
  La permission Micro n'est plus demandée ; tu peux la retirer dans les réglages Android.

### Checklist d'appareil
- [ ] Lecteur ouvert, titre avec des basses : halos qui respirent ; pause : retour au calme en moins d'une seconde.
- [ ] Mini-lecteur sur l'accueil : lueur discrète sur les basses.
- [ ] « Scène réactive » off : dérive lente seulement. « Effets lumineux » off : idem.
- [ ] Économie d'énergie activée : scène figée immédiatement.
- [ ] Visualiseur du lecteur et de l'égaliseur : barres vivantes, sans demande de permission.
- [ ] Radio en ligne : barres et halos réagissent aussi.
```

- [ ] **Step 2 : Graphe et tests complets**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest` ; Git Bash : `graphify update .`
Attendu : tout vert ; graphe mis à jour.

- [ ] **Step 3 : Commit**

```bash
git add docs/release-notes/v5.8.0.md graphify-out
git commit -m "docs: notes 5.8.0, section scène réactive"
```

---

## Auto-revue

- **Couverture du spec.** §1.1 tap → Task 2 (`PulseSink` sur `TeeAudioProcessor`, formats 16 bits et float, inactif sinon). §1.2 analyseur → Task 1. §1.3 source `WhileSubscribed`, muet en pause, `PlayerConnection.pulse` → Task 3. §1.4 suppression → Task 4. §1.5 tests → Tasks 1-3. §2.1 `SceneMapper` → Task 5. §2.2 aurore, lecture dans le dessin, condition lecteur/mini → Task 6. §2.3 signature → Task 7. §2.4 coupures et réglage → Tasks 5-6. §2.5 tests → Task 5. §6 réglage « Scène réactive » → Task 5 ; manifeste → Task 4 ; notes → Task 8.
- **Écart assumé.** Le spec parle d'un `AudioTapProcessor` maison ; Media3 fournit `TeeAudioProcessor` + `AudioBufferSink`, qui fait exactement cela sans code de passe-plat à maintenir. Le nom `PulseSink` remplace `AudioTapProcessor` ; le comportement est celui du spec.
- **Placeholders.** Aucun « TBD ». Deux points « vérifier le nom exact » (champ `playerConnection` dans `AppViewModel`, `currentSong` dans `PlayerUiState`) : lectures locales, pas d'inconnue de conception.
- **Cohérence des types.** `AudioPulse` (classe, égalité par contenu) partout ; `PulseSource.pulse`, `PlayerConnection.pulse`, `AppViewModel.pulse`, `EqualizerViewModel.pulse` : même type `StateFlow<AudioPulse>` ; `rememberScenePulse` rend `State<AudioPulse>?`, consommé par `AmbientBackdrop(pulse)` et `LocalScenePulse`.
