package fr.synxio.player.ui.viewmodel

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.DuplicateGroup
import fr.synxio.player.data.repo.DuplicateRepository
import fr.synxio.player.data.repo.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuplicatesUiState(
    val scanning: Boolean = false,
    val scanned: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList(),
    val deleteRequest: IntentSender? = null,
    val message: String? = null,
) {
    val selectedSongs: List<Song>
        get() = groups.flatMap { group -> group.all.filter { it.id in group.selectedIds } }

    val selectedCount: Int get() = selectedSongs.size
    val reclaimedBytes: Long get() = groups.sumOf { it.reclaimedBytes }
}

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val duplicates: DuplicateRepository,
    private val music: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DuplicatesUiState())
    val state: StateFlow<DuplicatesUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun scan() {
        job?.cancel()
        _state.value = DuplicatesUiState(scanning = true)
        job = viewModelScope.launch {
            val groups = duplicates.findDuplicates(music.library.value.songs)
            _state.value = DuplicatesUiState(scanned = true, groups = groups)
        }
    }

    /**
     * Coche ou décoche une copie.
     *
     * On refuse de tout décocher *et* de tout cocher dans un même groupe : supprimer
     * l'intégralité d'un groupe ferait disparaître le morceau de la bibliothèque, ce qui
     * n'est jamais l'intention quand on fait la chasse aux doublons.
     */
    fun toggle(group: DuplicateGroup, song: Song) {
        _state.value = _state.value.copy(
            groups = _state.value.groups.map { candidate ->
                if (candidate.keeper.id != group.keeper.id) return@map candidate
                val next = if (song.id in candidate.selectedIds) {
                    candidate.selectedIds - song.id
                } else {
                    candidate.selectedIds + song.id
                }
                if (next.size == candidate.all.size) candidate else candidate.copy(selectedIds = next)
            }
        )
    }

    fun requestDelete() {
        val songs = _state.value.selectedSongs
        if (songs.isEmpty()) return
        val sender = duplicates.deleteRequestFor(songs)
        if (sender != null) {
            _state.value = _state.value.copy(deleteRequest = sender)
        } else {
            _state.value = _state.value.copy(
                message = "Suppression groupée indisponible sur cette version d'Android"
            )
        }
    }

    fun onDeleteResult(granted: Boolean) {
        _state.value = _state.value.copy(deleteRequest = null)
        if (!granted) {
            _state.value = _state.value.copy(message = "Suppression annulée")
            return
        }
        val count = _state.value.selectedCount
        viewModelScope.launch {
            music.refresh()
            _state.value = DuplicatesUiState(
                message = "$count fichier${if (count > 1) "s" else ""} mis à la corbeille",
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
