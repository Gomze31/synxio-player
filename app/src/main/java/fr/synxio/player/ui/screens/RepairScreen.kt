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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asFileSize
import fr.synxio.player.data.meta.BackupSession
import fr.synxio.player.data.meta.EnrichProgress
import fr.synxio.player.data.meta.FieldFix
import fr.synxio.player.data.meta.FixField
import fr.synxio.player.data.meta.TrackFix
import fr.synxio.player.data.meta.WorkshopReport
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.SheetHandle
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.SynxioSnackbar
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.overallFraction
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
import fr.synxio.player.ui.viewmodel.WorkshopFilter
import fr.synxio.player.ui.viewmodel.WorkshopPhase
import fr.synxio.player.ui.viewmodel.WorkshopUiState
import java.text.DateFormat
import java.util.Date

/**
 * Atelier des tags : écrit dans les fichiers ce que la cascade de métadonnées a retrouvé.
 *
 * On n'écrit jamais sans montrer : chaque valeur proposée est présentée champ par champ
 * avec sa source, celles qui sont douteuses arrivent décochées avec la raison du doute,
 * les originaux sont sauvegardés et la séance peut être annulée.
 *
 * L'écran n'a pas accès à l'AppViewModel : ses messages passent par sa propre snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: RepairViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cascade by viewModel.cascade.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val reduceMotion = LocalReduceMotion.current
    var showBackups by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(state.consentRequest) {
        state.consentRequest?.let { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Le bas est déjà absorbé par le mini-lecteur de l'écran principal : sans cette
        // restriction, la marge de la barre système s'ajoutait une seconde fois sous le pied.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar) { SynxioSnackbar(it) } },
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Atelier des tags", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (state.backups.isNotEmpty() && state.phase != WorkshopPhase.WRITING) {
                        IconButton(onClick = { showBackups = true }) {
                            Icon(Icons.Rounded.History, contentDescription = "Sauvegardes des originaux")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(Motion.exit(Motion.XS))
                } else {
                    (fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 12 }) togetherWith
                        fadeOut(Motion.exit())
                }
            },
            label = "workshopPhase",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { phase ->
            when (phase) {
                WorkshopPhase.INTRO -> IntroPane(
                    cascade = cascade,
                    backups = state.backups.size,
                    onStart = viewModel::analyse,
                    onShowBackups = { showBackups = true },
                )

                WorkshopPhase.ANALYSING -> ProgressPane(
                    label = if (cascade.running) cascade.phase.label else "Comparaison avec les fichiers",
                    done = if (cascade.running) cascade.done else state.done,
                    total = if (cascade.running) cascade.total else state.total,
                    fraction = if (cascade.running) cascade.overallFraction() else state.fraction,
                    unit = "titres",
                    onCancel = viewModel::backToIntro,
                )

                WorkshopPhase.WRITING -> ProgressPane(
                    label = if (state.restoring) "Restauration des originaux" else "Écriture dans les fichiers",
                    done = state.done,
                    total = state.total,
                    fraction = state.fraction,
                    unit = "fichiers",
                    // Interrompre une écriture laisserait une séance à moitié appliquée.
                    onCancel = null,
                )

                WorkshopPhase.EMPTY -> EmptyState(
                    title = "Rien à écrire",
                    subtitle = "Tes fichiers ont déjà tout ce que Synxio a retrouvé.",
                    icon = Icons.Rounded.TaskAlt,
                    actionLabel = "Relancer l'analyse",
                    onAction = viewModel::analyse,
                )

                WorkshopPhase.REVIEW -> ReviewPane(
                    state = state,
                    reduceMotion = reduceMotion,
                    onFilter = viewModel::setFilter,
                    onToggleField = viewModel::toggleField,
                    onToggleTrack = viewModel::toggleTrack,
                    onSelectVisible = viewModel::selectVisible,
                    onClearVisible = viewModel::clearVisible,
                    onBackup = viewModel::setBackup,
                    onApply = viewModel::requestApply,
                )

                WorkshopPhase.DONE -> DonePane(
                    report = state.report,
                    onUndo = { state.report?.backup?.let(viewModel::requestRestore) },
                    onAgain = viewModel::analyse,
                    onClose = onBack,
                )
            }
        }
    }

    if (showBackups) {
        BackupsSheet(
            backups = state.backups,
            onRestore = { showBackups = false; viewModel.requestRestore(it) },
            onDelete = viewModel::deleteBackup,
            onDismiss = { showBackups = false },
        )
    }
}

// --- Accueil -------------------------------------------------------------------------

@Composable
private fun IntroPane(
    cascade: EnrichProgress,
    backups: Int,
    onStart: () -> Unit,
    onShowBackups: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text("DANS TES FICHIERS", style = EyebrowTextStyle, color = scheme.primary)
        Text(
            "Ce que Synxio a retrouvé, écrit pour de bon",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Synxio affiche déjà les artistes, albums et pochettes retrouvés. L'atelier les " +
                "écrit dans les fichiers eux-mêmes, pour qu'ils suivent ta musique partout : " +
                "voiture, ordinateur, autres lecteurs.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        StepCard(0, Icons.Rounded.AutoFixHigh, "Propositions champ par champ",
            "Chaque valeur avec sa source : nom du fichier, MusicBrainz, image du dossier, accents réparés.")
        StepCard(1, Icons.Rounded.WarningAmber, "Les doutes arrivent décochés",
            "Ordre artiste/titre incertain, fiche trouvée par le titre seul, album de compilation : à toi de trancher.")
        StepCard(2, Icons.Rounded.Shield, "Originaux sauvegardés",
            "Une copie de chaque fichier est gardée 30 jours. Une séance s'annule d'un geste. Le son n'est jamais réencodé.")

        if (cascade.running) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(cascade.phase.label.uppercase(), style = EyebrowTextStyle, color = scheme.primary)
                    Spacer(Modifier.height(Space.sm))
                    BrandProgressBar(progress = cascade.overallFraction(), height = 6.dp)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "L'atelier attendra la fin de l'analyse de la bibliothèque.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xs))
        BrandButton(
            text = "Préparer les corrections",
            onClick = onStart,
            icon = Icons.Rounded.AutoFixHigh,
            modifier = Modifier.fillMaxWidth(),
        )
        if (backups > 0) {
            GlassButton(
                text = if (backups > 1) "$backups sauvegardes" else "1 sauvegarde",
                onClick = onShowBackups,
                icon = Icons.Rounded.History,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Text(
            text = "Rien n'est modifié à ce stade : tu verras chaque correction avant d'écrire.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.bottomInset))
    }
}

@Composable
private fun StepCard(index: Int, icon: ImageVector, title: String, body: String) {
    GlassCard(Modifier.fillMaxWidth().enterCascade(index, key = "workshop-step-$index")) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Revue ---------------------------------------------------------------------------

@Composable
private fun ReviewPane(
    state: WorkshopUiState,
    reduceMotion: Boolean,
    onFilter: (WorkshopFilter) -> Unit,
    onToggleField: (TrackFix, FixField) -> Unit,
    onToggleTrack: (TrackFix) -> Unit,
    onSelectVisible: (Boolean) -> Unit,
    onClearVisible: () -> Unit,
    onBackup: (Boolean) -> Unit,
    onApply: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val visible = state.visible
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
            Text("PROPOSITIONS", style = EyebrowTextStyle, color = scheme.primary)
            Text(
                text = "${plural(state.selectedFields, "correction")} · ${plural(state.selectedFiles, "fichier")}",
                style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            WorkshopFilter.entries.forEach { filter ->
                val count = when (filter) {
                    WorkshopFilter.ALL -> state.fixes.size
                    WorkshopFilter.SURE -> state.sureCount
                    WorkshopFilter.CHECK -> state.checkCount
                    WorkshopFilter.ARTWORK -> state.artworkCount
                }
                LumenChip(label = "${filter.label} · $count", selected = state.filter == filter, onClick = { onFilter(filter) })
            }
        }
        Row(
            Modifier.padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cocher :",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.sm),
            )
            TextButton(onClick = { onSelectVisible(false) }) { Text("les sûrs") }
            TextButton(onClick = { onSelectVisible(true) }) { Text("tout") }
            TextButton(onClick = onClearVisible) { Text("rien") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            itemsIndexed(visible, key = { _, it -> it.path }) { index, fix ->
                FixCard(
                    fix = fix,
                    selected = state.selection[fix.path].orEmpty(),
                    onToggleField = { onToggleField(fix, it) },
                    onToggleTrack = { onToggleTrack(fix) },
                    modifier = (if (reduceMotion) Modifier else Modifier.animateItem()).enterCascade(index, key = fix.path),
                )
            }
        }

        // Pied de verre : la sauvegarde et l'action restent atteignables quelle que soit la liste.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SynxioShape.sheet)
                .glass(SynxioShape.sheet, strength = 0.8f)
                // Pas de marge de barre système : l'écran est déjà posé au-dessus du
                // mini-lecteur, qui l'absorbe.
                .padding(horizontal = Space.gutter, vertical = Space.md),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .clip(SynxioShape.cardSm)
                    .toggleable(value = state.backup && state.backupFits, enabled = state.backupFits, role = Role.Switch, onValueChange = onBackup),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = SynxioTheme.colors.success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text("Sauvegarder les originaux", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (state.backupFits) "${state.backupBytes.asFileSize()} · gardés 30 jours"
                        else "Pas assez de place (${state.backupBytes.asFileSize()} nécessaires)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.backupFits) scheme.onSurfaceVariant else scheme.error,
                    )
                }
                Switch(
                    checked = state.backup && state.backupFits,
                    onCheckedChange = null,
                    enabled = state.backupFits,
                    colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary),
                )
            }
            Spacer(Modifier.height(Space.sm))
            BrandButton(
                text = when {
                    state.selectedFiles == 0 -> "Coche au moins une correction"
                    state.selectedFiles == 1 -> "Écrire dans 1 fichier"
                    else -> "Écrire dans ${state.selectedFiles} fichiers"
                },
                onClick = onApply,
                enabled = state.selectedFiles > 0,
                icon = Icons.Rounded.AutoFixHigh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Un fichier : en-tête cochable (tout le titre), puis une ligne cochable par champ. */
