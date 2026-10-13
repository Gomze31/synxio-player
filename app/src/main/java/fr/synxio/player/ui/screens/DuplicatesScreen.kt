package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.asFileSize
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.DuplicateGroup
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.DuplicatesViewModel

/**
 * Chasse aux doublons.
 *
 * La copie conservée est marquée « à garder » et ne peut pas être décochée seule sans
 * qu'une autre la remplace : on ne veut pas qu'un geste distrait supprime le morceau
 * dans son intégralité.
 *
 * L'écran n'a pas accès à l'AppViewModel : ses messages passent par sa propre snackbar.
 * La suppression elle-même est confirmée par la boîte de dialogue système (corbeille).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DuplicatesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val reduceMotion = LocalReduceMotion.current

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(state.deleteRequest) {
        state.deleteRequest?.let {
            deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Doublons") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.scanning -> {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Space.lg))
                        Text(
                            "Comparaison de la bibliothèque…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                !state.scanned -> IntroPane(onScan = viewModel::scan)

                state.groups.isEmpty() -> EmptyState(
                    title = "Aucun doublon",
                    subtitle = "Chaque morceau de ta bibliothèque n'existe qu'en un seul exemplaire.",
                    icon = Icons.Rounded.ContentCopy,
                    actionLabel = "Relancer l'analyse",
                    onAction = viewModel::scan,
                )

                else -> {
                    Text(
                        text = "${state.groups.size} groupe${if (state.groups.size > 1) "s" else ""} · " +
                            "${state.reclaimedBytes.asFileSize()} récupérables",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.md),
                    )

                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = Space.md),
                    ) {
                        items(state.groups, key = { it.keeper.id }) { group ->
                            Column(if (reduceMotion) Modifier else Modifier.animateItem()) {
                                GroupCard(group) { song -> viewModel.toggle(group, song) }
                                HorizontalDivider()
                            }
                        }
                    }

                    Button(
                        onClick = viewModel::requestDelete,
                        enabled = state.selectedCount > 0,
                        shape = SynxioShape.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.gutter, vertical = Space.lg)
                            .heightIn(min = Space.touchTarget),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Text(
                            text = "Mettre ${state.selectedCount} fichier" +
                                "${if (state.selectedCount > 1) "s" else ""} à la corbeille",
                            modifier = Modifier.padding(start = Space.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroPane(onScan: () -> Unit) {
    Column(Modifier.padding(Space.gutter)) {
        Text(
            text = "Synxio compare tes fichiers deux à deux : d'abord les copies strictement " +
                "identiques (même taille, même durée), puis les morceaux qui portent le même " +
                "titre et le même artiste avec une durée concordante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.lg))
        Text(
            text = "Un live, un remix ou une version radio ont des durées différentes : ils ne " +
                "sont jamais regroupés avec l'original.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xl))
        Button(
            onClick = onScan,
            shape = SynxioShape.pill,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touchTarget),
        ) {
            Text("Chercher les doublons")
        }
        Spacer(Modifier.height(Space.md))
        Text(
            text = "Les fichiers supprimés partent à la corbeille du système : tu as trente " +
                "jours pour revenir en arrière.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, onToggle: (Song) -> Unit) {
    Column(Modifier.padding(horizontal = Space.md, vertical = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                model = group.keeper.artworkUri,
                modifier = Modifier.size(44.dp),
                shape = SynxioShape.artworkSm,
            )
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.keeper.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = group.keeper.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Simple étiquette : le type de doublon s'affiche, il ne se clique pas.
            Surface(
                shape = SynxioShape.pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    group.kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                )
            }
        }

        Spacer(Modifier.height(Space.xs))
        group.all.forEach { song ->
            CopyRow(
                song = song,
                isKeeper = song.id == group.keeper.id,
                selected = song.id in group.selectedIds,
                onToggle = { onToggle(song) },
            )
        }
    }
}

/** Toute la ligne est la case à cocher : une seule cible tactile, annoncée comme telle. */
@Composable
private fun CopyRow(song: Song, isKeeper: Boolean, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clip(SynxioShape.artworkSm)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .then(
                if (isKeeper) Modifier.semantics {
                    stateDescription = if (selected) "Cochée, copie à garder" else "Non cochée, copie à garder"
                } else Modifier
            )
            .padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(horizontal = Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isKeeper) {
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = "à garder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "${song.extension} · ~${song.approxBitrateKbps} kb/s · " +
                    "${song.sizeBytes.asFileSize()} · ${song.durationMs.asDuration()}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
