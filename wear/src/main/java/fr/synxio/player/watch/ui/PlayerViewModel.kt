package fr.synxio.player.watch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.watch.data.Connectivity
import fr.synxio.player.watch.data.PhoneLink
import fr.synxio.player.watch.data.WearStateStore
import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearPaths
import fr.synxio.player.wear.protocol.WearState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sign

/**
 * État de l'écran lecteur.
 *
 * Les commandes partent tout de suite et l'écran bascule sans attendre ; le prochain état
 * du téléphone (ou [OPTIMISM_MS]) remet les compteurs. Les crans de couronne sont
 * groupés par [CROWN_WINDOW_MS] : un pas envoyé par rafale, jamais une pluie de messages.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val store: WearStateStore,
    private val link: PhoneLink,
) : ViewModel() {

    /** Horloge murale, remplaçable par les tests avant tout appel. */
    internal var clock: () -> Long = System::currentTimeMillis

    /** Bascules locales en attente de confirmation. */
    private data class Pending(
        val playing: Boolean? = null,
        val favorite: Boolean? = null,
        val volume: Float? = null,
        val volumeVisible: Boolean = false,
        val connectingSince: Long? = null,
    )

    private val pending = MutableStateFlow(Pending())
    private var optimismJob: Job? = null
    private var volumeHideJob: Job? = null
    private var connectingJob: Job? = null
    private var crownJob: Job? = null
    private var crownSteps = 0

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val art: StateFlow<ByteArray?> = store.stored.map { it.art }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.current().art)
    val artBlurred: StateFlow<ByteArray?> = store.stored.map { it.artBlurred }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.current().artBlurred)

    val uiState: StateFlow<PlayerUiState> = combine(store.stored, link.connectivity, pending) { stored, connectivity, p ->
        build(stored.state, connectivity, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState.Idle)

    init {
        // Tout nouvel état du téléphone clôt l'optimisme et la connexion en cours.
        viewModelScope.launch {
            var last = store.current().state
            store.stored.collect { stored ->
                if (stored.state != last) {
                    last = stored.state
                    optimismJob?.cancel()
                    val connecting = pending.value.connectingSince
                    val arrived = stored.state?.let { !it.isEmpty && connecting != null && it.capturedAtMs >= connecting } == true
                    pending.update {
                        it.copy(
                            playing = null, favorite = null, volume = null,
                            connectingSince = if (arrived) null else it.connectingSince,
                        )
                    }
                    if (arrived) connectingJob?.cancel()
                }
            }
        }
    }

    private fun build(state: WearState?, connectivity: Connectivity, p: Pending): PlayerUiState {
        when (connectivity) {
            Connectivity.PLAY_SERVICES_MISSING -> return PlayerUiState.PlayServicesMissing
            Connectivity.PHONE_UNREACHABLE -> return PlayerUiState.PhoneUnreachable
            Connectivity.UNKNOWN, Connectivity.PHONE_REACHABLE -> Unit
        }
        if (p.connectingSince != null) return PlayerUiState.Connecting
        if (state == null || state.isEmpty) return PlayerUiState.Idle
        if (state.protocolVersion != WearPaths.PROTOCOL_VERSION) {
            return PlayerUiState.VersionMismatch(phoneNewer = state.protocolVersion > WearPaths.PROTOCOL_VERSION)
        }
        val now = clock()
        return PlayerUiState.Playing(
            state = state,
            isPlaying = p.playing ?: (state.isPlaying && !state.isStaleAt(now)),
            favorite = p.favorite ?: state.favorite,
            volume = p.volume ?: state.volume,
            volumeVisible = p.volumeVisible,
        )
    }

    fun onForeground() {
        viewModelScope.launch { link.refresh() }
    }

    fun playPause() {
        val current = uiState.value as? PlayerUiState.Playing
        if (current != null) optimistic { it.copy(playing = !current.isPlaying) }
        send(WearCommand.PlayPause)
    }

    fun next() = send(WearCommand.Next)
    fun previous() = send(WearCommand.Previous)

    fun toggleFavorite() {
        val current = uiState.value as? PlayerUiState.Playing ?: return
        optimistic { it.copy(favorite = !current.favorite) }
        send(WearCommand.ToggleFavorite)
    }

    /** Un cran de couronne : +1 ou -1. */
    fun volumeStep(delta: Int) {
        crownSteps += delta
        if (crownJob?.isActive == true) return
        crownJob = viewModelScope.launch {
            delay(CROWN_WINDOW_MS)
            val net = crownSteps.sign
            crownSteps = 0
            if (net == 0) return@launch
            val current = uiState.value as? PlayerUiState.Playing
            if (current != null) {
                val level = (current.volume + net * VOLUME_STEP).coerceIn(0f, 1f)
                optimistic { it.copy(volume = level, volumeVisible = true) }
                volumeHideJob?.cancel()
                volumeHideJob = viewModelScope.launch {
                    delay(VOLUME_VISIBLE_MS)
                    pending.update { it.copy(volumeVisible = false) }
                }
            }
            send(if (net > 0) WearCommand.VolumeUp else WearCommand.VolumeDown)
        }
    }

    fun start(mediaId: String) {
        val since = clock()
        pending.update { it.copy(connectingSince = since) }
        connectingJob?.cancel()
        connectingJob = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (pending.value.connectingSince == since) {
                pending.update { it.copy(connectingSince = null) }
                _events.tryEmit(UNREACHABLE)
            }
        }
        send(WearCommand.Start(mediaId))
    }

    private fun optimistic(change: (Pending) -> Pending) {
        pending.update(change)
        optimismJob?.cancel()
        optimismJob = viewModelScope.launch {
            delay(OPTIMISM_MS)
            pending.update { it.copy(playing = null, favorite = null, volume = null) }
        }
    }

    private fun send(command: WearCommand) {
        viewModelScope.launch {
            link.send(command).onFailure {
                pending.update { it.copy(playing = null, favorite = null, volume = null, connectingSince = null) }
                connectingJob?.cancel()
                _events.tryEmit(UNREACHABLE)
            }
        }
    }

    private companion object {
        const val OPTIMISM_MS = 3_000L
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val VOLUME_VISIBLE_MS = 1_500L
        const val CROWN_WINDOW_MS = 80L
        const val VOLUME_STEP = 1f / 15f
        const val UNREACHABLE = "Téléphone injoignable"
    }
}
