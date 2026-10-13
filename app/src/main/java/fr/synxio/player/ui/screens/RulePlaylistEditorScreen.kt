package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.RuleField
import fr.synxio.player.data.model.RuleOperator
import fr.synxio.player.data.model.SmartRule
import fr.synxio.player.data.model.SmartSort
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.RulePlaylistEditorViewModel

/** Champs dont la valeur est un nombre (ou un nombre de jours pour les dates). */
private val RuleField.isNumeric: Boolean
    get() = this != RuleField.FAVORITE && RuleOperator.CONTAINS !in operators

/** Erreur d'un critère, ou null s'il est valide. */
private fun SmartRule.error(): String? = when {
    field == RuleField.FAVORITE -> null
    value.isBlank() -> "Indique une valeur."
    field.isNumeric && value.trim().toLongOrNull() == null -> "Saisis un nombre entier."
    else -> null
}

/** Aide sous le champ valeur : l'unité attendue n'est pas évidente pour les dates. */
private fun SmartRule.valueHint(): String? = when {
    operator == RuleOperator.IN_LAST_DAYS || operator == RuleOperator.NOT_IN_LAST_DAYS -> "Nombre de jours"
    else -> null
}

/**
 * Éditeur d'une playlist à règles : nom, critères et tri, avec un aperçu live du nombre
 * de titres correspondants.
 *
 * `ruleId` à 0 ou moins crée une nouvelle playlist ; un identifiant positif charge la
 * playlist existante.
 *
 * Le bouton Enregistrer reste actif : grisé, il ne disait pas ce qui manquait. Un appui
 * sur un formulaire incomplet affiche les erreurs sous les champs concernés.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulePlaylistEditorScreen(
    ruleId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RulePlaylistEditorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(ruleId) { viewModel.load(ruleId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val nameError = if (showErrors && state.name.isBlank()) "Donne un nom à ta playlist." else null
    val rulesError = if (showErrors && state.rules.isEmpty()) "Ajoute au moins un critère." else null
    val formValid = state.canSave && state.rules.all { it.error() == null }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId > 0) "Modifier la règle" else "Nouvelle règle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.md)) {
                    Text(
                        text = "${state.matchCount.pluralSongs()} correspond${if (state.matchCount > 1) "ent" else ""}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.height(Space.sm))
                    Button(
                        onClick = { if (formValid) viewModel.save() else showErrors = true },
                        enabled = !state.loading,
                        shape = SynxioShape.pill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touchTarget),
                    ) { Text("Enregistrer") }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Chargement" })
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.lg),
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nom de la playlist") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "match") {
                FormSectionTitle("Correspondance")
                ChoiceRow(
                    options = listOf(true to "Toutes les règles", false to "Au moins une"),
                    selected = state.matchAll,
                    onSelect = viewModel::setMatchAll,
                )
            }

            item(key = "rules-title") {
                FormSectionTitle("Critères")
                if (state.rules.isEmpty()) {
                    Text(
                        text = rulesError ?: "Aucun critère : ajoute-en un pour composer ta règle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (rulesError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                }
            }

            itemsIndexed(state.rules) { index, rule ->
                RuleCard(
                    index = index,
                    rule = rule,
                    error = if (showErrors) rule.error() else null,
                    onFieldChange = { viewModel.updateField(index, it) },
                    onOperatorChange = { viewModel.updateOperator(index, it) },
                    onValueChange = { viewModel.updateValue(index, it) },
                    onRemove = { viewModel.removeRule(index) },
                    modifier = Modifier.padding(bottom = Space.md),
                )
            }

            item(key = "add-rule") {
                OutlinedButton(
                    onClick = viewModel::addRule,
                    shape = SynxioShape.pill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Space.touchTarget),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Ajouter un critère")
                }
            }

            item(key = "sort") {
                FormSectionTitle("Tri")
                DropdownSelector(
                    label = "Trier par",
                    selected = state.sort,
                    options = SmartSort.entries,
                    optionLabel = SmartSort::label,
                    onSelect = viewModel::setSort,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.md))
                ChoiceRow(
                    options = listOf(false to "Croissant", true to "Décroissant"),
                    selected = state.descending,
                    onSelect = viewModel::setDescending,
                )
            }

            item(key = "limit") {
                FormSectionTitle("Limite")
                OutlinedTextField(
                    value = state.limitText,
                    onValueChange = viewModel::setLimitText,
                    label = { Text("Nombre maximal de titres") },
                    placeholder = { Text("Pas de limite") },
                    supportingText = { Text("Laisse vide pour garder tous les titres.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FormSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = Space.xl, bottom = Space.sm)
            .semantics { heading() },
    )
}

/** Choix exclusif en puces : annoncé comme un groupe de boutons radio. */
@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                // La puce gère déjà le clic et l'état coché ; on précise seulement le rôle.
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/**
 * Un critère. Couche tonale plutôt que carte ombrée : la liste en contient plusieurs,
 * des ombres empilées alourdiraient l'écran.
 */
@Composable
private fun RuleCard(
    index: Int,
    rule: SmartRule,
    error: String?,
    onFieldChange: (RuleField) -> Unit,
    onOperatorChange: (RuleOperator) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SynxioShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(start = Space.lg, end = Space.xs, top = Space.xs, bottom = Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Critère ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = "Retirer le critère ${index + 1}")
                }
            }
            Column(Modifier.padding(end = Space.md)) {
                DropdownSelector(
                    label = "Champ",
                    selected = rule.field,
                    options = RuleField.entries,
                    optionLabel = RuleField::label,
                    onSelect = onFieldChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.md))
                DropdownSelector(
                    label = "Condition",
                    // L'opérateur proposé vient toujours de field.operators : « favori
                    // contient » ne doit jamais pouvoir être composé.
                    selected = rule.operator,
                    options = rule.field.operators,
                    optionLabel = RuleOperator::label,
                    onSelect = onOperatorChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                // FAVORITE se suffit de « oui »/« non » : la valeur libre n'a pas de sens ici.
                if (rule.field != RuleField.FAVORITE) {
                    Spacer(Modifier.height(Space.md))
                    val hint = rule.valueHint()
                    OutlinedTextField(
                        value = rule.value,
                        onValueChange = onValueChange,
                        label = { Text("Valeur") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = (error ?: hint)?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (rule.field.isNumeric) KeyboardType.Number else KeyboardType.Text,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