@Composable
private fun FixCard(
    fix: TrackFix,
    selected: Set<FixField>,
    onToggleField: (FixField) -> Unit,
    onToggleTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val anySelected = selected.isNotEmpty()
    val strength by animateFloatAsState(if (anySelected) 0.75f else 0.4f, Motion.standardOrSnap(reduce, Motion.SHORT), label = "fixGlass")
    Column(
        modifier
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = strength)
            .padding(vertical = Space.sm),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = selected.size == fix.fixes.size, role = Role.Checkbox, onValueChange = { onToggleTrack() })
                .padding(horizontal = Space.md, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(model = fix.previewUri, modifier = Modifier.size(52.dp), shape = SynxioShape.artworkSm)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = fix.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                fix.displayArtist?.let { artist ->
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = fix.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!fix.sure) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = "À vérifier",
                    tint = SynxioTheme.colors.warning,
                    modifier = Modifier.size(20.dp).padding(end = Space.xxs),
                )
            }
            Checkbox(
                checked = selected.size == fix.fixes.size,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = scheme.primary, checkmarkColor = scheme.onPrimary),
            )
        }
        fix.fixes.forEach { fieldFix ->
            FieldRow(fieldFix, checked = fieldFix.field in selected, onToggle = { onToggleField(fieldFix.field) })
        }
    }
}

