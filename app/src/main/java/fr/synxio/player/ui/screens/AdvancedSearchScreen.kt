package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

/** Délai sans frappe avant de relancer le filtre. */
private const val FILTER_DEBOUNCE_MS = 150L

/**
 * Écran de recherche avancée - version simplifiée.
 */
@Composable
fun AdvancedSearchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val library by viewModel.library.collectAsStateWithLifecycle()

    // Le filtre tourne hors du thread principal et ne part qu'une fois la frappe posée :
    // produceState annule le calcul précédent à chaque nouvelle requête.
    val filteredSongs by produceState(initialValue = library.songs, query, library) {
        if (query.isNotBlank()) delay(FILTER_DEBOUNCE_MS)
        value = viewModel.filterSongs(query, library)
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
                // fillMaxSize faisait occuper tout l'écran au champ et poussait les
                // résultats hors de la vue.
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Rechercher") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
            )

            Spacer(Modifier.height(16.dp))

            Text("Résultats: ${filteredSongs.size} morceaux")

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(filteredSongs, index) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
