package fr.synxio.player.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Mesure le niveau sonore moyen d'un fichier audio, en dBFS.
 *
 * ## Pourquoi un échantillonnage et pas le fichier entier
 *
 * Décoder intégralement 328 morceaux prendrait des dizaines de minutes. On décode donc
 * [WINDOW_COUNT] fenêtres de [WINDOW_MS] réparties dans la piste, en sautant le tout
 * début et la toute fin — là où se trouvent les intros et les fondus, qui tireraient la
 * moyenne vers le bas.
 *
 * ## Ce que la mesure est, et n'est pas
 *
 * C'est un RMS, pas une mesure EBU R128 : pas de pondération K, pas de porte relative.
 * Pour aligner le volume de fichiers rippés entre eux, c'est suffisant et
 * considérablement moins coûteux. Pour un mastering, ça ne le serait pas.
 */
@Singleton
class LoudnessAnalyzer @Inject constructor() {

    /** Niveau moyen en dBFS (valeur négative), ou null si le fichier est illisible. */
    suspend fun analyse(path: String): Float? = withContext(Dispatchers.Default) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(path) }
            val trackIndex = extractor.audioTrackIndex() ?: return@withContext null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            extractor.selectTrack(trackIndex)

            val durationUs = format.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                ?.getLong(MediaFormat.KEY_DURATION) ?: 0L
            if (durationUs <= 0) return@withContext null

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            var sumSquares = 0.0
            var sampleCount = 0L

            for (window in windowsFor(durationUs)) {
                coroutineContext.ensureActive()
                extractor.seekTo(window, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()
                val (sum, count) = decodeWindow(extractor, codec, window + WINDOW_MS * 1000L)
                sumSquares += sum
                sampleCount += count
            }

            if (sampleCount == 0L) return@withContext null
            val rms = sqrt(sumSquares / sampleCount)
            if (rms <= 0.0) return@withContext SILENCE_DBFS

            (20.0 * log10(rms)).toFloat().coerceIn(SILENCE_DBFS, 0f)
        } catch (error: Throwable) {
            // Un fichier corrompu ou un codec exotique ne doit pas interrompre l'analyse
            // de toute la bibliothèque : on le signale et on passe au suivant.
            Log.w(TAG, "Analyse impossible pour $path", error)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    /**
     * Positions de début des fenêtres, réparties sur les 80 % centraux de la piste.
     *
     * Les 10 % de chaque extrémité sont écartés : intro, outro et fondus y faussent la
     * mesure sur à peu près tous les morceaux.
     */
    private fun windowsFor(durationUs: Long): List<Long> {
        val start = (durationUs * 0.1).toLong()
        val end = (durationUs * 0.9).toLong()
        val span = end - start
        if (span <= 0) return listOf(0L)

        val windowUs = WINDOW_MS * 1000L
        if (span <= windowUs) return listOf(start)

        val count = WINDOW_COUNT.coerceAtMost((span / windowUs).toInt()).coerceAtLeast(1)
        val step = span / count
        return (0 until count).map { start + it * step }
    }

    /** Décode jusqu'à [untilUs] et accumule la somme des carrés des échantillons. */
    private fun decodeWindow(
        extractor: MediaExtractor,
        codec: MediaCodec,
        untilUs: Long,
    ): Pair<Double, Long> {
        val info = MediaCodec.BufferInfo()
        var sumSquares = 0.0
        var count = 0L
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)
                    val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                    if (size < 0 || extractor.sampleTime > untilUs) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        val (sum, n) = accumulate(buffer, info.offset, info.size)
                        sumSquares += sum
                        count += n
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                // Rien de prêt et plus rien à envoyer : le décodeur est à sec.
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> outputDone = true
            }
        }

        return sumSquares to count
    }

    /**
     * Somme des carrés d'un buffer PCM 16 bits signé.
     *
     * Les décodeurs Android renvoient du PCM 16 bits pour les formats courants (MP3,
     * AAC, FLAC, Vorbis, Opus). Un décodeur qui sortirait du flottant produirait des
     * valeurs aberrantes ici — d'où la normalisation par [PCM16_FULL_SCALE], qui borne
     * mécaniquement le résultat dans [-1, 1] pour du 16 bits.
     */
    private fun accumulate(buffer: ByteBuffer, offset: Int, size: Int): Pair<Double, Long> {
        val shorts = buffer.duplicate()
            .order(ByteOrder.nativeOrder())
            .position(offset)
            .limit(offset + size)
            .let { (it as ByteBuffer).slice().order(ByteOrder.nativeOrder()).asShortBuffer() }

        var sum = 0.0
        var count = 0L
        while (shorts.hasRemaining()) {
            val value = shorts.get() / PCM16_FULL_SCALE
            sum += value * value
            count++
        }
        return sum to count
    }

    private fun MediaExtractor.audioTrackIndex(): Int? {
        for (i in 0 until trackCount) {
            val mime = getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private companion object {
        const val TAG = "LoudnessAnalyzer"
        const val WINDOW_COUNT = 6
        const val WINDOW_MS = 2_000L
        const val TIMEOUT_US = 10_000L
        const val PCM16_FULL_SCALE = 32768.0
        const val SILENCE_DBFS = -70f
    }
}
