package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.asFileSize
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.DuplicateGroup
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.SynxioSnackbar
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.components.shimmer
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.DuplicatesViewModel

private enum class DuplicatesPhase { Scanning, Intro, Empty, Results }

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

    val phase = when {
        state.scanning -> DuplicatesPhase.Scanning
        !state.scanned -> DuplicatesPhase.Intro
        state.groups.isEmpty() -> DuplicatesPhase.Empty
        else -> DuplicatesPhase.Results
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) { SynxioSnackbar(it) } },
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Doublons", style = MaterialTheme.typography.headlineSmall) },
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
            label = "duplicatesPhase",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { target ->
            when (target) {
                DuplicatesPhase.Scanning -> ScanningPane()

                DuplicatesPhase.Intro -> IntroPane(onScan = viewModel::scan)

                DuplicatesPhase.Empty -> EmptyState(
                    title = "Aucun doublon",
                    subtitle = "Chaque morceau de ta bibliothèque n'existe qu'en un seul exemplaire.",
                    icon = Icons.Rounded.ContentCopy,
                    actionLabel = "Relancer l'analyse",
                    onAction = viewModel::scan,
                )

                DuplicatesPhase.Results -> Column(Modifier.fillMaxSize()) {
                    SummaryHeader(
                        groupCount = state.groups.size,
                        reclaimable = state.reclaimedBytes.asFileSize(),
                    )

                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = Space.gutter,
                            end = Space.gutter,
                            top = Space.xs,
                            bottom = Space.bottomInset,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        itemsIndexed(state.groups, key = { _, it -> it.keeper.id }) { index, group ->
                            GroupCard(
                                group = group,
                                onToggle = { song -> viewModel.toggle(group, song) },
                                modifier = (if (reduceMotion) Modifier else Modifier.animateItem())
                                    .enterCascade(index, key = group.keeper.id),
                            )
                        }
                    }

                    TrashButton(
                        count = state.selectedCount,
                        onClick = viewModel::requestDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.gutter, vertical = Space.lg),
                    )
                }
            }
        }
    }
}

/** Bilan en tête : combien de groupes, combien d'espace on peut regagner. */
@Composable
private fun SummaryHeader(groupCount: Int, reclaimable: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text("GROUPES", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Text(
                "$groupCount",
                style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("RÉCUPÉRABLE", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Le volume récupérable change à chaque case cochée : chiffres à chasse fixe.
            AnimatedContent(
                targetState = reclaimable,
                transitionSpec = { fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.XS)) },
                label = "reclaimable",
            ) { value ->
                Text(value, style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers))
            }
        }
    }
}

/** Comparaison en cours : des groupes fantômes, plutôt qu'un simple anneau au milieu du vide. */
@Composable
private fun ScanningPane() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text("ANALYSE", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
        Text(
            "Comparaison de la bibliothèque…",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        repeat(3) { index ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .enterCascade(index, key = "dup-skeleton-$index")
                    .clip(SynxioShape.card)
                    .glass(SynxioShape.card, strength = 0.35f)
                    .padding(Space.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(SynxioShape.artworkSm).shimmer())
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(SynxioShape.pill).shimmer())
                        Spacer(Modifier.height(Space.sm))
                        Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(SynxioShape.pill).shimmer())
                    }
                }
                Spacer(Modifier.height(Space.md))
                repeat(2) {
                    Box(
                        Modifier
                            .padding(vertical = Space.xs)
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(SynxioShape.cardSm)
                            .shimmer()
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPane(onScan: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text("BIBLIOTHÈQUE", style = EyebrowTextStyle, color = scheme.primary)
        Text(
            "Un morceau, un fichier",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        GlassCard(Modifier.fillMaxWidth().enterCascade(0, key = "dup-how")) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                IntroLine(
                    icon = Icons.Rounded.Search,
                    text = "Synxio compare tes fichiers deux à deux : d'abord les copies strictement " +
                        "identiques (même taille, même durée), puis les morceaux qui portent le même " +
                        "titre et le même artiste avec une durée concordante.",
                )
                IntroLine(
                    icon = Icons.Rounded.Verified,
                    text = "Un live, un remix ou une version radio ont des durées différentes : ils ne " +
                        "sont jamais regroupés avec l'original.",
                )
                IntroLine(
                    icon = Icons.Rounded.RestoreFromTrash,
                    text = "Les fichiers supprimés partent à la corbeille du système : tu as trente " +
                        "jours pour revenir en arrière.",
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        BrandButton(
            text = "Chercher les doublons",
            onClick = onScan,
            icon = Icons.Rounded.Search,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.bottomInset))
    }
}

@Composable
private fun IntroLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.md))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Mise à la corbeille : même gabarit que l'action principale Lumen, mais dans la teinte
 * d'erreur du thème. Ici la signature braise → magenta inviterait au geste ; une
 * suppression, même réversible, doit au contraire se lire comme telle.
 */
