package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.repo.MetadataMatch
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.SynxioSnackbar
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.components.shimmer
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.TagEditorViewModel

private val HeaderArtworkSize = 168.dp
private val MatchArtworkSize = 56.dp
private val ButtonProgressSize = 18.dp
private val ButtonProgressStroke = 2.dp
private val MatchListMaxHeight = 420.dp

/** Validation locale des champs numériques : erreur à afficher, ou null. */
private fun numberError(value: String?, allowSlash: Boolean): String? {
    val v = value?.trim().orEmpty()
    if (v.isEmpty()) return null
    val ok = if (allowSlash) v.matches(Regex("""\d+(/\d+)?""")) else v.all(Char::isDigit)
    return when {
        ok -> null
        allowSlash -> "Nombre attendu (ex. 3 ou 3/12)."
        else -> "Nombre attendu."
    }
}

/**
 * Éditeur de tags d'un titre.
 *
 * Le ViewModel de cet écran est local (pas d'accès à AppViewModel) : ses messages passent
 * par un SnackbarHost propre à l'écran, sauf si l'appelant fournit [onMessage] pour les
 * remonter au scaffold racine — utile pour « Tags enregistrés », émis juste avant que
 * l'écran ne se ferme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(
    songId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: ((String) -> Unit)? = null,
) {
    val viewModel: TagEditorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(songId) { viewModel.load(songId) }

    // Android 11+ : l'écriture d'un fichier dont on n'est pas propriétaire passe par
    // une boîte de dialogue système. On relance la sauvegarde une fois accordée.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK) }

    LaunchedEffect(state.consentRequest) {
        state.consentRequest?.let { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            viewModel.consumeMessage()
            if (onMessage != null) onMessage(it) else snackbarHost.showSnackbar(it)
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // Un champ numérique invalide est signalé sous le champ dès la saisie ; le rappel
    // global n'apparaît qu'après une tentative d'enregistrement refusée.
    val yearError = numberError(state.edit.year, allowSlash = false)
    val trackError = numberError(state.edit.track, allowSlash = true)
    val discError = numberError(state.edit.disc, allowSlash = true)
    var triedInvalid by rememberSaveable { mutableStateOf(false) }
    val formValid = yearError == null && trackError == null && discError == null

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) { SynxioSnackbar(it) } },
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Modifier les tags", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.song != null,
                enter = fadeIn(Motion.enter()),
                exit = fadeOut(Motion.exit()),
            ) {
                // Pendant l'écriture, le bouton reste en place mais s'éteint : un second
                // toucher ne relance rien, et le libellé dit ce qui se passe.
                BrandButton(
                    text = if (state.saving) "Enregistrement…" else "Enregistrer",
                    icon = Icons.Rounded.Save,
                    enabled = !state.saving,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    onClick = {
                        when {
                            state.saving -> Unit
                            formValid -> viewModel.save()
                            else -> triedInvalid = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        val song = state.song
        AnimatedContent(
            targetState = state.loading || song == null,
            transitionSpec = {
                fadeIn(Motion.enter(if (reduceMotion) Motion.XS else Motion.MEDIUM)) togetherWith
                    fadeOut(Motion.exit(Motion.XS))
            },
            label = "tagEditorLoading",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { loading ->
            if (loading || song == null) {
                LoadingSkeleton()
                return@AnimatedContent
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                ArtworkHeader(
                    artwork = song.artworkUri,
                    fileName = song.fileName,
                    folder = song.folderPath,
                )

                // Prévenir avant la saisie, pas après : les tags ne restent pas dans Synxio,
                // ils modifient le fichier lui-même (visible par les autres lecteurs, copié
                // avec lui).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SynxioShape.cardSm)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(Space.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "Les modifications sont écrites dans le fichier d'origine : " +
                            "elles seront visibles dans les autres applis et suivront le fichier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Les fichiers rippés ont des tags approximatifs : plutôt que de tout
                // ressaisir à la main, on va chercher la fiche officielle.
                val busy = state.searching || state.fetchingArtwork
                AnimatedContent(
                    targetState = busy,
                    transitionSpec = { fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.XS)) },
                    label = "searchOnline",
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { isBusy ->
                    if (isBusy) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Space.touchTarget)
                                .clip(SynxioShape.pill)
                                .glass(SynxioShape.pill, strength = 0.8f)
                                .padding(horizontal = Space.xl, vertical = Space.md)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ButtonProgressSize),
                                strokeWidth = ButtonProgressStroke,
                                color = SynxioTheme.colors.brandEnd,
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                if (state.fetchingArtwork) "Téléchargement…" else "Recherche…",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        GlassButton(
                            text = "Rechercher les infos et la pochette",
                            onClick = { viewModel.searchOnline() },
                            icon = Icons.Rounded.CloudDownload,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                FieldGroup("Morceau", index = 0) {
                    TagField("Titre", state.edit.title) { v -> viewModel.update { it.copy(title = v) } }
                    TagField("Artiste", state.edit.artist) { v -> viewModel.update { it.copy(artist = v) } }
                    TagField("Genre", state.edit.genre) { v -> viewModel.update { it.copy(genre = v) } }
                    Text(
                        "Note",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                    fr.synxio.player.ui.components.StarRating(
                        rating = state.song?.id?.let { ratings[it] } ?: 0,
                        onRate = viewModel::rate,
                        modifier = Modifier.offset(x = (-9).dp),
                    )
                }

                FieldGroup("Album", index = 1) {
                    TagField("Album", state.edit.album) { v -> viewModel.update { it.copy(album = v) } }
                    TagField("Artiste de l'album", state.edit.albumArtist) { v ->
                        viewModel.update { it.copy(albumArtist = v) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Box(Modifier.weight(1f)) {
                            TagField(
                                "Année", state.edit.year, KeyboardType.Number,
                                error = yearError,
                            ) { v -> viewModel.update { it.copy(year = v) } }
                        }
                        Box(Modifier.weight(1f)) {
                            TagField(
                                "Piste", state.edit.track, KeyboardType.Number,
                                error = trackError,
                            ) { v -> viewModel.update { it.copy(track = v) } }
                        }
                        Box(Modifier.weight(1f)) {
                            TagField(
                                "Disque", state.edit.disc, KeyboardType.Number,
                                error = discError,
                            ) { v -> viewModel.update { it.copy(disc = v) } }
                        }
                    }
                    AnimatedVisibility(
                        visible = triedInvalid && !formValid,
                        enter = fadeIn(Motion.enter(Motion.SHORT)) + expandVertically(Motion.spatialOrSnap(reduceMotion)),
                        exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.spatialOrSnap(reduceMotion)),
                    ) {
                        Row(
                            Modifier
                                .padding(top = Space.xs)
                                .semantics { liveRegion = LiveRegionMode.Assertive },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                "Corrige les champs en rouge avant d'enregistrer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                FieldGroup("Notes", index = 2) {
                    TagField("Commentaire", state.edit.comment) { v ->
                        viewModel.update { it.copy(comment = v) }
                    }
                    TagField("Paroles", state.edit.lyrics, singleLine = false) { v ->
                        viewModel.update { it.copy(lyrics = v) }
                    }
                }

                // Laisse le bouton flottant au-dessus du dernier champ.
                Spacer(Modifier.height(Space.xxxl + Space.xxxl))
            }
        }
    }

    if (state.matches.isNotEmpty()) {
        MetadataMatchSheet(
            matches = state.matches,
            onSelect = { match, withArtwork -> viewModel.applyMatch(match, withArtwork) },
            onSearch = { viewModel.searchOnline(it) },
            onDismiss = viewModel::dismissMatches,
        )
    }
}

/**
 * En-tête : la pochette du fichier mise en scène, posée sur sa propre lueur, puis le nom
 * du fichier et son dossier. On sait d'emblée quel fichier on est en train de réécrire.
 */
