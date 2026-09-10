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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import fr.synxio.player.ui.viewmodel.AppViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()

    if (library.isLoading && library.songs.isEmpty()) {
        Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (library.isEmpty) {
        EmptyState(
            title = "Aucune musique trouvée",
            subtitle = "Ajoute des fichiers audio sur ton appareil, puis lance un nouveau scan.",
            actionLabel = "Relancer le scan",
            onAction = { viewModel.rescan() },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { GreetingHeader(library.songs.size, library.totalDurationMs) }

        item {
            QuickActions(
                onShuffleAll = { viewModel.shufflePlay(library.songs) },
                onFavorites = { viewModel.play(favorites) },
                favoriteCount = favorites.size,
                onRecent = { viewModel.play(recentlyAdded) },
            )
        }

        if (recentlyPlayed.isNotEmpty()) {
            item { SectionHeader("Reprendre l'écoute") }
            item {
                SongCarousel(
                    songs = recentlyPlayed.take(15),
                    onClick = { song -> viewModel.playSong(song, recentlyPlayed) },
                )
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item { SectionHeader("Ajoutés récemment", actionLabel = "Tout voir", onAction = onSeeAll) }
            item {
                SongCarousel(
                    songs = recentlyAdded,
                    onClick = { song -> viewModel.playSong(song, recentlyAdded) },
                )
            }
        }

        if (mostPlayed.isNotEmpty()) {
            item { SectionHeader("Les plus écoutés") }
            item {
                SongCarousel(
                    songs = mostPlayed.take(15),
                    onClick = { song -> viewModel.playSong(song, mostPlayed) },
                )
            }
        }

        item { SectionHeader("Albums", actionLabel = "Tout voir", onAction = onSeeAll) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(library.albums.sortedByDescending { it.dateAddedSec }.take(20), key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        onClick = { onOpenAlbum(album.id) },
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
        }

        item { SectionHeader("Artistes", actionLabel = "Tout voir", onAction = onSeeAll) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(library.artists.sortedByDescending { it.songCount }.take(20), key = { it.name }) { artist ->
                    Column(
                        modifier = Modifier
                            .width(104.dp)
                            .clickable { onOpenArtist(artist.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Artwork(
                            model = artist.artworkUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                        Spacer(Modifier.height(8.dp))
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

@Composable
private fun GreetingHeader(songCount: Int, totalDurationMs: Long) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        in 18..22 -> "Bonne soirée"
        else -> "Bonne nuit"
    }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Text(greeting, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${songCount.pluralSongs()} · ${totalDurationMs.asLongDuration()} de musique",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickActions(
    onShuffleAll: () -> Unit,
    onFavorites: () -> Unit,
    favoriteCount: Int,
    onRecent: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionTile(
            icon = Icons.Rounded.Shuffle,
            label = "Tout mélanger",
            modifier = Modifier.weight(1f),
            onClick = onShuffleAll,
        )
        QuickActionTile(
            icon = Icons.Rounded.Favorite,
            label = "Favoris",
            badge = favoriteCount.takeIf { it > 0 }?.toString(),
            modifier = Modifier.weight(1f),
            onClick = onFavorites,
        )
        QuickActionTile(
            icon = Icons.Rounded.NewReleases,
            label = "Nouveautés",
            modifier = Modifier.weight(1f),
            onClick = onRecent,
        )
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.22f),
                        scheme.tertiary.copy(alpha = 0.12f),
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (badge != null) {
            Text(badge, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SongCarousel(songs: List<Song>, onClick: (Song) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(songs, key = { it.id }) { song ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onClick(song) },
            ) {
                Artwork(
                    model = song.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
