package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.RuleField
import fr.synxio.player.data.model.RuleOperator
import fr.synxio.player.data.model.RulePlaylistRules
import fr.synxio.player.data.model.SmartRule
import fr.synxio.player.data.model.SmartSort
import fr.synxio.player.data.repo.RulePlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de l'écran d'édition d'une playlist à règles. */
data class RulePlaylistEditorState(
    val loading: Boolean = true,
    val name: String = "",
    val rules: List<SmartRule> = emptyList(),
    val matchAll: Boolean = true,
    val sort: SmartSort = SmartSort.TITLE,
    val descending: Boolean = false,
    val limitText: String = "",
    val matchCount: Int = 0,
    val saved: Boolean = false,
) {
    /** Enregistrement désactivé tant qu'il manque un nom ou qu'aucun critère n'est posé. */
    val canSave: Boolean get() = name.isNotBlank() && rules.isNotEmpty()
}

/**
 * Pilote la création et la modification d'une playlist à règles.
 *
 * `ruleId` à 0 ou moins signifie création ; un identifiant positif charge la playlist
 * existante via [RulePlaylistRepository.load].
 */
@HiltViewModel
class RulePlaylistEditorViewModel @Inject constructor(
    private val repository: RulePlaylistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RulePlaylistEditorState())
    val state: StateFlow<RulePlaylistEditorState> = _state.asStateFlow()

    private var editingId: Long = -1L
    private var loadedFor: Long? = null

    init {
        // countMatching() lit un instantané interne au dépôt, alimenté par ce même flux :
        // sans un collecteur actif pendant que l'éditeur est ouvert, l'instantané se fige
        // (ou reste vide) et l'aperçu live cesse d'être vraiment live.
        viewModelScope.launch { repository.rulePlaylists.collect {} }
    }

    fun load(id: Long) {
        if (loadedFor == id) return
        loadedFor = id
        editingId = id

        if (id <= 0) {
            _state.value = RulePlaylistEditorState(loading = false)
            recount()
            return
        }

        viewModelScope.launch {
            val loaded = repository.load(id)
            _state.value = if (loaded != null) {
                val (name, rules) = loaded
                RulePlaylistEditorState(
                    loading = false,
                    name = name,
                    rules = rules.rules,
                    matchAll = rules.matchAll,
                    sort = rules.sort,
                    descending = rules.descending,
                    limitText = rules.limit?.toString().orEmpty(),
                )
            } else {
                RulePlaylistEditorState(loading = false)
            }
            recount()
        }
    }

    fun setName(value: String) {
        _state.value = _state.value.copy(name = value)
    }

    fun setMatchAll(value: Boolean) {
        _state.value = _state.value.copy(matchAll = value)
        recount()
    }

    fun setSort(value: SmartSort) {
        _state.value = _state.value.copy(sort = value)
    }

    fun setDescending(value: Boolean) {
        _state.value = _state.value.copy(descending = value)
    }

    fun setLimitText(value: String) {
        if (value.isEmpty() || value.all(Char::isDigit)) {
            _state.value = _state.value.copy(limitText = value)
        }
    }

    fun addRule() {
        val field = RuleField.entries.first()
        _state.value = _state.value.copy(
            rules = _state.value.rules + SmartRule(field, field.operators.first(), ""),
        )
        recount()
    }

    fun removeRule(index: Int) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.toMutableList().apply { removeAt(index) },
        )
        recount()
    }

    /**
     * Change le champ d'un critère.
     *
     * Si l'opérateur courant ne s'applique plus au nouveau champ (ex. « contient » sur
     * un champ passé de texte à booléen), on retombe sur le premier opérateur valable.
     * La valeur est vidée pour [RuleField.FAVORITE] : ses opérateurs ne l'utilisent pas.
     */
    fun updateField(index: Int, field: RuleField) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.toMutableList().apply {
                val current = this[index]
                val operator = if (current.operator in field.operators) {
                    current.operator
                } else {
                    field.operators.first()
                }
                this[index] = current.copy(
                    field = field,
                    operator = operator,
                    value = if (field == RuleField.FAVORITE) "" else current.value,
                )
            },
        )
        recount()
    }

    fun updateOperator(index: Int, operator: RuleOperator) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.toMutableList().apply {
                this[index] = this[index].copy(operator = operator)
            },
        )
        recount()
    }

    fun updateValue(index: Int, value: String) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.toMutableList().apply {
                this[index] = this[index].copy(value = value)
            },
        )
        recount()
    }

    private fun currentRules(): RulePlaylistRules = with(_state.value) {
        RulePlaylistRules(
            rules = rules,
            matchAll = matchAll,
            sort = sort,
            descending = descending,
            limit = limitText.toIntOrNull(),
        )
    }

    private fun recount() {
        _state.value = _state.value.copy(matchCount = repository.countMatching(currentRules()))
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(editingId, current.name.trim(), currentRules())
            _state.value = current.copy(saved = true)
        }
    }
}
