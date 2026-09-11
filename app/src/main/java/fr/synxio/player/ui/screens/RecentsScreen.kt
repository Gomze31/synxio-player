package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.viewmodel.AppViewModel

/**
 * Les ajouts récents, regroupés par ancienneté.
 *
 * La date d'ajout MediaStore est en secondes : c'est la date d'apparition du fichier sur
 * l'appareil, pas la date de sortie du morceau. Les libellés parlent donc d'« ajout »
 * et jamais de « nouveauté », qui laisserait croire à une information éditoriale.
 */
@Composable
fun RecentsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val buckets = remember(library.songs) { bucketize(library.songs) }
    val ordered = remember(buckets) { buckets.flatMap { it.second } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ajouts récents") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.shufflePlay(ordered) },
                        enabled = ordered.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Lecture aléatoire")
                    }
                },
            )
        },
    ) { padding ->
        if (ordered.isEmpty()) {
            EmptyState(
                title = "Rien de récent",
                subtitle = "Aucun morceau n'a été ajouté à ton appareil ces six derniers mois.",
                icon = Icons.Rounded.NewReleases,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            buckets.forEach { (label, songs) ->
                item(key = "header-$label") {
                    Column {
                        SectionHeader("$label · ${songs.size}")
                    }
                }
                items(songs, key = { it.id }) { song ->
                    val isCurrent = playerState.currentSong?.id == song.id
                    SongRow(
                        song = song,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playerState.isPlaying,
                        isFavorite = song.id in favorites,
                        onClick = { viewModel.playSong(song, ordered) },
                        onMenuClick = { onEditTags(song.id) },
                    )
                }
            }
        }
    }
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
