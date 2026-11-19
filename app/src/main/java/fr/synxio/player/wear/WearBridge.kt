package fr.synxio.player.wear

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.wear.protocol.WearState
import fr.synxio.player.widget.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pousse l'état de lecture vers la montre.
 *
 * Même place que `WidgetUpdater` : appelé par `PlaybackService.refreshWidgetSoon()` avec
 * la photo déjà calculée pour les widgets. Dédoublonne, prépare la pochette seulement
 * quand le titre change, et ne laisse jamais remonter d'exception.
 */
@Singleton
class WearBridge(
    private val publisher: WearStatePublisher,
    private val artPreparer: WearArtPreparer,
    private val volume: () -> Float,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        publisher: WearStatePublisher,
        artPreparer: WearArtPreparer,
    ) : this(publisher, artPreparer, volume = { musicVolume(context) }, clock = System::currentTimeMillis)

    private val mutex = Mutex()
    private var lastState: WearState? = null
    private var lastArtKey: String? = null
    private var lastArt: WearArt = WearArt.NONE

    suspend fun publish(snapshot: WidgetSnapshot, artworkUri: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = clock()
            val artKey = if (snapshot.isEmpty) null else "${snapshot.mediaId}|${artworkUri.orEmpty()}"
            val artChanged = artKey != lastArtKey
            if (artChanged) {
                lastArt = if (artKey == null) WearArt.NONE else safePrepare(artworkUri, artKey)
                lastArtKey = artKey
            }
            val state = snapshot.toWearState(volume(), lastArt)
            if (!WearStateDedup.needsPublish(lastState, state, now)) return@withLock
            send(state, art = if (artChanged) lastArt.art else null, blurred = if (artChanged) lastArt.artBlurred else null)
        }
    }

    /** Après un changement de volume demandé par la montre : réémet le dernier état avec le niveau courant. */
    suspend fun publishVolumeOnly() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = lastState ?: return@withLock
            val now = clock()
            val state = previous.copy(volume = volume(), capturedAtMs = now, positionMs = previous.progressAt(now))
            send(state, art = null, blurred = null)
        }
    }

    private suspend fun send(state: WearState, art: ByteArray?, blurred: ByteArray?) {
        try {
            publisher.publish(state, art, blurred)
            lastState = state
        } catch (e: Exception) {
            Log.w(TAG, "État non publié vers la montre", e)
        }
    }

    private fun safePrepare(artworkUri: String?, key: String): WearArt =
        try {
            artPreparer.prepare(artworkUri, key)
        } catch (e: Exception) {
            Log.w(TAG, "Pochette non préparée pour la montre", e)
            WearArt.NONE
        }

    private fun WidgetSnapshot.toWearState(volume: Float, art: WearArt) = WearState(
        mediaId = mediaId,
        title = title,
        artist = artist,
        isPlaying = isPlaying,
        favorite = favorite,
        positionMs = positionMs,
        durationMs = durationMs,
        capturedAtMs = capturedAtMs,
        live = live,
        hasNext = hasNext,
        shuffle = shuffle,
        repeatMode = repeatMode,
        volume = volume,
        accent = art.accent,
        accentLight = art.accentLight,
    )

    private companion object {
        const val TAG = "SynxioWear"

        fun musicVolume(context: Context): Float {
            val audio = context.getSystemService(AudioManager::class.java) ?: return 0f
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        }
    }
}
