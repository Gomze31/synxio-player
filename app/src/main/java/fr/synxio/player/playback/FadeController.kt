package fr.synxio.player.playback

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Seul propriétaire du volume d'ExoPlayer.
 *
 * Deux effets se superposent et devaient être composés en un seul point : le fondu
 * enchaîné (enveloppe qui varie avec la position) et la normalisation du volume (gain
 * constant sur toute la piste). Les laisser écrire chacun `player.volume` faisait que le
 * dernier à s'exécuter effaçait l'autre — le fondu ramenait le volume à 1 et annulait la
 * normalisation à chaque tick.
 *
 * Le fondu est « à un lecteur » : fondu sortant en fin de piste, fondu entrant sur la
 * suivante. Un vrai crossfade avec chevauchement demanderait deux instances d'ExoPlayer
 * mixées ; ce compromis évite le doublement de la conso mémoire/CPU tout en supprimant
 * les coupures brutales. Réglé à 0 ms, le lecteur reste en gapless natif.
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
                envelope = 1f
                applyVolume()
            } else {
                start()
            }
        }

    /** Gain de normalisation de la piste courante, entre 0 et 1. */
    var normalizationGain: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolume()
        }

    /** Position dans le fondu, indépendante du gain de normalisation. */
    private var envelope: Float = 1f

    private var job: Job? = null

    private fun applyVolume() {
        player.volume = (envelope * normalizationGain).coerceIn(0f, 1f)
    }

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

    /** Réinitialise l'enveloppe : appelé sur seek et changement de piste manuel. */
    fun resetVolume() {
        envelope = if (durationMs > 0) 0f else 1f
        applyVolume()
    }

    private fun applyVolumeForPosition() {
        if (!player.isPlaying) return
        val duration = player.duration
        if (duration <= 0) {
            envelope = 1f
            applyVolume()
            return
        }

        val position = player.currentPosition
        val fade = durationMs.toLong()
        val remaining = duration - position

        envelope = when {
            // Fondu entrant : on ne le fait que s'il reste largement de quoi jouer.
            position < fade && duration > fade * 2 -> position.toFloat() / fade
            // Fondu sortant, uniquement s'il y a une piste suivante à enchaîner.
            remaining < fade && player.hasNextMediaItem() -> remaining.toFloat() / fade
            else -> 1f
        }.coerceIn(0f, 1f)

        applyVolume()
    }

    private companion object {
        const val TICK_MS = 100L
        const val MAX_FADE_MS = 12_000
    }
}