@Composable
private fun FieldRow(fix: FieldFix, checked: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (checked) 1f else 0.55f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = Space.md + 52.dp + Space.md, end = Space.md, top = Space.xxs, bottom = Space.xxs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fix.field.label.uppercase(), style = EyebrowTextStyle, color = scheme.primary.copy(alpha = alpha))
                Spacer(Modifier.width(Space.sm))
                OriginPill(fix.origin.label)
            }
            if (fix.current != null) {
                Text(
                    text = fix.current,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant.copy(alpha = alpha),
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fix.current != null) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "devient",
                        tint = scheme.onSurfaceVariant.copy(alpha = alpha),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(Space.xs))
                }
                Text(
                    text = fix.proposed,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            fix.doubt?.let { doubt ->
                Text(
                    text = doubt,
                    style = MaterialTheme.typography.labelMedium,
                    color = SynxioTheme.colors.warning,
                )
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = scheme.primary, checkmarkColor = scheme.onPrimary),
        )
    }
}

@Composable
private fun OriginPill(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(SynxioShape.pill)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .padding(horizontal = Space.sm, vertical = Space.xxs),
    )
}

// --- Progression et bilan ------------------------------------------------------------

/**
 * Travail en cours : grand compteur, barre à la signature qui glisse vers sa nouvelle
 * valeur, et des lignes fantômes qui disent « des résultats arrivent ».
 */
