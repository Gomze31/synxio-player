package fr.synxio.player.playback

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fondu enchaîné « à un lecteur » : fondu sortant en fin de piste, fondu entrant sur
 * la suivante.
 *
 * Un vrai crossfade avec chevauchement demanderait deux instances d'ExoPlayer mixées ;
 * ce compromis évite le doublement de la conso mémoire/CPU tout en supprimant les
 * coupures brutales. Réglé à 0 ms, le lecteur reste en gapless natif.
 */
class FadeController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) {

    /** Durée du fondu, 0 = désactivé (gapless natif conservé). */
    var durationMs: Int = 0
        set(value) {
            field = value.coerceIn(0, MAX_FADE_MS)
            if (field == 0) {
                stop()
                player.volume = 1f
            } else {
                start()
            }
        }

    private var job: Job? = null

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (durationMs == 0) break
                applyVolumeForPosition()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Remet le volume à plein : appelé sur seek et changement de piste manuel. */
    fun resetVolume() {
        if (durationMs > 0) player.volume = 0f else player.volume = 1f
    }

    private fun applyVolumeForPosition() {
        if (!player.isPlaying) return
        val duration = player.duration
        if (duration <= 0) {
            player.volume = 1f
            return
        }

        val position = player.currentPosition
        val fade = durationMs.toLong()
        val remaining = duration - position

        val volume = when {
            // Fondu entrant : on ne le fait que s'il reste largement de quoi jouer.
            position < fade && duration > fade * 2 -> position.toFloat() / fade
            // Fondu sortant, uniquement s'il y a une piste suivante à enchaîner.
            remaining < fade && player.hasNextMediaItem() -> remaining.toFloat() / fade
            else -> 1f
        }

        player.volume = volume.coerceIn(0f, 1f)
    }

    private companion object {
        const val TICK_MS = 100L
        const val MAX_FADE_MS = 12_000
    }
}
