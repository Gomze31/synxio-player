package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.viewmodel.AppViewModel

/**
 * Écran de recherche avancée - version simplifiée.
 */
@Composable
fun AdvancedSearchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onPlaySong: (List<fr.synxio.player.data.model.Song>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val allSongs = viewModel.library.value.songs
    val filteredSongs = remember(query) {
        if (query.isBlank()) allSongs
        else allSongs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true) ||
            song.album.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Recherche avancée") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxSize(),
                label = { Text("Rechercher") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
            )

            Spacer(Modifier.height(16.dp))

            Text("Résultats: ${filteredSongs.size} morceaux")

            Spacer(Modifier.height(8.dp))

            filteredSongs.take(50).forEach { song ->
                SongRow(
                    song = song,
                    onClick = { onPlaySong(filteredSongs, filteredSongs.indexOf(song)) },
                    
                    
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
