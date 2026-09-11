package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import fr.synxio.player.ui.viewmodel.DuplicatesViewModel

/**
 * Chasse aux doublons.
 *
 * La copie conservée est marquée « à garder » et ne peut pas être décochée seule sans
 * qu'une autre la remplace : on ne veut pas qu'un geste distrait supprime le morceau
 * dans son intégralité.
 */
@Composable
fun DuplicatesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DuplicatesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Doublons") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
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
                        Spacer(Modifier.height(16.dp))
                        Text("Comparaison de la bibliothèque…")
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
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(state.groups, key = { it.keeper.id }) { group ->
                            GroupCard(group) { song -> viewModel.toggle(group, song) }
                            HorizontalDivider()
                        }
                    }

                    Button(
                        onClick = viewModel::requestDelete,
                        enabled = state.selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Text(
                            text = "Mettre ${state.selectedCount} fichier" +
                                "${if (state.selectedCount > 1) "s" else ""} à la corbeille",
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroPane(onScan: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text(
            text = "Synxio compare tes fichiers deux à deux : d'abord les copies strictement " +
                "identiques (même taille, même durée), puis les morceaux qui portent le même " +
                "titre et le même artiste avec une durée concordante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Un live, un remix ou une version radio ont des durées différentes : ils ne " +
                "sont jamais regroupés avec l'original.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("Chercher les doublons", Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Les fichiers supprimés partent à la corbeille du système : tu as trente " +
                "jours pour revenir en arrière.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, onToggle: (Song) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                model = group.keeper.artworkUri,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.keeper.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = group.keeper.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(group.kind.label, style = MaterialTheme.typography.labelSmall) },
            )
        }

        Spacer(Modifier.height(6.dp))
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

@Composable
private fun CopyRow(song: Song, isKeeper: Boolean, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isKeeper) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "à garder",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "${song.extension} · ~${song.approxBitrateKbps} kb/s · " +
                    "${song.sizeBytes.asFileSize()} · ${song.durationMs.asDuration()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
