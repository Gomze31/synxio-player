package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Ce que montre l'écran : chargement, rien de récent ou la liste par ancienneté. */
private enum class RecentsPane { LOADING, EMPTY, LIST }

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
    val reduceMotion = LocalReduceMotion.current
    var menuSong by remember { mutableStateOf<Song?>(null) }

    val buckets = remember(library.songs) { bucketize(library.songs) }
    val ordered = remember(buckets) { buckets.flatMap { it.second } }

    val pane = when {
        library.isLoading && library.songs.isEmpty() -> RecentsPane.LOADING
        ordered.isEmpty() -> RecentsPane.EMPTY
        else -> RecentsPane.LIST
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Ajouts récents", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    // Reste à portée une fois le bouton principal défilé hors de l'écran.
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
        AnimatedContent(
            targetState = pane,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                if (reduceMotion) fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(snap())
                else fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit())
            },
            label = "recentsPane",
        ) { target ->
            when (target) {
                // Squelette plutôt qu'un rond qui tourne : la liste arrive à sa place.
                RecentsPane.LOADING -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.sm, vertical = Space.md)
                        .semantics { contentDescription = "Chargement des ajouts récents" },
                ) { repeat(8) { SkeletonSongRow() } }

                RecentsPane.EMPTY -> EmptyState(
                    title = "Rien de récent",
                    subtitle = "Aucun morceau n'a été ajouté à ton appareil ces six derniers mois.",
                    icon = Icons.Rounded.NewReleases,
                )

                RecentsPane.LIST -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Space.bottomInset + Space.xl),
                ) {
                    item(key = "actions", contentType = "actions") {
                        RecentsActions(
                            count = ordered.size,
                            onShuffle = { viewModel.shufflePlay(ordered) },
                            onPlayAll = { viewModel.play(ordered) },
                            modifier = Modifier.enterCascade(0, "recents-actions"),
                        )
                    }
                    // Rang global de la ligne : seuls les premiers titres de l'écran entrent
                    // en cascade, pas chaque ligne révélée au défilement.
                    var rank = 0
                    buckets.forEach { (label, songs) ->
                        item(key = "header-$label", contentType = "header") {
                            SectionHeader(
                                title = label,
                                eyebrow = songs.size.pluralSongs(),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        val base = rank
                        rank += songs.size
                        itemsIndexed(songs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { i, song ->
                            val isCurrent = currentSongId == song.id
                            val position = base + i
                            SongRow(
                                song = song,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isFavorite = song.id in favorites,
                                onClick = { viewModel.playSong(song, ordered) },
                                onLongClick = { menuSong = song },
                                onMenuClick = { menuSong = song },
                                modifier = Modifier
                                    .animateItem()
                                    .then(
                                        if (position < Motion.MAX_STAGGERED) Modifier.enterCascade(position + 1, song.id)
                                        else Modifier
                                    )
                                    .padding(horizontal = Space.sm),
                            )
                        }
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

/** Résumé et actions principales : mélanger (le geste attendu ici) ou tout lire dans l'ordre. */
@Composable
private fun RecentsActions(
    count: Int,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
        Text(
            text = "${count.pluralSongs()} en six mois",
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandButton(text = "Lecture aléatoire", onClick = onShuffle, icon = Icons.Rounded.Shuffle)
            GlassButton(text = "Tout lire", onClick = onPlayAll, icon = Icons.Rounded.PlayArrow)
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
