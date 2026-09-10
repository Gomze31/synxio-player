package fr.synxio.player.ui.viewmodel

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.MetadataMatch
import fr.synxio.player.data.repo.MetadataRepository
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.TagEdit
import fr.synxio.player.data.repo.TagEditorRepository
import fr.synxio.player.data.repo.TagWriteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagEditorUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val edit: TagEdit = TagEdit(),
    val saving: Boolean = false,
    val message: String? = null,
    val consentRequest: IntentSender? = null,
    val saved: Boolean = false,
    // --- Recherche en ligne ---
    val searching: Boolean = false,
    val matches: List<MetadataMatch> = emptyList(),
    val searchQuery: String = "",
    val fetchingArtwork: Boolean = false,
)

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    private val music: MusicRepository,
    private val tags: TagEditorRepository,
    private val metadata: MetadataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TagEditorUiState())
    val state: StateFlow<TagEditorUiState> = _state.asStateFlow()

    private var pendingEdit: TagEdit? = null

    fun load(songId: Long) {
        if (_state.value.song?.id == songId) return
        val song = music.songById(songId)
        if (song == null) {
            _state.value = TagEditorUiState(loading = false, message = "Titre introuvable")
            return
        }
        _state.value = TagEditorUiState(loading = true, song = song)
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = false, edit = tags.read(song))
        }
    }

    // --- Recherche de métadonnées en ligne -------------------------------------------

    /** Lance la recherche à partir des tags actuels, en nettoyant le bruit des rips. */
    fun searchOnline() {
        val song = _state.value.song ?: return
        _state.value = _state.value.copy(searching = true, matches = emptyList())
        viewModelScope.launch {
            metadata.findMatches(song)
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        searching = false,
                        matches = results,
                        message = if (results.isEmpty()) "Aucune correspondance trouvée" else null,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        searching = false,
                        message = "Recherche impossible : ${it.message}",
                    )
                }
        }
    }

    /** Recherche libre quand la suggestion automatique tombe à côté. */
    fun searchOnline(query: String) {
        _state.value = _state.value.copy(searching = true, searchQuery = query)
        viewModelScope.launch {
            val expected = (_state.value.song?.durationMs ?: 0L) / 1000
            metadata.search(query, expected)
                .onSuccess { _state.value = _state.value.copy(searching = false, matches = it) }
                .onFailure {
                    _state.value = _state.value.copy(
                        searching = false,
                        message = "Recherche impossible : ${it.message}",
                    )
                }
        }
    }

    /**
     * Remplit le formulaire avec une correspondance.
     *
     * Rien n'est écrit sur le disque à ce stade : l'utilisateur relit et corrige,
     * puis valide avec le bouton Enregistrer.
     */
    fun applyMatch(match: MetadataMatch, withArtwork: Boolean) {
        _state.value = _state.value.copy(fetchingArtwork = withArtwork, matches = emptyList())

        viewModelScope.launch {
            val details = metadata.albumDetails(match.albumId)
            val artwork = if (withArtwork && match.artworkUrl != null) {
                metadata.downloadArtwork(match.artworkUrl)
            } else {
                null
            }

            _state.value = _state.value.copy(
                fetchingArtwork = false,
                edit = _state.value.edit.copy(
                    title = match.title,
                    artist = match.artist,
                    album = match.album,
                    albumArtist = match.artist,
                    genre = details?.genre ?: _state.value.edit.genre,
                    year = details?.year?.toString() ?: _state.value.edit.year,
                    artwork = artwork ?: _state.value.edit.artwork,
                ),
                message = when {
                    withArtwork && artwork == null -> "Infos appliquées, pochette indisponible"
                    withArtwork -> "Infos et pochette prêtes — vérifie puis enregistre"
                    else -> "Infos appliquées — vérifie puis enregistre"
                },
            )
        }
    }

    fun dismissMatches() {
        _state.value = _state.value.copy(matches = emptyList())
    }

    fun update(transform: (TagEdit) -> TagEdit) {
        _state.value = _state.value.copy(edit = transform(_state.value.edit))
    }

    fun save() {
        val song = _state.value.song ?: return
        val edit = _state.value.edit
        pendingEdit = edit
        _state.value = _state.value.copy(saving = true, message = null)

        viewModelScope.launch {
            when (val result = tags.write(song, edit)) {
                is TagWriteResult.Success -> {
                    music.refresh()
                    _state.value = _state.value.copy(
                        saving = false,
                        saved = true,
                        message = "Tags enregistrés",
                    )
                }

                is TagWriteResult.NeedsUserConsent -> _state.value = _state.value.copy(
                    saving = false,
                    consentRequest = result.intentSender,
                    message = "Android demande ton autorisation pour modifier ce fichier",
                )

                is TagWriteResult.Failure -> _state.value = _state.value.copy(
                    saving = false,
                    message = result.message,
                )
            }
        }
    }

    /** Rejoue l'écriture une fois l'autorisation système accordée. */
    fun onConsentResult(granted: Boolean) {
        _state.value = _state.value.copy(consentRequest = null)
        if (granted) save() else {
            _state.value = _state.value.copy(message = "Modification refusée")
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
