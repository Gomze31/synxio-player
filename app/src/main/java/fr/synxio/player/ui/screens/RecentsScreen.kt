package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.viewmodel.AppViewModel

/**
 * Les ajouts récents, regroupés par ancienneté.
 *
 * La date d'ajout MediaStore est en secondes : c'est la date d'apparition du fichier sur
 * l'appareil, pas la date de sortie du morceau. Les libellés parlent donc d'« ajout »
 * et jamais de « nouveauté », qui laisserait croire à une information éditoriale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    // Identifiant + lecture/pause seulement : playerState bouge 4×/s avec la position.
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    val buckets = remember(library.songs) { bucketize(library.songs) }
    val ordered = remember(buckets) { buckets.flatMap { it.second } }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Ajouts récents", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    // Masquée plutôt que grisée quand il n'y a rien à mélanger : un bouton
                    // inerte dans la barre n'apprend rien à personne.
                    if (ordered.isNotEmpty()) {
                        IconButton(onClick = { viewModel.shufflePlay(ordered) }) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Lecture aléatoire des ajouts récents")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            library.isLoading && library.songs.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            ordered.isEmpty() -> EmptyState(
                title = "Rien de récent",
                subtitle = "Aucun morceau n'a été ajouté à ton appareil ces six derniers mois.",
                icon = Icons.Rounded.NewReleases,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = Space.xl),
            ) {
                buckets.forEach { (label, songs) ->
                    item(key = "header-$label", contentType = "header") {
                        SectionHeader("$label · ${songs.size}")
                    }
                    items(songs, key = { it.id }, contentType = { "song" }) { song ->
                        val isCurrent = currentSongId == song.id
                        SongRow(
                            song = song,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isFavorite = song.id in favorites,
                            onClick = { viewModel.playSong(song, ordered) },
                            onLongClick = { menuSong = song },
                            onMenuClick = { menuSong = song },
                            modifier = Modifier
                                .padding(horizontal = Space.sm)
                                .semantics {
                                    if (isCurrent) stateDescription = "En cours de lecture"
                                },
                        )
                    }
                }
            }
        }
    }

    SongMenuHost(
        viewModel = viewModel,
        song = menuSong,
        onDismiss = { menuSong = null },
        onEditTags = onEditTags,
    )
}

/**
 * Découpe par ancienneté d'ajout.
 *
 * On s'arrête à six mois : au-delà, « récent » ne veut plus rien dire et l'écran ferait
 * doublon avec la bibliothèque triée par date.
 */
private fun bucketize(songs: List<Song>): List<Pair<String, List<Song>>> {
    val nowSec = System.currentTimeMillis() / 1000
    val day = 24L * 3600

    val ranges = listOf(
        "Aujourd'hui" to day,
        "Cette semaine" to 7 * day,
        "Ce mois-ci" to 30 * day,
        "Ces trois derniers mois" to 90 * day,
        "Ces six derniers mois" to 180 * day,
    )

    val recent = songs
        .filter { it.dateAddedSec > 0 && nowSec - it.dateAddedSec <= 180 * day }
        .sortedByDescending { it.dateAddedSec }

    var remaining = recent
    return buildList {
        ranges.forEach { (label, maxAge) ->
            val (inBucket, rest) = remaining.partition { nowSec - it.dateAddedSec <= maxAge }
            if (inBucket.isNotEmpty()) add(label to inBucket)
            remaining = rest
        }
    }
}
