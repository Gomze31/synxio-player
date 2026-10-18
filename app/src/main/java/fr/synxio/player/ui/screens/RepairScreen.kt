package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.repo.RepairProposal
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.SynxioSnackbar
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.RepairViewModel

/** Les quatre temps de l'écran : on passe de l'un à l'autre par un fondu, jamais par un saut. */
private enum class RepairPhase { Intro, Progress, Empty, Review }

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

    val phase = when {
        state.progress.running -> RepairPhase.Progress
        !state.analysed -> RepairPhase.Intro
        state.proposals.isEmpty() -> RepairPhase.Empty
        else -> RepairPhase.Review
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) { SynxioSnackbar(it) } },
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Réparer les tags", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(Motion.exit(Motion.XS))
                } else {
                    (fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 12 }) togetherWith
                        fadeOut(Motion.exit())
                }
            },
            label = "repairPhase",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { target ->
            when (target) {
                RepairPhase.Progress -> ProgressPane(
                    label = if (state.applying) "Écriture des tags…" else "Analyse en ligne…",
                    done = state.progress.analysed,
                    total = state.progress.total,
                    fraction = state.progress.fraction,
                    onCancel = viewModel::cancel,
                )

                RepairPhase.Intro -> IntroPane(
                    minConfidence = state.minConfidence,
                    withArtwork = state.withArtwork,
                    onConfidence = viewModel::setMinConfidence,
                    onArtwork = viewModel::setWithArtwork,
                    onStart = viewModel::analyse,
                )

                RepairPhase.Empty -> EmptyState(
                    title = "Rien à corriger",
                    subtitle = "Aucune correspondance fiable trouvée pour les titres suspects.",
                    icon = Icons.Rounded.TaskAlt,
                    actionLabel = "Relancer l'analyse",
                    onAction = viewModel::analyse,
                )

                RepairPhase.Review -> ReviewPane(
                    proposals = state.proposals,
                    selectedCount = state.selectedCount,
                    reduceMotion = reduceMotion,
                    onSelectAll = viewModel::setAllSelected,
                    onToggle = viewModel::toggle,
                    onApply = viewModel::requestApply,
                )
            }
        }
    }
}

/** Liste des corrections proposées, avec la sélection en tête et l'écriture en pied. */
@Composable
private fun ReviewPane(
    proposals: List<RepairProposal>,
    selectedCount: Int,
    reduceMotion: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onToggle: (RepairProposal) -> Unit,
    onApply: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("PROPOSITIONS", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "$selectedCount / ${proposals.size} retenus",
                    style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                    modifier = Modifier.semantics { heading() },
                )
            }
            // Les deux raccourcis s'allument quand ils décrivent l'état courant : on lit
            // d'un coup d'œil « tout est coché » sans compter.
            LumenChip(
                label = "Tout",
                selected = selectedCount == proposals.size,
                onClick = { onSelectAll(true) },
            )
            Spacer(Modifier.width(Space.sm))
            LumenChip(
                label = "Aucun",
                selected = selectedCount == 0,
                onClick = { onSelectAll(false) },
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = Space.xs,
                bottom = Space.bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            itemsIndexed(proposals, key = { _, it -> it.song.id }) { index, proposal ->
                ProposalRow(
                    proposal = proposal,
                    onToggle = { onToggle(proposal) },
                    modifier = (if (reduceMotion) Modifier else Modifier.animateItem())
                        .enterCascade(index, key = proposal.song.id),
                )
            }
        }

        // L'action se pose au-dessus de la liste, sur un pied de verre : elle reste
        // atteignable quelle que soit la longueur de la liste.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.lg),
        ) {
            BrandButton(
                text = when {
                    selectedCount > 1 -> "Appliquer aux $selectedCount titres"
                    selectedCount == 1 -> "Appliquer au titre retenu"
                    else -> "Coche au moins un titre"
                },
                onClick = onApply,
                enabled = selectedCount > 0,
                icon = Icons.Rounded.AutoFixHigh,
                modifier = Modifier.fillMaxWidth(),
            )
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
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text("ANALYSE EN LIGNE", style = EyebrowTextStyle, color = scheme.primary)
        Text(
            "Des tags propres, sans ressaisir",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Synxio repère les titres aux tags douteux — artiste manquant, chaîne " +
                "de re-upload, « (Lyrics) » dans le titre, album de compilation, accents " +
                "perdus — puis cherche la fiche officielle correspondante.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        GlassCard(Modifier.fillMaxWidth().enterCascade(0, key = "repair-confidence")) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Tune)
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "Seuil de fiabilité",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$minConfidence %",
                        style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                        color = scheme.primary,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "Calculé sur l'écart de durée avec ton fichier. Plus haut = moins de " +
                        "propositions, mais plus sûres.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Slider(
                    value = minConfidence.toFloat(),
                    onValueChange = { onConfidence(it.toInt()) },
                    valueRange = 35f..95f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.primary,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = scheme.onSurface.copy(alpha = 0.14f),
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Seuil de fiabilité"
                        stateDescription = "$minConfidence %"
                    },
                )
            }
        }

        // Toute la carte bascule l'option : la cible tactile n'est pas réduite à l'interrupteur.
        GlassCard(Modifier.fillMaxWidth().enterCascade(1, key = "repair-artwork"), contentPadding = 0.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .toggleable(value = withArtwork, role = Role.Switch, onValueChange = onArtwork)
                    .padding(Space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Rounded.Image)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("Télécharger les pochettes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Images 1000×1000 embarquées dans les fichiers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Switch(
                    checked = withArtwork,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary),
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        BrandButton(
            text = "Analyser la bibliothèque",
            onClick = onStart,
            icon = Icons.Rounded.AutoFixHigh,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = SynxioTheme.colors.success,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = "Rien n'est modifié à ce stade : tu verras la liste des corrections " +
                    "proposées avant d'écrire quoi que ce soit.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Space.bottomInset))
    }
}

