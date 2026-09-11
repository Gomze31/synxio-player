package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.LyricsOffsetDao
import fr.synxio.player.data.db.LyricsOffsetEntity
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
    /** Décalage manuel, en ms. Positif = les paroles défilent plus tôt. */
    val offsetMs: Long = 0L,
) {
    /** Position à laquelle interroger les paroles, décalage appliqué. */
    fun adjustedPosition(positionMs: Long): Long = positionMs + offsetMs

    val offsetLabel: String
        get() = when {
            offsetMs == 0L -> "synchro"
            offsetMs > 0 -> "+%.1f s".format(offsetMs / 1000f)
            else -> "%.1f s".format(offsetMs / 1000f)
        }
}

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
    private val offsetDao: LyricsOffsetDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private var currentSong: Song? = null

    /** Recharge seulement si le morceau a changé : évite un appel réseau par recomposition. */
    fun load(song: Song?, force: Boolean = false) {
        currentSong = song
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
            _state.value = LyricsUiState(
                loading = false,
                lyrics = lyrics,
                songId = song.id,
                offsetMs = offsetDao.offsetFor(song.path) ?: 0L,
            )
        }
    }

    fun save(song: Song, content: String) = viewModelScope.launch {
        repository.saveManual(song, content)
        load(song, force = true)
    }

    /**
     * Ajuste le décalage par pas de [stepMs] et le persiste.
     *
     * L'écriture est immédiate plutôt que différée : l'utilisateur règle le décalage en
     * écoutant, et s'attend à le retrouver la fois suivante sans action de validation.
     */
    fun nudgeOffset(stepMs: Long) {
        val song = currentSong ?: return
        val next = (_state.value.offsetMs + stepMs).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        _state.value = _state.value.copy(offsetMs = next)
        viewModelScope.launch {
            if (next == 0L) {
                offsetDao.remove(song.path)
            } else {
                offsetDao.put(
                    LyricsOffsetEntity(song.path, next, System.currentTimeMillis())
                )
            }
        }
    }

    fun resetOffset() {
        val current = _state.value.offsetMs
        if (current != 0L) nudgeOffset(-current)
    }

    private companion object {
        const val MAX_OFFSET_MS = 30_000L
    }
}
