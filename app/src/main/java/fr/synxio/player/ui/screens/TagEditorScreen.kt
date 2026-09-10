package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.synxio.player.data.repo.MetadataMatch
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.viewmodel.TagEditorViewModel

@Composable
fun TagEditorScreen(songId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TagEditorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

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
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Modifier les tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::save,
                icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                text = { Text(if (state.saving) "Enregistrement…" else "Enregistrer") },
            )
        },
    ) { padding ->
        val song = state.song
        if (state.loading || song == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.artworkUri, Modifier.size(72.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(song.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        text = song.folderPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            // Les fichiers rippés ont des tags approximatifs : plutôt que de tout
            // ressaisir à la main, on va chercher la fiche officielle.
            OutlinedButton(
                onClick = { viewModel.searchOnline() },
                enabled = !state.searching && !state.fetchingArtwork,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                if (state.searching || state.fetchingArtwork) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.fetchingArtwork) "Téléchargement…" else "Recherche…")
                } else {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    TagField("Année", state.edit.year, KeyboardType.Number) { v ->
                        viewModel.update { it.copy(year = v) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    TagField("Piste", state.edit.track, KeyboardType.Number) { v ->
                        viewModel.update { it.copy(track = v) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    TagField("Disque", state.edit.disc, KeyboardType.Number) { v ->
                        viewModel.update { it.copy(disc = v) }
                    }
                }
            }

            TagField("Commentaire", state.edit.comment) { v ->
                viewModel.update { it.copy(comment = v) }
            }
            TagField("Paroles", state.edit.lyrics, singleLine = false) { v ->
                viewModel.update { it.copy(lyrics = v) }
            }

            Spacer(Modifier.height(96.dp))
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
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Correspondances trouvées",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Importer aussi la pochette", Modifier.weight(1f))
                Switch(checked = withArtwork, onCheckedChange = { withArtwork = it })
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
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(matches) { match ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(match, withArtwork) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            model = match.artworkUrl,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = match.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                text = "${match.artist} · ${match.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                text = "${match.durationSec / 60}:${"%02d".format(match.durationSec % 60)}" +
                                    " · fiabilité ${match.confidence} %",
                                style = MaterialTheme.typography.labelSmall,
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
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}
