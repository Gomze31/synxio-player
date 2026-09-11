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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.RuleField
import fr.synxio.player.data.model.RuleOperator
import fr.synxio.player.data.model.SmartRule
import fr.synxio.player.data.model.SmartSort
import fr.synxio.player.ui.viewmodel.RulePlaylistEditorViewModel

/**
 * Éditeur d'une playlist à règles : nom, critères et tri, avec un aperçu live du nombre
 * de titres correspondants.
 *
 * `ruleId` à 0 ou moins crée une nouvelle playlist ; un identifiant positif charge la
 * playlist existante.
 */
@Composable
fun RulePlaylistEditorScreen(
    ruleId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RulePlaylistEditorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(ruleId) { viewModel.load(ruleId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId > 0) "Modifier la règle" else "Nouvelle règle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "${state.matchCount} titre(s) correspondent",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Enregistrer") }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nom de la playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
            }

            item {
                Text("Correspondance", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.matchAll,
                        onClick = { viewModel.setMatchAll(true) },
                        label = { Text("Toutes les règles") },
                    )
                    FilterChip(
                        selected = !state.matchAll,
                        onClick = { viewModel.setMatchAll(false) },
                        label = { Text("Au moins une") },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            if (state.rules.isEmpty()) {
                item {
                    Text(
                        text = "Aucun critère : ajoute-en un pour composer ta règle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            itemsIndexed(state.rules) { index, rule ->
                RuleCard(
                    rule = rule,
                    onFieldChange = { viewModel.updateField(index, it) },
                    onOperatorChange = { viewModel.updateOperator(index, it) },
                    onValueChange = { viewModel.updateValue(index, it) },
                    onRemove = { viewModel.removeRule(index) },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            item {
                TextButton(onClick = viewModel::addRule) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ajouter un critère")
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text("Tri", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                DropdownSelector(
                    label = "Trier par",
                    selected = state.sort,
                    options = SmartSort.entries,
                    optionLabel = SmartSort::label,
                    onSelect = viewModel::setSort,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.descending,
                        onClick = { viewModel.setDescending(false) },
                        label = { Text("Croissant") },
                    )
                    FilterChip(
                        selected = state.descending,
                        onClick = { viewModel.setDescending(true) },
                        label = { Text("Décroissant") },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                OutlinedTextField(
                    value = state.limitText,
                    onValueChange = viewModel::setLimitText,
                    label = { Text("Limite (facultatif)") },
                    placeholder = { Text("Pas de limite") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: SmartRule,
    onFieldChange: (RuleField) -> Unit,
    onOperatorChange: (RuleOperator) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Critère",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Retirer ce critère")
                }
            }
            Spacer(Modifier.height(4.dp))
            DropdownSelector(
                label = "Champ",
                selected = rule.field,
                options = RuleField.entries,
                optionLabel = RuleField::label,
                onSelect = onFieldChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = rule.value,
                    onValueChange = onValueChange,
                    label = { Text("Valeur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