@Composable
private fun ArtworkHeader(artwork: Any?, fileName: String, folder: String) {
    // Pas les couleurs de LocalArtworkColors : elles décrivent le titre en lecture, qui
    // n'est pas forcément celui qu'on édite. La signature reste neutre et toujours juste.
    val glowColor = SynxioTheme.colors.brandEnd
    val glow = LocalGlowEffects.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.md, bottom = Space.sm)
            .enterCascade(0, key = "tag-header"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (glow) {
                Box(
                    Modifier
                        .size(HeaderArtworkSize + 80.dp)
                        .background(
                            Brush.radialGradient(listOf(glowColor.copy(alpha = 0.30f), Color.Transparent))
                        )
                )
            }
            Artwork(
                model = artwork,
                modifier = Modifier
                    .size(HeaderArtworkSize)
                    .glowShadow(SynxioShape.artworkLg, glowColor, elevation = 28.dp),
                shape = SynxioShape.artworkLg,
                contentDescription = "Pochette du fichier",
            )
        }
        Spacer(Modifier.height(Space.md))
        Text(
            fileName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = folder,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Groupe de champs : une carte de verre coiffée d'un surtitre. */
@Composable
private fun FieldGroup(title: String, index: Int, content: @Composable () -> Unit) {
    GlassCard(
        Modifier
            .fillMaxWidth()
            .enterCascade(index + 1, key = "tag-group-$title"),
        contentPadding = Space.md,
    ) {
        Column {
            Text(
                title.uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = Space.xs, bottom = Space.xs)
                    .semantics { heading() },
            )
            content()
        }
    }
}

