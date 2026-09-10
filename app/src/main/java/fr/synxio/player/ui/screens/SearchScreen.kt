package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.ArtistCard
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GenreRow
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.viewmodel.AppViewModel

@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Titre, artiste, album, genre…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )

        when {
            query.isBlank() -> EmptyState(
                title = "Cherche dans ta bibliothèque",
                subtitle = "La recherche ignore les accents et tolère les fautes de frappe.",
                icon = Icons.Rounded.Search,
            )

            results.isEmpty -> EmptyState(
                title = "Aucun résultat",
                subtitle = "Rien ne correspond à « $query ».",
                icon = Icons.Rounded.Search,
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (results.artists.isNotEmpty()) {
                    item { SectionHeader("Artistes") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(results.artists, key = { it.name }) { artist ->
                                ArtistCard(
                                    artist = artist,
                                    onClick = { onOpenArtist(artist.name) },
                                    modifier = Modifier.width(110.dp),
                                )
                            }
                        }
                    }
                }

                if (results.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(results.albums, key = { it.id }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { onOpenAlbum(album.id) },
                                    modifier = Modifier.width(150.dp),
                                )
                            }
                        }
                    }
                }

                if (results.genres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    items(results.genres, key = { it.name }) { genre ->
                        GenreRow(
                            genre = genre,
                            onClick = { onOpenGenre(genre.name) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }

                if (results.songs.isNotEmpty()) {
                    item { SectionHeader("Titres") }
                    items(results.songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            onClick = { viewModel.playSong(song, results.songs) },
                            isCurrent = playerState.currentSong?.id == song.id,
                            isPlaying = playerState.isPlaying,
                            isFavorite = song.id in favorites,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            onLongClick = { menuSong = song },
                            onMenuClick = { menuSong = song },
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
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    )
}