@Composable
private fun ProgressPane(
    label: String,
    done: Int,
    total: Int,
    fraction: Float,
    unit: String,
    onCancel: (() -> Unit)?,
) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.standardOrSnap(reduce),
        label = "workshopProgress",
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
                Text("${(animated * 100).toInt()} %", style = MaterialTheme.typography.displayMedium.merge(TabularNumbers))
                Spacer(Modifier.height(Space.md))
                BrandProgressBar(
                    progress = animated,
                    height = 8.dp,
                    modifier = Modifier.semantics { contentDescription = "$label $done sur $total" },
                )
                Spacer(Modifier.height(Space.sm))
                if (total > 0) {
                    Text(
                        "$done / $total $unit",
                        style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        if (onCancel != null) {
            Spacer(Modifier.height(Space.xl))
            GlassButton(
                text = "Arrêter",
                onClick = onCancel,
                icon = Icons.Rounded.Stop,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun DonePane(
    report: WorkshopReport?,
    onUndo: () -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ok = report?.ok ?: 0
    val failed = report?.failed.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Text("TERMINÉ", style = EyebrowTextStyle, color = scheme.primary)
            Text(
                text = if (ok > 1) "$ok fichiers corrigés" else "$ok fichier corrigé",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (report?.backup != null) {
                    "Les originaux sont sauvegardés (${report.backup.totalBytes.asFileSize()}). Tu peux tout annuler pendant 30 jours."
                } else {
                    "Aucune sauvegarde n'a été faite pour cette séance."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        if (failed.isNotEmpty()) {
            item {
                Text("ÉCHECS · ${failed.size}", style = EyebrowTextStyle, color = scheme.error)
            }
            items(failed) { (file, reason) ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(file, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                BrandButton(text = "Terminé", onClick = onClose, icon = Icons.Rounded.TaskAlt, modifier = Modifier.fillMaxWidth())
                if (report?.backup != null && ok > 0) {
                    GlassButton(text = "Annuler ces modifications", onClick = onUndo, icon = Icons.Rounded.Restore, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = onAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Relancer l'analyse")
                }
            }
        }
    }
}

// --- Sauvegardes ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupsSheet(
    backups: List<BackupSession>,
    onRestore: (BackupSession) -> Unit,
    onDelete: (BackupSession) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf<BackupSession?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetHandle() },
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text("SAUVEGARDES", style = EyebrowTextStyle, color = scheme.primary)
            Text(
                "Originaux gardés avant chaque écriture, effacés au bout de 30 jours.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (backups.isEmpty()) {
                Text("Aucune sauvegarde.", style = MaterialTheme.typography.bodyLarge)
            }
            backups.forEach { session ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SynxioShape.card)
                        .glass(SynxioShape.card, strength = 0.6f)
                        .padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.createdAt)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "${session.files.size} fichiers · ${session.totalBytes.asFileSize()}",
                            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirm = session }) {
                        Icon(Icons.Rounded.Restore, contentDescription = "Remettre ces fichiers comme avant")
                    }
                    IconButton(onClick = { onDelete(session) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Supprimer cette sauvegarde", tint = scheme.error)
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
        }
    }
    confirm?.let { session ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remettre ${session.files.size} fichiers comme avant ?") },
            text = { Text("Les tags et pochettes écrits pendant cette séance seront remplacés par les originaux.") },
            confirmButton = { TextButton(onClick = { confirm = null; onRestore(session) }) { Text("Restaurer") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

/** Pastille d'icône des cartes : disque de verre teinté par l'accent. */
@Composable
private fun IconBadge(icon: ImageVector) {
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

private fun plural(count: Int, word: String) = if (count > 1) "$count ${word}s" else "$count $word"
