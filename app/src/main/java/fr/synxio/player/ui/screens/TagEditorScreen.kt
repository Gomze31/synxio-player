package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.repo.MetadataMatch
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.TagEditorViewModel

private val HeaderArtworkSize = 72.dp
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
@Composable
fun TagEditorScreen(
    songId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: ((String) -> Unit)? = null,
) {
    val viewModel: TagEditorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Modifier les tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.song != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        when {
                            state.saving -> Unit
                            formValid -> viewModel.save()
                            else -> triedInvalid = true
                        }
                    },
                    icon = {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ButtonProgressSize),
                                strokeWidth = ButtonProgressStroke,
                            )
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                        }
                    },
                    text = { Text(if (state.saving) "Enregistrement…" else "Enregistrer") },
                )
            }
        },
    ) { padding ->
        val song = state.song
        if (state.loading || song == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                Alignment.Center,
            ) { CircularProgressIndicator(Modifier.semantics { contentDescription = "Chargement" }) }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
        ) {
            Row(
                Modifier.padding(vertical = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.artworkUri, Modifier.size(HeaderArtworkSize), SynxioShape.artworkSm)
                Spacer(Modifier.width(Space.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.folderPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Prévenir avant la saisie, pas après : les tags ne restent pas dans Synxio,
            // ils modifient le fichier lui-même (visible par les autres lecteurs, copié
            // avec lui).
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = SynxioShape.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.md),
            ) {
                Row(
                    Modifier.padding(Space.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "Les modifications sont écrites dans le fichier d'origine : " +
                            "elles seront visibles dans les autres applis et suivront le fichier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Les fichiers rippés ont des tags approximatifs : plutôt que de tout
            // ressaisir à la main, on va chercher la fiche officielle.
            val busy = state.searching || state.fetchingArtwork
            OutlinedButton(
                onClick = { viewModel.searchOnline() },
                enabled = !busy,
                shape = SynxioShape.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .padding(bottom = Space.sm),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonProgressSize),
                        strokeWidth = ButtonProgressStroke,
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(if (state.fetchingArtwork) "Téléchargement…" else "Recherche…")
                } else {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Rechercher les infos et la pochette")
                }
            }

            TagField("Titre", state.edit.title) { v -> viewModel.update { it.copy(title = v) } }
            TagField("Artiste", state.edit.artist) { v -> viewModel.update { it.copy(artist = v) } }
            TagField("Album", state.edit.album) { v -> viewModel.update { it.copy(album = v) } }
            TagField("Artiste de l'album", state.edit.albumArtist) { v ->
                viewModel.update { it.copy(albumArtist = v) }
            }
            TagField("Genre", state.edit.genre) { v -> viewModel.update { it.copy(genre = v) } }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
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
            if (triedInvalid && !formValid) {
                Text(
                    "Corrige les champs en rouge avant d'enregistrer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = Space.sm),
                )
            }

            TagField("Commentaire", state.edit.comment) { v ->
                viewModel.update { it.copy(comment = v) }
            }
            TagField("Paroles", state.edit.lyrics, singleLine = false) { v ->
                viewModel.update { it.copy(lyrics = v) }
            }

            // Laisse le bouton flottant au-dessus du dernier champ.
            Spacer(Modifier.height(Space.xxxl + Space.xxxl))
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

/** Liste des correspondances trouvées en ligne, avec pochette et indice de confiance. */
@Composable
private fun MetadataMatchSheet(
    matches: List<MetadataMatch>,
    onSelect: (MetadataMatch, Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var withArtwork by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = Space.xl)) {
            Text(
                text = "Correspondances trouvées",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = withArtwork, role = Role.Switch, onValueChange = { withArtwork = it })
                    .heightIn(min = Space.touchTarget)
                    .padding(horizontal = Space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Importer aussi la pochette", Modifier.weight(1f))
                Switch(checked = withArtwork, onCheckedChange = null)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Affiner la recherche") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { if (query.isNotBlank()) onSearch(query) }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Chercher")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xl, vertical = Space.sm),
            )

            HorizontalDivider(Modifier.padding(vertical = Space.xs))

            LazyColumn(Modifier.heightIn(max = MatchListMaxHeight)) {
                items(matches) { match ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Appliquer cette fiche", role = Role.Button) {
                                onSelect(match, withArtwork)
                            }
                            .padding(horizontal = Space.xl, vertical = Space.sm),
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${match.durationSec / 60}:${"%02d".format(match.durationSec % 60)}" +
                                    " · fiabilité ${match.confidence} %",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = if (match.confidence >= 80) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
    )
}
