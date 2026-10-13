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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.repo.RepairProposal
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.RepairViewModel

/**
 * Réparation des tags en masse.
 *
 * Le principe : on n'écrit jamais sans montrer. L'analyse propose, l'utilisateur
 * décoche ce qui lui semble douteux, et l'écriture ne part qu'ensuite — avec une
 * seule demande d'autorisation système pour tous les fichiers.
 *
 * L'écran n'a pas accès à l'AppViewModel : ses messages passent par sa propre snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: RepairViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val reduceMotion = LocalReduceMotion.current

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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Réparer les tags") },
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
                    icon = Icons.Rounded.TaskAlt,
                    actionLabel = "Relancer l'analyse",
                    onAction = viewModel::analyse,
                )
                return@Column
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.gutter, end = Space.sm, top = Space.sm, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.selectedCount} / ${state.proposals.size} retenus",
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.setAllSelected(true) }) { Text("Tout cocher") }
                TextButton(onClick = { viewModel.setAllSelected(false) }) { Text("Aucun") }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Space.md),
            ) {
                items(state.proposals, key = { it.song.id }) { proposal ->
                    Column(if (reduceMotion) Modifier else Modifier.animateItem()) {
                        ProposalRow(proposal) { viewModel.toggle(proposal) }
                        HorizontalDivider()
                    }
                }
            }

            Button(
                onClick = viewModel::requestApply,
                enabled = state.selectedCount > 0,
                shape = SynxioShape.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.lg)
                    .heightIn(min = Space.touchTarget),
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                Text(
                    text = if (state.selectedCount > 1) "Appliquer aux ${state.selectedCount} titres"
                    else "Appliquer au titre retenu",
                    modifier = Modifier.padding(start = Space.sm),
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
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(Space.gutter)
    ) {
        Text(
            text = "Synxio repère les titres aux tags douteux — artiste manquant, chaîne " +
                "de re-upload, « (Lyrics) » dans le titre, album de compilation, accents " +
                "perdus — puis cherche la fiche officielle correspondante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xl))

        Text(
            "Seuil de fiabilité : $minConfidence %",
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
        )
        Text(
            text = "Calculé sur l'écart de durée avec ton fichier. Plus haut = moins de " +
                "propositions, mais plus sûres.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = minConfidence.toFloat(),
            onValueChange = { onConfidence(it.toInt()) },
            valueRange = 35f..95f,
            steps = 3,
            modifier = Modifier.semantics {
                contentDescription = "Seuil de fiabilité"
                stateDescription = "$minConfidence %"
            },
        )

        Spacer(Modifier.height(Space.sm))
        // Ligne entière basculable : la cible tactile n'est pas réduite à l'interrupteur.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touchTarget)
                .toggleable(value = withArtwork, role = Role.Switch, onValueChange = onArtwork),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Télécharger les pochettes", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Images 1000×1000 embarquées dans les fichiers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Space.md))
            Switch(checked = withArtwork, onCheckedChange = null)
        }

        Spacer(Modifier.height(Space.xl))
        Button(
            onClick = onStart,
            shape = SynxioShape.pill,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touchTarget),
        ) {
            Text("Analyser la bibliothèque")
        }
        Spacer(Modifier.height(Space.md))
        Text(
            text = "Rien n'est modifié à ce stade : tu verras la liste des corrections " +
                "proposées avant d'écrire quoi que ce soit.",
            style = MaterialTheme.typography.bodyMedium,
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
            .padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Space.lg))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label $done sur $total" },
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "$done / $total",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xl))
        OutlinedButton(onClick = onCancel, shape = SynxioShape.pill) { Text("Arrêter") }
    }
}

/** Une proposition : toute la ligne est la case à cocher. */
@Composable
private fun ProposalRow(proposal: RepairProposal, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = proposal.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = proposal.selected, onCheckedChange = null, modifier = Modifier.padding(end = Space.sm))
        Artwork(
            model = proposal.match?.artworkUrl ?: proposal.song.artworkUri,
            modifier = Modifier.size(52.dp),
            shape = SynxioShape.artworkSm,
        )
        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = proposal.song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            proposal.changes.forEach { (field, value) ->
                Row {
                    Text(
                        text = "$field ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
            // Étiquette d'information (fiabilité + raison) : pas une seconde cible de clic.
            Surface(
                shape = SynxioShape.pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = "${proposal.match?.confidence ?: 0} % · " +
                        (proposal.issues.firstOrNull()?.label ?: "Tags à vérifier"),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                )
            }
        }
    }
}
