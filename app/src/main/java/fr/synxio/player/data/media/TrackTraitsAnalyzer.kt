package fr.synxio.player.data.media

import fr.synxio.player.data.media.dsp.DecodedTrack
import fr.synxio.player.data.media.dsp.analyseTraits
import fr.synxio.player.data.model.TrackTraits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empreinte sonore 2 : décode tête (10 s), trois fenêtres de 12 s au centre et queue (10 s),
 * puis confie le signal au DSP pur. Environ 46 s d'audio par titre.
 */
@Singleton
class TrackTraitsAnalyzer @Inject constructor(private val decoder: PcmDecoder) {

    suspend fun analyse(path: String, durationMs: Long): TrackTraits? = withContext(Dispatchers.Default) {
        val duration = durationMs.takeIf { it > 0 } ?: decoder.durationMs(path) ?: return@withContext null
        val middle = decoder.decodeWindows(path, WINDOW_COUNT, WINDOW_MS)
        if (middle.isEmpty()) return@withContext null
        val sampleRate = middle.first().sampleRate
        val head = decoder.decodeRange(path, 0L, minOf(EDGE_MS, duration))
        val tailStart = (duration - EDGE_MS).coerceAtLeast(0L)
        val tail = decoder.decodeRange(path, tailStart, duration)
        analyseTraits(
            DecodedTrack(
                sampleRate = sampleRate,
                durationMs = duration,
                head = head?.samples ?: ShortArray(0),
                middle = middle.map { it.samples },
                tail = tail?.samples ?: ShortArray(0),
                tailStartMs = tailStart,
            )
        )
    }

    private companion object {
        const val WINDOW_COUNT = 3
        const val WINDOW_MS = 12_000L
        const val EDGE_MS = 10_000L
    }
}
