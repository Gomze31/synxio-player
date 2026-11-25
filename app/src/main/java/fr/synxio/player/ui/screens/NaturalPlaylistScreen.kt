package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.RulePlaylistRules
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.NaturalPlaylistViewModel
import kotlinx.coroutines.launch

private val EXAMPLES = listOf(
    "Une heure de rock calme des années 90",
    "Du rap énergique que je n'ai jamais écouté",
    "Mes favoris en flow",
    "30 titres de chanson française pour travailler",
    "Électro rapide, sans Daft Punk, en aléatoire",
    "Morceaux courts en la mineur",
)

/**
 * « Décris ta playlist » : une phrase en français devient des règles. Les chips « Compris »
 * montrent ce qui a été lu (un appui retire la clause), « Ignoré » ce qui ne l'a pas été ;
 * le compteur et l'aperçu suivent en direct ; la barre basse lit, lit en Flow, ou envoie
 * dans l'éditeur de règles prérempli.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NaturalPlaylistScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSave: (name: String, rules: RulePlaylistRules) -> Unit,
    modifier: Modifier = Modifier,
) {
    val natural: NaturalPlaylistViewModel = hiltViewModel()
    val state by natural.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Décris ta playlist", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GlassButton(
                        text = "Lire",
                        icon = Icons.Rounded.PlayArrow,
                        onClick = {
                            if (state.songs.isEmpty()) viewModel.showMessage("Aucun titre ne correspond pour l'instant.")
                            else viewModel.play(state.songs)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "Lire en Flow",
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = {
                            if (state.songs.isEmpty()) viewModel.showMessage("Aucun titre ne correspond pour l'instant.")
                            else scope.launch { viewModel.play(natural.flowSongs()) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                BrandButton(
                    text = "Enregistrer",
                    icon = Icons.Rounded.Save,
                    enabled = state.hasRules,
                    onClick = { onSave(state.suggestedName, state.rules) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.sm, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "field") {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = natural::setText,
                    placeholder = { Text("Une heure de rock calme des années 90…") },
                    minLines = 2,
                    maxLines = 4,
                    shape = SynxioShape.cardSm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .enterCascade(0, "natural-field"),
                )
            }

            if (state.understood.isEmpty()) {
                item(key = "examples") {
                    Column(Modifier.enterCascade(1, "natural-examples")) {
                        Label(if (state.text.isBlank()) "Par exemple" else "Rien de compris pour l'instant. Essaie :")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            EXAMPLES.forEach { example ->
                                LumenChip(label = example, selected = false, onClick = { natural.setText(example) })
                            }
                        }
                        if (state.ignored.isNotEmpty()) {
                            Spacer(Modifier.height(Space.md))
                            IgnoredChips(state.ignored)
                        }
                    }
                }
            } else {
                item(key = "understood") {
                    GlassCard(Modifier.fillMaxWidth().enterCascade(1, "natural-understood")) {
                        Column {
                            Label("Compris · appuie pour retirer")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                state.understood.forEachIndexed { index, clause ->
                                    val active = index !in state.removed
                                    LumenChip(
                                        label = clause.label,
                                        selected = active,
                                        icon = if (active) Icons.Rounded.Check else null,
                                        onClick = { natural.toggleClause(index) },
                                    )
                                }
                            }
                            if (state.ignored.isNotEmpty()) {
                                Spacer(Modifier.height(Space.md))
                                IgnoredChips(state.ignored)
                            }
                        }
                    }
                }

                item(key = "count") {
                    Column(Modifier.padding(horizontal = Space.xs)) {
                        Text(
                            text = if (state.count == 0) "Aucun titre ne correspond" else "${state.count.pluralSongs()} · ${state.durationMs.asDuration()}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (state.needsAnalysis) {
                            Text(
                                text = "Tempo, énergie et tonalité ne voient que les titres analysés : l'analyse du son continue en arrière-plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(state.preview, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.playSong(song, state.songs) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (state.count > state.preview.size) {
                    item(key = "more") {
                        Text(
                            text = "… et ${state.count - state.preview.size} autres",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Space.sm),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IgnoredChips(words: List<String>) {
    Label("Ignoré")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        words.forEach { word -> LumenChip(label = word, selected = false, onClick = {}) }
    }
}
