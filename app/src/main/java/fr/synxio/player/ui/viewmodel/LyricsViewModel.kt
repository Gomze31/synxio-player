package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.model.Lyrics
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.LyricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsUiState(
    val loading: Boolean = false,
    val lyrics: Lyrics = Lyrics.EMPTY,
    val songId: Long = -1L,
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    /** Recharge seulement si le morceau a changé : évite un appel réseau par recomposition. */
    fun load(song: Song?, force: Boolean = false) {
        if (song == null) {
            _state.value = LyricsUiState()
            return
        }
        if (!force && _state.value.songId == song.id) return

        _state.value = LyricsUiState(loading = true, songId = song.id)
        viewModelScope.launch {
            if (force) repository.clearCache(song)
            val allowOnline = settings.settings.first().lyricsOnlineEnabled
            val lyrics = repository.lyricsFor(song, allowOnline)
            _state.value = LyricsUiState(loading = false, lyrics = lyrics, songId = song.id)
        }
    }

    fun save(song: Song, content: String) = viewModelScope.launch {
        repository.saveManual(song, content)
        load(song, force = true)
    }
}