@Composable
private fun TrashButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val enabled = count > 0
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, Motion.effects(), label = "trashAlpha")
    Row(
        modifier
            .pressBounce(0.95f)
            .heightIn(min = Space.touchTarget)
            .then(if (enabled) Modifier.glowShadow(SynxioShape.pill, scheme.error, elevation = 12.dp) else Modifier)
            .clip(SynxioShape.pill)
            .background(scheme.errorContainer.copy(alpha = alpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.DeleteSweep,
            contentDescription = null,
            tint = scheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = if (enabled) {
                "Mettre $count fichier${if (count > 1) "s" else ""} à la corbeille"
            } else {
                "Coche les copies à supprimer"
            },
            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
            color = scheme.onErrorContainer,
            maxLines = 1,
        )
    }
}

@Composable
private fun GroupCard(
    group: DuplicateGroup,
    onToggle: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.7f)
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                model = group.keeper.artworkUri,
                modifier = Modifier.size(52.dp),
                shape = SynxioShape.artworkSm,
            )
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.keeper.title,
                    style = MaterialTheme.typography.titleMedium,
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
            Spacer(Modifier.width(Space.sm))
            // Simple étiquette : le type de doublon s'affiche, il ne se clique pas.
            Text(
                group.kind.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(SynxioShape.pill)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = Space.md, vertical = Space.xs),
            )
        }

        Spacer(Modifier.height(Space.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
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
}

/** Toute la ligne est la case à cocher : une seule cible tactile, annoncée comme telle. */
@Composable
private fun CopyRow(song: Song, isKeeper: Boolean, selected: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    // Une copie cochée pour la corbeille se teinte d'erreur : on voit ce qui va partir.
    val tint by animateFloatAsState(
        if (selected) 1f else 0f,
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "copyTint",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .pressBounce(0.98f)
            .clip(SynxioShape.cardSm)
            .background(scheme.errorContainer.copy(alpha = 0.35f * tint))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .then(
                if (isKeeper) Modifier.semantics {
                    stateDescription = if (selected) "Cochée, copie à garder" else "Non cochée, copie à garder"
                } else Modifier
            )
            .padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = scheme.error, checkmarkColor = scheme.onError),
            modifier = Modifier.padding(horizontal = Space.md),
        )
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                AnimatedVisibility(
                    visible = isKeeper,
                    enter = scaleIn(Motion.spatialBouncy()) + fadeIn(Motion.enter(Motion.SHORT)),
                    exit = scaleOut(Motion.exit()) + fadeOut(Motion.exit()),
                ) {
                    Row {
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = "À GARDER",
                            style = EyebrowTextStyle,
                            color = SynxioTheme.colors.onBrand,
                            modifier = Modifier
                                .clip(SynxioShape.pill)
                                .background(SynxioTheme.colors.brandBrushHorizontal)
                                .padding(horizontal = Space.sm, vertical = Space.xxs),
                        )
                    }
                }
            }
            Text(
                text = "${song.extension} · ~${song.approxBitrateKbps} kb/s · " +
                    "${song.sizeBytes.asFileSize()} · ${song.durationMs.asDuration()}",
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
