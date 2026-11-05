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
