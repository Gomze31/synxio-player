package fr.synxio.player.watch.data

import fr.synxio.player.wear.protocol.WearState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Dernier état reçu du téléphone et ses images ; `state == null` tant que rien n'est arrivé. */
data class StoredState(
    val state: WearState?,
    val art: ByteArray?,
    val artBlurred: ByteArray?,
)

/**
 * Source de vérité de la montre.
 *
 * Rempli par `WearStateListener` (même si l'appli est fermée) et relu par l'interface,
 * la tuile et la complication, qui ne touchent jamais au Data Layer elles-mêmes.
 * Persisté en fichiers : un processus tout juste créé pour dessiner la tuile retrouve
 * l'état sans attendre Play Services.
 */
class WearStateStore(private val dir: File) {

    private val _stored = MutableStateFlow(StoredState(null, null, null))
    val stored: StateFlow<StoredState> = _stored.asStateFlow()

    private val stateFile get() = File(dir, "state.json")
    private val artFile get() = File(dir, "art.jpg")
    private val blurredFile get() = File(dir, "art_blurred.jpg")

    @Synchronized
    fun load() {
        val state = stateFile.takeIf { it.exists() }?.readText()?.let(WearState::fromJson)
        _stored.value = StoredState(
            state = state,
            art = artFile.takeIf { state != null && it.exists() }?.readBytes(),
            artBlurred = blurredFile.takeIf { state != null && it.exists() }?.readBytes(),
        )
    }

    /**
     * Enregistre [state]. Images `null` : conservées si le titre n'a pas changé (le
     * téléphone ne les renvoie pas à chaque transition), effacées sinon.
     */
    @Synchronized
    fun save(state: WearState, art: ByteArray?, artBlurred: ByteArray?) {
        dir.mkdirs()
        val previous = _stored.value
        val sameTrack = !state.isEmpty && previous.state?.mediaId == state.mediaId
        val keptArt = art ?: previous.art?.takeIf { sameTrack }
        val keptBlurred = artBlurred ?: previous.artBlurred?.takeIf { sameTrack }

        stateFile.writeText(state.toJson())
        if (keptArt != null) artFile.writeBytes(keptArt) else artFile.delete()
        if (keptBlurred != null) blurredFile.writeBytes(keptBlurred) else blurredFile.delete()
        _stored.value = StoredState(state, keptArt, keptBlurred)
    }

    fun current(): StoredState = _stored.value
}