/**
 * Analyse ou écriture en cours : grand compteur, barre à la signature qui glisse vers sa
 * nouvelle valeur, et des lignes fantômes qui disent « des résultats arrivent ».
 */
@Composable
private fun ProgressPane(
    label: String,
    done: Int,
    total: Int,
    fraction: Float,
    onCancel: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.standardOrSnap(reduce),
        label = "repairProgress",
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter, vertical = Space.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text(label.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Space.sm))
                Text(
                    "${(animated * 100).toInt()} %",
                    style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
                )
                Spacer(Modifier.height(Space.md))
                BrandProgressBar(
                    progress = animated,
                    height = 8.dp,
                    modifier = Modifier.semantics { contentDescription = "$label $done sur $total" },
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "$done / $total titres",
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.lg))
        // Squelettes : décoratifs, l'avancement est déjà annoncé par la barre.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SynxioShape.card)
                .glass(SynxioShape.card, strength = 0.35f)
                .padding(vertical = Space.sm),
        ) {
            repeat(3) { SkeletonSongRow() }
        }
        Spacer(Modifier.height(Space.xl))
        GlassButton(
            text = "Arrêter",
            onClick = onCancel,
            icon = Icons.Rounded.Stop,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Une proposition : toute la carte est la case à cocher. */
@Composable
private fun ProposalRow(
    proposal: RepairProposal,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val confidence = proposal.match?.confidence ?: 0
    // Une proposition décochée s'efface à moitié : on voit ce qui ne sera pas écrit.
    val alpha by animateFloatAsState(
        if (proposal.selected) 1f else 0.55f,
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "proposalAlpha",
    )
    Row(
        modifier
            .fillMaxWidth()
            .pressBounce(0.98f)
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = if (proposal.selected) 0.75f else 0.4f)
            .toggleable(value = proposal.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(contentAlignment = Alignment.TopStart) {
            Artwork(
                model = proposal.match?.artworkUrl ?: proposal.song.artworkUri,
                modifier = Modifier.size(64.dp),
                shape = SynxioShape.artworkSm,
            )
        }
        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = proposal.song.title,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            proposal.changes.forEach { (field, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = field.uppercase(),
                        style = EyebrowTextStyle,
                        color = scheme.primary.copy(alpha = alpha),
                        modifier = Modifier.width(64.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            // Étiquette d'information (fiabilité + raison) : pas une seconde cible de clic.
            Row(
                Modifier
                    .clip(SynxioShape.pill)
                    .background(scheme.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = Space.md, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandProgressBar(
                    progress = confidence / 100f,
                    height = 4.dp,
                    modifier = Modifier.width(28.dp),
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = "$confidence % · " + (proposal.issues.firstOrNull()?.label ?: "Tags à vérifier"),
                    style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Checkbox(
            checked = proposal.selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = scheme.primary, checkmarkColor = scheme.onPrimary),
            modifier = Modifier.padding(start = Space.sm),
        )
    }
}

/** Pastille d'icône des cartes de réglage : disque de verre teinté par l'accent. */
@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}
