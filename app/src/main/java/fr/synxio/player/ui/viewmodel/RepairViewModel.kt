package fr.synxio.player.ui.viewmodel

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.repo.LibraryRepairRepository
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.RepairProgress
import fr.synxio.player.data.repo.RepairProposal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepairUiState(
    val progress: RepairProgress = RepairProgress(),
    val proposals: List<RepairProposal> = emptyList(),
    val analysed: Boolean = false,
    val applying: Boolean = false,
    val minConfidence: Int = 80,
    val withArtwork: Boolean = true,
    val consentRequest: IntentSender? = null,
    val message: String? = null,
) {
    val selectedCount: Int get() = proposals.count { it.selected }
}

@HiltViewModel
class RepairViewModel @Inject constructor(
    private val repair: LibraryRepairRepository,
    private val music: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RepairUiState())
    val state: StateFlow<RepairUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Analyse toute la bibliothèque : une requête réseau par morceau suspect. */
    fun analyse() {
        job?.cancel()
        val songs = music.library.value.songs
        _state.value = _state.value.copy(
            progress = RepairProgress(0, songs.size, running = true),
            proposals = emptyList(),
            analysed = false,
        )

        job = viewModelScope.launch {
            val proposals = repair.analyse(songs, _state.value.minConfidence) { done, total ->
                _state.value = _state.value.copy(
                    progress = RepairProgress(done, total, running = true)
                )
            }
            _state.value = _state.value.copy(
                proposals = proposals,
                analysed = true,
                progress = RepairProgress(running = false),
                message = if (proposals.isEmpty()) {
                    "Aucune correction proposée — tes tags sont déjà corrects, ou aucune correspondance fiable n'a été trouvée."
                } else null,
            )
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(
            progress = RepairProgress(running = false),
            applying = false,
        )
    }

    fun toggle(proposal: RepairProposal) {
        _state.value = _state.value.copy(
            proposals = _state.value.proposals.map {
                if (it.song.id == proposal.song.id) it.copy(selected = !it.selected) else it
            }
        )
    }

    fun setAllSelected(selected: Boolean) {
        _state.value = _state.value.copy(
            proposals = _state.value.proposals.map { it.copy(selected = selected) }
        )
    }

    fun setMinConfidence(value: Int) {
        _state.value = _state.value.copy(minConfidence = value)
    }

    fun setWithArtwork(value: Boolean) {
        _state.value = _state.value.copy(withArtwork = value)
    }

    /**
     * Demande l'autorisation d'écriture groupée avant d'appliquer.
     * Android 11+ exige un consentement, mais un seul suffit pour toute la liste.
     */
    fun requestApply() {
        val selected = _state.value.proposals.filter { it.selected }
        if (selected.isEmpty()) return

        val sender = repair.writeRequestFor(selected.map { it.song })
        if (sender != null) {
            _state.value = _state.value.copy(consentRequest = sender)
        } else {
            applyNow()
        }
    }

    fun onConsentResult(granted: Boolean) {
        _state.value = _state.value.copy(consentRequest = null)
        if (granted) applyNow()
        else _state.value = _state.value.copy(message = "Autorisation refusée, rien n'a été modifié")
    }

    private fun applyNow() {
        val selected = _state.value.proposals.filter { it.selected }
        _state.value = _state.value.copy(
            applying = true,
            progress = RepairProgress(0, selected.size, running = true),
        )

        job = viewModelScope.launch {
            val result = repair.apply(selected, _state.value.withArtwork) { done, total ->
                _state.value = _state.value.copy(
                    progress = RepairProgress(done, total, running = true)
                )
            }
            music.refresh()

            val ok = result.ok
            val failed = result.failed
            _state.value = _state.value.copy(
                applying = false,
                // Si rien n'a abouti, on garde la liste à l'écran : la vider obligerait à
                // relancer une analyse de plusieurs minutes pour simplement réessayer.
                analysed = ok > 0 || failed == 0,
                proposals = if (ok > 0 || failed == 0) emptyList() else _state.value.proposals,
                progress = RepairProgress(running = false),
                message = buildString {
                    append("$ok titre${if (ok > 1) "s" else ""} corrigé${if (ok > 1) "s" else ""}")
                    if (failed > 0) {
                        append(" · $failed échec${if (failed > 1) "s" else ""}")
                        result.firstError?.let { append(" : $it") }
                    }
                },
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
