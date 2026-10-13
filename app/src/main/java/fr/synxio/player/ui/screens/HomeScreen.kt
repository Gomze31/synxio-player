package fr.synxio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel
import java.util.Calendar

/** Nombre de titres montrés dans les carrousels « Reprendre » et « Les plus écoutés ». */
private const val CAROUSEL_SIZE = 15

/** Largeurs des cartes de carrousel : fixes pour que les rangées gardent leur rythme. */
private val SongCardWidth = 140.dp
private val AlbumCardWidth = 160.dp
private val ArtistCardWidth = 104.dp

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onSeeAll: () -> Unit,
    onSeeRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    // Triés par le ViewModel hors du thread principal, jamais pendant la composition.
    val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    // Seulement l'identifiant et l'état lecture/pause : playerState change 4×/s avec la
    // position et recomposerait tout l'accueil pour rien.
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()

    // Premier scan pas encore terminé : un squelette qui a la forme de l'accueil plutôt
    // qu'un rond qui tourne au milieu du vide, l'écran ne « saute » pas à l'arrivée.
    if (library.isLoading && library.songs.isEmpty()) {
        HomeSkeleton(modifier)
        return
    }

    if (library.isEmpty) {
        EmptyState(
            title = "Aucune musique trouvée",
            subtitle = "Ajoute des fichiers audio sur ton appareil (dossier Musique ou Téléchargements), puis relance un scan.",
            icon = Icons.Rounded.LibraryMusic,
            actionLabel = "Relancer le scan",
            onAction = { viewModel.rescan() },
            modifier = modifier,
        )
        return
    }

    val resumeSongs = remember(recentlyPlayed) { recentlyPlayed.take(CAROUSEL_SIZE) }
    val topSongs = remember(mostPlayed) { mostPlayed.take(CAROUSEL_SIZE) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.xl),
    ) {
        item(key = "greeting", contentType = "greeting") {
            GreetingHeader(library.songs.size, library.totalDurationMs)
        }

        item(key = "quick", contentType = "quick") {
            QuickActions(
                canShuffle = library.songs.isNotEmpty(),
                onShuffleAll = { viewModel.shufflePlay(library.songs) },
                favoriteCount = favorites.size,
                onFavorites = { viewModel.play(favorites) },
                // La puce ouvre l'écran des ajouts récents au lieu de lancer la lecture :
                // « qu'est-ce qui est arrivé ? » est une question, pas une commande.
                onRecent = onSeeRecents,
            )
        }

        if (resumeSongs.isNotEmpty()) {
            item(key = "h-resume", contentType = "header") { SectionHeader("Reprendre l'écoute") }
            item(key = "c-resume", contentType = "songCarousel") {
                SongCarousel(
                    songs = resumeSongs,
                    currentSongId = currentSongId,
                    onClick = { song -> viewModel.playSong(song, recentlyPlayed) },
                )
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item(key = "h-added", contentType = "header") {
                SectionHeader(
                    title = "Ajoutés récemment",
                    actionLabel = "Tout voir",
                    onAction = onSeeRecents,
                )
            }
            item(key = "c-added", contentType = "songCarousel") {
                SongCarousel(
                    songs = recentlyAdded,
                    currentSongId = currentSongId,
                    onClick = { song -> viewModel.playSong(song, recentlyAdded) },
                )
            }
        }

        if (topSongs.isNotEmpty()) {
            item(key = "h-top", contentType = "header") { SectionHeader("Les plus écoutés") }
            item(key = "c-top", contentType = "songCarousel") {
                SongCarousel(
                    songs = topSongs,
                    currentSongId = currentSongId,
                    onClick = { song -> viewModel.playSong(song, mostPlayed) },
                )
            }
        }

        if (recentAlbums.isNotEmpty()) {
            item(key = "h-albums", contentType = "header") {
                SectionHeader("Albums", actionLabel = "Tout voir", onAction = onSeeAll)
            }
            item(key = "c-albums", contentType = "albumCarousel") {
                LazyRow(
                    // AlbumCard porte déjà Space.sm de marge interne : gutter = md + sm.
                    contentPadding = PaddingValues(horizontal = Space.md),
                ) {
                    items(recentAlbums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onOpenAlbum(album.id) },
                            modifier = Modifier.width(AlbumCardWidth),
                        )
                    }
                }
            }
        }

        if (topArtists.isNotEmpty()) {
            item(key = "h-artists", contentType = "header") {
                SectionHeader("Artistes", actionLabel = "Tout voir", onAction = onSeeAll)
            }
            item(key = "c-artists", contentType = "artistCarousel") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    items(topArtists, key = { it.name }) { artist ->
                        Column(
                            modifier = Modifier
                                .width(ArtistCardWidth)
                                .clip(SynxioShape.artworkMd)
                                .clickable(
                                    onClickLabel = "Ouvrir l'artiste",
                                    role = Role.Button,
                                ) { onOpenArtist(artist.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Artwork(
                                model = artist.artworkUri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                shape = CircleShape,
                            )
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingHeader(songCount: Int, totalDurationMs: Long) {
    // Calculée une fois par entrée sur l'écran : inutile de relire l'horloge à chaque
    // recomposition.
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Bonjour"
            in 12..17 -> "Bon après-midi"
            in 18..22 -> "Bonsoir"
            else -> "Bonne nuit"
        }
    }

    Column(
        Modifier.padding(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.xl,
            bottom = Space.md,
        )
    ) {
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "${songCount.pluralSongs()} · ${totalDurationMs.asLongDuration()} de musique",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Raccourcis de l'accueil en puces : plus compacts que des tuiles, ils laissent la place
 * aux pochettes, qui sont le vrai contenu de l'écran.
 */
@Composable
private fun QuickActions(
    canShuffle: Boolean,
    onShuffleAll: () -> Unit,
    favoriteCount: Int,
    onFavorites: () -> Unit,
    onRecent: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item(key = "shuffle") {
            QuickActionChip(
                icon = Icons.Rounded.Shuffle,
                label = "Tout mélanger",
                enabled = canShuffle,
                onClick = onShuffleAll,
            )
        }
        item(key = "favorites") {
            // Sans favori, la puce ne lancerait rien : on la désactive plutôt que de
            // laisser un bouton muet.
            QuickActionChip(
                icon = Icons.Rounded.Favorite,
                label = if (favoriteCount > 0) "Favoris · $favoriteCount" else "Favoris",
                enabled = favoriteCount > 0,
                onClick = onFavorites,
            )
        }
        item(key = "recent") {
            QuickActionChip(
                icon = Icons.Rounded.NewReleases,
                label = "Ajouts récents",
                onClick = onRecent,
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        shape = SynxioShape.pill,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        border = null,
    )
}

@Composable
private fun SongCarousel(songs: List<Song>, currentSongId: Long?, onClick: (Song) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        items(songs, key = { it.id }) { song ->
            val isCurrent = song.id == currentSongId
            Column(
                modifier = Modifier
                    .width(SongCardWidth)
                    .clip(SynxioShape.artworkMd)
                    .clickable(onClickLabel = "Lire", role = Role.Button) { onClick(song) }
                    .semantics {
                        if (isCurrent) stateDescription = "En cours de lecture"
                    },
            ) {
                Artwork(
                    model = song.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = SynxioShape.artworkMd,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Squelette du premier chargement : mêmes marges et mêmes proportions que l'accueil
 * réel. Il reste immobile (pas de scintillement) : un chargement n'a pas à attirer l'œil.
 */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Chargement de ta bibliothèque" }
            .padding(horizontal = Space.gutter, vertical = Space.xl),
    ) {
        Box(
            Modifier
                .width(200.dp)
                .height(36.dp)
                .clip(SynxioShape.artworkSm)
                .background(block)
        )
        Spacer(Modifier.height(Space.sm))
        Box(
            Modifier
                .width(160.dp)
                .height(16.dp)
                .clip(SynxioShape.artworkSm)
                .background(block)
        )
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            repeat(3) {
                Box(
                    Modifier
                        .width(112.dp)
                        .height(32.dp)
                        .clip(SynxioShape.pill)
                        .background(block)
                )
            }
        }
        repeat(2) {
            Spacer(Modifier.height(Space.xl + Space.sm))
            Box(
                Modifier
                    .width(180.dp)
                    .height(22.dp)
                    .clip(SynxioShape.artworkSm)
                    .background(block)
            )
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(SongCardWidth)
                            .clip(SynxioShape.artworkMd)
                            .background(block)
                    )
                }
            }
        }
    }
}
