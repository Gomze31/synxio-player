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
