package fr.synxio.player.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.viewmodel.AppViewModel

/**
 * Menu contextuel d'un titre, factorisé : chaque écran n'a qu'à mémoriser le morceau
 * sélectionné et à appeler ce composable une fois.
 */
@Composable
fun SongMenuHost(
    viewModel: AppViewModel,
    song: Song?,
    onDismiss: () -> Unit,
    onOpenAlbum: ((Long) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    onEditTags: ((Long) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    if (song == null) return

    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var showPlaylistPicker by remember { mutableStateOf(false) }

    SongOptionsSheet(
        song = song,
        isFavorite = song.id in favorites,
        actions = SongActions(
            onPlayNext = { viewModel.playNext(listOf(song)) },
            onAddToQueue = { viewModel.addToQueue(listOf(song)) },
            onStartRadio = { viewModel.startRadio(song) },
            onToggleFavorite = { viewModel.toggleFavorite(song) },
            onAddToPlaylist = { showPlaylistPicker = true },
            onOpenAlbum = onOpenAlbum?.let { { it(song.albumId) } },
            onOpenArtist = onOpenArtist?.let { { it(song.displayArtist) } },
            onEditTags = onEditTags?.let { { it(song.id) } },
            onShare = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = song.mimeType.ifBlank { "audio/*" }
                    putExtra(Intent.EXTRA_STREAM, song.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, "Partager « ${song.title} »"))
            },
            onRemoveFromPlaylist = onRemoveFromPlaylist,
        ),
        onDismiss = onDismiss,
    )

    if (showPlaylistPicker) {
        AddToPlaylistSheet(
            playlists = playlists,
            onSelect = { viewModel.addToPlaylist(it.id, listOf(song)) },
            onCreate = { viewModel.createPlaylist(it, listOf(song)) },
            onDismiss = { showPlaylistPicker = false; onDismiss() },
        )
    }
}
