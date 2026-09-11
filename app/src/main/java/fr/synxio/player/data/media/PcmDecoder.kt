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

/** Échantillons décodés d'une fenêtre, en PCM 16 bits mono. */
data class PcmWindow(val samples: ShortArray, val sampleRate: Int) {
    // ShortArray n'a pas d'égalité structurelle : sans ces redéfinitions, deux fenêtres
    // au contenu identique seraient considérées différentes.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcmWindow) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRate
}

/**
 * Décodage d'extraits PCM d'un fichier audio.
 *
 * Extrait du mesureur de volume du lot 4, où cette mécanique MediaCodec était enfouie :
 * l'analyse de similarité a besoin exactement des mêmes échantillons, et dupliquer cette
 * boucle aurait condamné les deux copies à diverger.
 *
 * Le multicanal est replié en mono par moyenne des canaux. Pour du timbre comme pour du
 * niveau, la différence gauche/droite n'apporte rien et doublerait le travail.
 */
@Singleton
class PcmDecoder @Inject constructor() {

    /**
     * Décode [windowCount] fenêtres de [windowMs] réparties sur les 80 % centraux.
     *
     * Les 10 % de chaque extrémité sont écartés : intro, outro et fondus y faussent
     * aussi bien la mesure de niveau que la description du timbre.
     */
    suspend fun decodeWindows(
        path: String,
        windowCount: Int,
        windowMs: Long,
    ): List<PcmWindow> = withContext(Dispatchers.Default) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(path) }
            val trackIndex = extractor.audioTrackIndex() ?: return@withContext emptyList()
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            extractor.selectTrack(trackIndex)

            val durationUs = format.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                ?.getLong(MediaFormat.KEY_DURATION) ?: 0L
            if (durationUs <= 0) return@withContext emptyList()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.takeIf { it.containsKey(MediaFormat.KEY_CHANNEL_COUNT) }
                ?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 1

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            starts(durationUs, windowCount, windowMs).map { start ->
                coroutineContext.ensureActive()
                extractor.seekTo(start, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()
                PcmWindow(
                    samples = decodeWindow(extractor, codec, start + windowMs * 1000L, channels),
                    sampleRate = sampleRate,
                )
            }.filter { it.samples.isNotEmpty() }
        } catch (error: Throwable) {
            // Un fichier corrompu ou un codec exotique ne doit pas interrompre l'analyse
            // de toute la bibliothèque : on le signale et on passe au suivant.
            Log.w(TAG, "Décodage impossible pour $path", error)
            emptyList()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun starts(durationUs: Long, windowCount: Int, windowMs: Long): List<Long> {
        val start = (durationUs * 0.1).toLong()
        val end = (durationUs * 0.9).toLong()
        val span = end - start
        if (span <= 0) return listOf(0L)

        val windowUs = windowMs * 1000L
        if (span <= windowUs) return listOf(start)

        val count = windowCount.coerceAtMost((span / windowUs).toInt()).coerceAtLeast(1)
        val step = span / count
        return (0 until count).map { start + it * step }
    }

    private fun decodeWindow(
        extractor: MediaExtractor,
        codec: MediaCodec,
        untilUs: Long,
        channels: Int,
    ): ShortArray {
        val info = MediaCodec.BufferInfo()
        val collected = ArrayList<Short>()
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
                        appendMono(buffer, info.offset, info.size, channels, collected)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                // Rien de prêt et plus rien à envoyer : le décodeur est à sec.
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> outputDone = true
            }
        }

        return ShortArray(collected.size) { collected[it] }
    }

    /** Replie les canaux en mono par moyenne, en PCM 16 bits signé. */
    private fun appendMono(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channels: Int,
        out: ArrayList<Short>,
    ) {
        val shorts = buffer.duplicate()
            .order(ByteOrder.nativeOrder())
            .position(offset)
            .limit(offset + size)
            .let { (it as ByteBuffer).slice().order(ByteOrder.nativeOrder()).asShortBuffer() }

        if (channels <= 1) {
            while (shorts.hasRemaining()) out.add(shorts.get())
            return
        }

        val frame = ShortArray(channels)
        while (shorts.remaining() >= channels) {
            var sum = 0
            for (c in 0 until channels) {
                frame[c] = shorts.get()
                sum += frame[c]
            }
            out.add((sum / channels).toShort())
        }
    }

    private fun MediaExtractor.audioTrackIndex(): Int? {
        for (i in 0 until trackCount) {
            val mime = getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private companion object {
        const val TAG = "PcmDecoder"
        const val TIMEOUT_US = 10_000L
    }
}
