package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.LyricsOffsetDao
import fr.synxio.player.data.db.LyricsOffsetEntity
import fr.synxio.player.data.model.Lyrics
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.LyricsOutcome
import fr.synxio.player.data.repo.LyricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import fr.synxio.player.data.repo.TranslationRepository

data class LyricsUiState(
    val loading: Boolean = false,
    val isTranslating: Boolean = false,
    val lyrics: Lyrics = Lyrics.EMPTY,
    val originalLyrics: Lyrics = Lyrics.EMPTY,
    val songId: Long = -1L,
    /** Décalage manuel, en ms. Positif = les paroles défilent plus tôt. */
    val offsetMs: Long = 0L,
    val outcome: LyricsOutcome = LyricsOutcome.FOUND,
    val targetLanguage: String? = null,
) {
    /**
     * Message affiché quand il n'y a rien à montrer.
     *
     * Chaque cas oriente vers une action différente : activer un réglage, corriger les
     * tags, ou changer de réseau. Un texte unique les enverrait tous au même endroit.
     */
    val emptyMessage: String
        get() = when (outcome) {
            LyricsOutcome.ONLINE_DISABLED ->
                "Aucun fichier .lrc à côté du morceau, et aucune parole dans ses tags.\n\n" +
                    "Active « Chercher les paroles en ligne » dans les réglages pour " +
                    "interroger LRCLIB."

            LyricsOutcome.NOT_FOUND ->
                "LRCLIB ne connaît pas ce morceau.\n\n" +
                    "Ses tags sont peut-être trop approximatifs pour le retrouver : " +
                    "« Réparer les tags » dans les réglages améliore souvent la recherche."

            LyricsOutcome.UNREACHABLE ->
                "LRCLIB est injoignable depuis ce réseau.\n\n" +
                    "Certains réseaux d'entreprise ou d'école filtrent le domaine. " +
                    "Réessaie en données mobiles."

            LyricsOutcome.FOUND ->
                "Aucune parole trouvée pour ce titre."
        }

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
    private val translationRepository: TranslationRepository,
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
            val currentSettings = settings.settings.first()
            val allowOnline = currentSettings.lyricsOnlineEnabled
            val result = repository.lyricsFor(song, allowOnline)
            
            _state.value = LyricsUiState(
                loading = false,
                lyrics = result.lyrics,
                originalLyrics = result.lyrics,
                songId = song.id,
                offsetMs = offsetDao.offsetFor(song.path) ?: 0L,
                outcome = result.outcome,
            )
            
            if (currentSettings.autoTranslateLyrics && result.lyrics.lines.isNotEmpty()) {
                val locale = java.util.Locale.getDefault().language
                val targetLang = when(locale) {
                    "fr" -> com.google.mlkit.nl.translate.TranslateLanguage.FRENCH
                    "es" -> com.google.mlkit.nl.translate.TranslateLanguage.SPANISH
                    "de" -> com.google.mlkit.nl.translate.TranslateLanguage.GERMAN
                    "it" -> com.google.mlkit.nl.translate.TranslateLanguage.ITALIAN
                    else -> null
                }
                if (targetLang != null) {
                    translateTo(targetLang)
                }
            }
        }
    }

    fun translateTo(targetLang: String, sourceLang: String = com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH) {
        val song = currentSong ?: return
        val currentOriginal = _state.value.originalLyrics
        if (currentOriginal.lines.isEmpty()) return

        _state.value = _state.value.copy(isTranslating = true, targetLanguage = targetLang)
        
        viewModelScope.launch {
            val result = translationRepository.translateLyrics(
                lyrics = currentOriginal,
                sourceLang = sourceLang,
                targetLang = targetLang,
                cacheKey = song.path
            )
            
            result.onSuccess { translated ->
                _state.value = _state.value.copy(
                    isTranslating = false,
                    lyrics = translated
                )
            }.onFailure {
                // En cas d'erreur, on restaure l'original
                _state.value = _state.value.copy(
                    isTranslating = false,
                    lyrics = currentOriginal,
                    targetLanguage = null
                )
            }
        }
    }
    
    fun revertTranslation() {
        _state.value = _state.value.copy(
            lyrics = _state.value.originalLyrics,
            targetLanguage = null
        )
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