/** Chargement : la silhouette de l'écran (pochette, champs) qui scintille doucement. */
@Composable
private fun LoadingSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter, vertical = Space.md)
            .semantics { contentDescription = "Chargement" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(HeaderArtworkSize).clip(SynxioShape.artworkLg).shimmer())
        Spacer(Modifier.height(Space.md))
        Box(Modifier.fillMaxWidth(0.6f).height(18.dp).clip(SynxioShape.pill).shimmer())
        Spacer(Modifier.height(Space.sm))
        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(SynxioShape.pill).shimmer())
        Spacer(Modifier.height(Space.xl))
        repeat(2) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.md)
                    .clip(SynxioShape.card)
                    .glass(SynxioShape.card, strength = 0.35f)
                    .padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                repeat(3) {
                    Box(Modifier.fillMaxWidth().height(52.dp).clip(SynxioShape.cardSm).shimmer())
                }
            }
        }
    }
}

/** Liste des correspondances trouvées en ligne, avec pochette et indice de confiance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataMatchSheet(
    matches: List<MetadataMatch>,
    onSelect: (MetadataMatch, Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var withArtwork by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerLow,
        shape = SynxioShape.sheet,
    ) {
        Column(Modifier.padding(bottom = Space.xl)) {
            Column(Modifier.padding(horizontal = Space.xl, vertical = Space.sm)) {
                Text("EN LIGNE", style = EyebrowTextStyle, color = scheme.primary)
                Text(
                    text = "Correspondances trouvées",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = withArtwork, role = Role.Switch, onValueChange = { withArtwork = it })
                    .heightIn(min = Space.touchTarget)
                    .padding(horizontal = Space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Importer aussi la pochette", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = withArtwork,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Affiner la recherche") },
                singleLine = true,
                shape = SynxioShape.cardSm,
                colors = glassFieldColors(),
                trailingIcon = {
                    IconButton(onClick = { if (query.isNotBlank()) onSearch(query) }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Chercher")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xl, vertical = Space.sm),
            )

            LazyColumn(
                Modifier.heightIn(max = MatchListMaxHeight),
                contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                itemsIndexed(matches) { index, match ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .enterCascade(index, key = "match-$index-${match.title}")
                            .pressBounce(0.98f)
                            .clip(SynxioShape.cardSm)
                            .clickable(onClickLabel = "Appliquer cette fiche", role = Role.Button) {
                                onSelect(match, withArtwork)
                            }
                            .padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            model = match.artworkUrl,
                            modifier = Modifier.size(MatchArtworkSize),
                            shape = SynxioShape.artworkSm,
                        )
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = match.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${match.artist} · ${match.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(Space.xs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Jauge de fiabilité à la signature : l'œil compare les fiches
                                // sans lire les pourcentages.
                                BrandProgressBar(
                                    progress = match.confidence / 100f,
                                    height = 4.dp,
                                    modifier = Modifier.width(36.dp),
                                )
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    text = "${match.durationSec / 60}:${"%02d".format(match.durationSec % 60)}" +
                                        " · fiabilité ${match.confidence} %",
                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                                    color = if (match.confidence >= 80) scheme.primary else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagField(
    label: String,
    value: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = SynxioShape.cardSm,
        colors = glassFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
    )
}

/** Champ de saisie en verre : fond translucide, liseré discret, accent au focus. */
@Composable
private fun glassFieldColors() = MaterialTheme.colorScheme.let { scheme ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surfaceContainerHighest.copy(alpha = 0.55f),
        unfocusedContainerColor = scheme.surfaceContainerHighest.copy(alpha = 0.32f),
        errorContainerColor = scheme.errorContainer.copy(alpha = 0.25f),
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.6f),
        focusedLabelColor = scheme.primary,
        cursorColor = scheme.primary,
    )
}
