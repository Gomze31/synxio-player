package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.synxio.player.data.repo.RepairProposal
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.viewmodel.RepairViewModel

/**
 * Réparation des tags en masse.
 *
 * Le principe : on n'écrit jamais sans montrer. L'analyse propose, l'utilisateur
 * décoche ce qui lui semble douteux, et l'écriture ne part qu'ensuite — avec une
 * seule demande d'autorisation système pour tous les fichiers.
 */
@Composable
fun RepairScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: RepairViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(state.consentRequest) {
        state.consentRequest?.let {
            consentLauncher.launch(IntentSenderRequest.Builder(it).build())
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
                title = { Text("Réparer les tags") },
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
            if (state.progress.running) {
                ProgressPane(
                    label = if (state.applying) "Écriture des tags…" else "Analyse en ligne…",
                    done = state.progress.analysed,
                    total = state.progress.total,
                    fraction = state.progress.fraction,
                    onCancel = viewModel::cancel,
                )
                return@Column
            }

            if (!state.analysed) {
                IntroPane(
                    minConfidence = state.minConfidence,
                    withArtwork = state.withArtwork,
                    onConfidence = viewModel::setMinConfidence,
                    onArtwork = viewModel::setWithArtwork,
                    onStart = viewModel::analyse,
                )
                return@Column
            }

            if (state.proposals.isEmpty()) {
                EmptyState(
                    title = "Rien à corriger",
                    subtitle = "Aucune correspondance fiable trouvée pour les titres suspects.",
                    actionLabel = "Relancer l'analyse",
                    onAction = viewModel::analyse,
                )
                return@Column
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.selectedCount} / ${state.proposals.size} retenus",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.setAllSelected(true) }) { Text("Tout") }
                TextButton(onClick = { viewModel.setAllSelected(false) }) { Text("Aucun") }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.proposals, key = { it.song.id }) { proposal ->
                    ProposalRow(proposal) { viewModel.toggle(proposal) }
                    HorizontalDivider()
                }
            }

            Button(
                onClick = viewModel::requestApply,
                enabled = state.selectedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                Text(
                    text = "Appliquer aux ${state.selectedCount} titres",
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun IntroPane(
    minConfidence: Int,
    withArtwork: Boolean,
    onConfidence: (Int) -> Unit,
    onArtwork: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.padding(20.dp)) {
        Text(
            text = "Synxio repère les titres aux tags douteux — artiste manquant, chaîne " +
                "de re-upload, « (Lyrics) » dans le titre, album de compilation, accents " +
                "perdus — puis cherche la fiche officielle correspondante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Text("Seuil de fiabilité : $minConfidence %", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Calculé sur l'écart de durée avec ton fichier. Plus haut = moins de " +
                "propositions, mais plus sûres.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = minConfidence.toFloat(),
            onValueChange = { onConfidence(it.toInt()) },
            valueRange = 35f..95f,
            steps = 3,
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Télécharger les pochettes", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Images 1000×1000 embarquées dans les fichiers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = withArtwork, onCheckedChange = onArtwork)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Analyser la bibliothèque", Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Rien n'est modifié à ce stade : tu verras la liste des corrections " +
                "proposées avant d'écrire quoi que ce soit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressPane(
    label: String,
    done: Int,
    total: Int,
    fraction: Float,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("$done / $total", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text("Arrêter") }
    }
}

@Composable
private fun ProposalRow(proposal: RepairProposal, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = proposal.selected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Artwork(
            model = proposal.match?.artworkUrl ?: proposal.song.artworkUri,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = proposal.song.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            proposal.changes.forEach { (field, value) ->
                Row {
                    Text(
                        text = "$field ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Box {
                AssistChip(
                    onClick = onToggle,
                    label = {
                        Text(
                            text = "${proposal.match?.confidence ?: 0} % · " +
                                proposal.issues.first().label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}
