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
    /** Alternatives « l'un de » : un titre passe s'il en remplit au moins une, en plus des [rules]. */
    val anyOf: List<SmartRule> = emptyList(),
    val sort: SmartSort = SmartSort.TITLE,
    val descending: Boolean = false,
    val limitText: String = "",
    val matchCount: Int = 0,
    val saved: Boolean = false,
) {
    /** Enregistrement désactivé tant qu'il manque un nom ou qu'aucun critère ni alternative n'est posé. */
    val canSave: Boolean get() = name.isNotBlank() && (rules.isNotEmpty() || anyOf.isNotEmpty())
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

    /**
     * [draft] : nom et règles proposés par l'écran « Décris ta playlist » ; ne vaut qu'en
     * création ([id] à 0 ou moins) et préremplit l'éditeur comme si l'utilisateur les avait tapés.
     */
    fun load(id: Long, draft: Pair<String, RulePlaylistRules>? = null) {
        if (loadedFor == id) return
        loadedFor = id
        editingId = id

        if (id <= 0) {
            _state.value = draft?.let { (name, rules) -> stateOf(name, rules) } ?: RulePlaylistEditorState(loading = false)
            recount()
            return
        }

        viewModelScope.launch {
            val loaded = repository.load(id)
            _state.value = loaded?.let { (name, rules) -> stateOf(name, rules) } ?: RulePlaylistEditorState(loading = false)
            recount()
        }
    }

    private fun stateOf(name: String, rules: RulePlaylistRules) = RulePlaylistEditorState(
        loading = false,
        name = name,
        rules = rules.rules,
        matchAll = rules.matchAll,
        anyOf = rules.anyOf,
        sort = rules.sort,
        descending = rules.descending,
        limitText = rules.limit?.toString().orEmpty(),
    )

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

    fun addRule() = edit(group = false) { it + newRule() }
    fun removeRule(index: Int) = edit(group = false) { it.toMutableList().apply { removeAt(index) } }
    fun addAnyOf() = edit(group = true) { it + newRule() }
    fun removeAnyOf(index: Int) = edit(group = true) { it.toMutableList().apply { removeAt(index) } }

    private fun newRule(): SmartRule {
        val field = RuleField.entries.first()
        return SmartRule(field, field.operators.first(), "")
    }

    /** Les critères et les alternatives « l'un de » se modifient de la même façon : [group] choisit la liste. */
    private fun edit(group: Boolean, transform: (List<SmartRule>) -> List<SmartRule>) {
        val current = _state.value
        _state.value = if (group) current.copy(anyOf = transform(current.anyOf)) else current.copy(rules = transform(current.rules))
        recount()
    }

    /**
     * Change le champ d'un critère.
     *
     * Si l'opérateur courant ne s'applique plus au nouveau champ (ex. « contient » sur
     * un champ passé de texte à booléen), on retombe sur le premier opérateur valable.
     * La valeur est vidée pour [RuleField.FAVORITE] : ses opérateurs ne l'utilisent pas.
     */
    fun updateField(index: Int, field: RuleField, group: Boolean = false) = edit(group) { list ->
        list.toMutableList().apply {
            val current = this[index]
            val operator = if (current.operator in field.operators) current.operator else field.operators.first()
            this[index] = current.copy(
                field = field,
                operator = operator,
                value = if (field == RuleField.FAVORITE) "" else current.value,
            )
        }
    }

    fun updateOperator(index: Int, operator: RuleOperator, group: Boolean = false) = edit(group) { list ->
        list.toMutableList().apply { this[index] = this[index].copy(operator = operator) }
    }

    fun updateValue(index: Int, value: String, group: Boolean = false) = edit(group) { list ->
        list.toMutableList().apply { this[index] = this[index].copy(value = value) }
    }

    private fun currentRules(): RulePlaylistRules = with(_state.value) {
        RulePlaylistRules(
            rules = rules,
            matchAll = matchAll,
            sort = sort,
            descending = descending,
            limit = limitText.toIntOrNull(),
            anyOf = anyOf,
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
