package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.RuleField
import fr.synxio.player.data.model.RuleOperator
import fr.synxio.player.data.model.SmartRule
import fr.synxio.player.data.model.SmartSort
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.shimmer
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.synxioTopBarColors
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

/** Pastille numérotée d'un critère. */
private val RuleBadgeSize = 28.dp

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
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = {
                    Text(
                        if (ruleId > 0) "Modifier la règle" else "Nouvelle règle",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            SaveBar(
                matchCount = state.matchCount,
                enabled = !state.loading,
                onSave = { if (formValid) viewModel.save() else showErrors = true },
            )
        },
    ) { padding ->
        if (state.loading) {
            EditorSkeleton(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.sm, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "name") {
                GlassCard(Modifier.fillMaxWidth().enterCascade(0, "rule-name")) {
                    Column {
                        FormSectionTitle("Nom", eyebrow = "Ta playlist", topPadding = 0.dp)
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::setName,
                            label = { Text("Nom de la playlist") },
                            singleLine = true,
                            isError = nameError != null,
                            supportingText = nameError?.let { { Text(it) } },
                            shape = SynxioShape.cardSm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item(key = "match") {
                GlassCard(Modifier.fillMaxWidth().enterCascade(1, "rule-match")) {
                    Column {
                        FormSectionTitle("Correspondance", eyebrow = "Logique", topPadding = 0.dp)
                        ChoiceRow(
                            options = listOf(true to "Toutes les règles", false to "Au moins une"),
                            selected = state.matchAll,
                            onSelect = viewModel::setMatchAll,
                        )
                    }
                }
            }

            item(key = "rules-title") {
                Column(Modifier.enterCascade(2, "rule-criteria")) {
                    FormSectionTitle("Critères", eyebrow = "Ce qui entre")
                    if (state.rules.isEmpty()) {
                        Text(
                            text = rulesError ?: "Aucun critère : ajoute-en un pour composer ta règle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rulesError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "add-rule") {
                GlassButton(
                    text = "Ajouter un critère",
                    onClick = viewModel::addRule,
                    icon = Icons.Rounded.Add,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth(),
                )
            }

            item(key = "sort") {
                GlassCard(Modifier.fillMaxWidth().animateItem()) {
                    Column {
                        FormSectionTitle("Tri", eyebrow = "Ordre", topPadding = 0.dp)
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
                }
            }

            item(key = "limit") {
                GlassCard(Modifier.fillMaxWidth().animateItem()) {
                    Column {
                        FormSectionTitle("Limite", eyebrow = "Taille", topPadding = 0.dp)
                        OutlinedTextField(
                            value = state.limitText,
                            onValueChange = viewModel::setLimitText,
                            label = { Text("Nombre maximal de titres") },
                            placeholder = { Text("Pas de limite") },
                            supportingText = { Text("Laisse vide pour garder tous les titres.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = SynxioShape.cardSm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Barre d'enregistrement : verre dense, aperçu du nombre de titres qui roule quand il
 * change (annoncé poliment par TalkBack), puis l'action principale à la signature.
 */
@Composable
private fun SaveBar(matchCount: Int, enabled: Boolean, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SynxioShape.sheet)
            .background(scheme.surfaceContainer.copy(alpha = 0.9f))
            .glass(SynxioShape.sheet, strength = 1f)
            .padding(horizontal = Space.gutter, vertical = Space.md),
    ) {
        AnimatedContent(
            targetState = matchCount,
            transitionSpec = {
                if (reduce) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    val up = targetState > initialState
                    (slideInVertically(Motion.spatial()) { if (up) it else -it } + fadeIn(Motion.enter(Motion.SHORT)))
                        .togetherWith(slideOutVertically(Motion.spatial()) { if (up) -it else it } + fadeOut(Motion.exit()))
                }
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            label = "matchCount",
        ) { count ->
            Text(
                text = "${count.pluralSongs()} correspond${if (count > 1) "ent" else ""}",
                style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Space.sm))
        BrandButton(
            text = "Enregistrer",
            onClick = onSave,
            icon = Icons.Rounded.Check,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Squelette du formulaire pendant le chargement d'une règle existante. */
@Composable
private fun EditorSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter, vertical = Space.sm)
            .semantics { contentDescription = "Chargement" },
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (i == 0) 120.dp else 150.dp)
                    .clip(SynxioShape.card)
                    .shimmer()
            )
        }
    }
}

@Composable
private fun FormSectionTitle(text: String, eyebrow: String? = null, topPadding: androidx.compose.ui.unit.Dp = Space.lg) {
    Column(Modifier.padding(top = topPadding, bottom = Space.sm)) {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(Space.xxs))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Choix exclusif en puces Lumen : annoncé comme un groupe de boutons radio. */
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
            LumenChip(
                label = label,
                selected = isSelected,
                onClick = { onSelect(value) },
                icon = if (isSelected) Icons.Rounded.Check else null,
                // La puce gère déjà le clic et l'état ; on précise seulement le rôle.
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/**
 * Un critère : carte de verre numérotée d'une pastille à la signature. Le verre plutôt
 * qu'une ombre : la liste en contient plusieurs, des ombres empilées alourdiraient l'écran.
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
    val colors = SynxioTheme.colors
    GlassCard(modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(Modifier.padding(start = Space.lg, end = Space.xs, top = Space.xs, bottom = Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(RuleBadgeSize)
                        .clip(CircleShape)
                        .background(colors.brandBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                        color = colors.onBrand,
                    )
                }
                Spacer(Modifier.size(Space.md))
                Text(
                    text = "Critère ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = "Retirer le critère ${index + 1}")
                }
            }
            Spacer(Modifier.height(Space.xs))
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
                        shape = SynxioShape.cardSm,
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
    val scheme = MaterialTheme.colorScheme
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
            shape = SynxioShape.cardSm,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = SynxioShape.cardSm,
            containerColor = scheme.surfaceContainerHigh,
        ) {
            options.forEach { option ->
                val current = option == selected
                DropdownMenuItem(
                    text = { Text(optionLabel(option), color = if (current) scheme.primary else scheme.onSurface) },
                    trailingIcon = if (current) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary) }
                    } else null,
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
