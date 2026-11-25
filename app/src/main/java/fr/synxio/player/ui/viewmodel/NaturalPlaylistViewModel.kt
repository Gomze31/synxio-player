package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.Clause
import fr.synxio.player.data.model.NaturalPlaylistParser
import fr.synxio.player.data.model.RuleField
import fr.synxio.player.data.model.RulePlaylistRules
import fr.synxio.player.data.model.SmartSort
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.RulePlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** État de l'écran « Décris ta playlist ». */
data class NaturalPlaylistState(
    val text: String = "",
    /** Clauses comprises, dans l'ordre d'affichage ; [removed] garde les indices retirés d'un appui. */
    val understood: List<Clause> = emptyList(),
    val removed: Set<Int> = emptySet(),
    val ignored: List<String> = emptyList(),
    val rules: RulePlaylistRules = RulePlaylistRules(sort = SmartSort.RANDOM),
    /** Titres attrapés aujourd'hui, limite comprise ; vide sans clause active. */
    val songs: List<Song> = emptyList(),
) {
    val activeClauses: List<Clause> get() = understood.filterIndexed { index, _ -> index !in removed }
    val hasRules: Boolean get() = rules.rules.isNotEmpty() || rules.anyOf.isNotEmpty()
    val count: Int get() = songs.size
    val durationMs: Long get() = songs.sumOf { it.durationMs }
    val preview: List<Song> get() = songs.take(PREVIEW)
    /** Une clause tempo / énergie / tonalité ne voit que les titres analysés : l'écran le dit. */
    val needsAnalysis: Boolean get() = (rules.rules + rules.anyOf).any { it.field in ANALYSIS_FIELDS }
    val suggestedName: String get() = NaturalPlaylistParser.suggestName(text)

    private companion object {
        const val PREVIEW = 5
        val ANALYSIS_FIELDS = setOf(RuleField.BPM, RuleField.ENERGY, RuleField.KEY)
    }
}

/**
 * Lit la phrase au fil de la frappe (débouncée), la soumet à [NaturalPlaylistParser] avec le
 * vocabulaire de la bibliothèque, et évalue les règles sur l'instantané du dépôt pour le
 * compteur et l'aperçu. Retirer une chip recompose les règles sans relire la phrase.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class NaturalPlaylistViewModel @Inject constructor(
    private val repository: RulePlaylistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NaturalPlaylistState())
    val state: StateFlow<NaturalPlaylistState> = _state.asStateFlow()

    private val input = MutableStateFlow("")

    init {
        // Même raison que l'éditeur : l'instantané du dépôt ne vit que si quelqu'un collecte.
        viewModelScope.launch { repository.rulePlaylists.collect {} }
        viewModelScope.launch { input.debounce(DEBOUNCE_MS).collect { reparse(it) } }
    }

    fun setText(value: String) {
        _state.update { it.copy(text = value) }
        input.value = value
    }

    /** Un appui sur une chip « Compris » retire la clause ; un second la remet. */
    fun toggleClause(index: Int) {
        _state.update { s -> s.copy(removed = if (index in s.removed) s.removed - index else s.removed + index) }
        viewModelScope.launch { recompute() }
    }

    /** Les mêmes titres, enchaînés en Flow (titres non analysés en fin). */
    suspend fun flowSongs(): List<Song> = withContext(Dispatchers.Default) {
        repository.evaluateNow(_state.value.rules.copy(sort = SmartSort.FLOW))
    }

    private suspend fun reparse(text: String) {
        val parsed = withContext(Dispatchers.Default) { NaturalPlaylistParser.parse(text, repository.vocabulary()) }
        _state.update { it.copy(understood = parsed.understood, ignored = parsed.ignored, removed = emptySet()) }
        recompute()
    }

    private suspend fun recompute() {
        val clauses = _state.value.activeClauses
        val rules = NaturalPlaylistParser.assemble(clauses)
        val songs = if (clauses.isEmpty()) emptyList() else withContext(Dispatchers.Default) { repository.evaluateNow(rules) }
        _state.update { it.copy(rules = rules, songs = songs) }
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
    }
}
