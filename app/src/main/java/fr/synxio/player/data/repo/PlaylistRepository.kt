package fr.synxio.player.data.repo

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.db.PlaylistDao
import fr.synxio.player.data.db.PlaylistEntity
import fr.synxio.player.data.db.PlaylistSongEntity
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PlaylistDao,
    private val musicRepository: MusicRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * Les playlists ne stockent que des ids : on les recroise avec la bibliothèque
     * pour que les morceaux supprimés disparaissent tout seuls.
     */
    val playlists: StateFlow<List<Playlist>> = combine(
        dao.observePlaylists(),
        dao.observeAllEntries(),
        musicRepository.songsById,
    ) { playlists, entries, index ->
        val entriesByPlaylist = entries.groupBy { it.playlistId }
        playlists.map { entity ->
            val songs = entriesByPlaylist[entity.id]
                .orEmpty()
                .sortedBy { it.position }
                .mapNotNull { index[it.songId] }
            entity.toPlaylist(songs)
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun observePlaylist(id: Long): Flow<Playlist?> = combine(
        dao.observePlaylist(id),
        dao.observeEntries(id),
        musicRepository.songsById,
    ) { entity, entries, index ->
        entity?.toPlaylist(entries.sortedBy { it.position }.mapNotNull { index[it.songId] })
    }

    private fun PlaylistEntity.toPlaylist(songs: List<Song>) =
        Playlist(id, name, createdAt, updatedAt, songs)

    suspend fun create(name: String, songs: List<Song> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
        if (songs.isNotEmpty()) addSongs(id, songs)
        return id
    }

    suspend fun addSongs(playlistId: Long, songs: List<Song>) {
        if (songs.isEmpty()) return
        val start = dao.lastPosition(playlistId) + 1
        dao.insertEntries(
            songs.mapIndexed { i, song ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = song.id,
                    songPath = song.path,
                    position = start + i,
                )
            }
        )
        dao.touch(playlistId, System.currentTimeMillis())
    }

    suspend fun removeSong(playlistId: Long, songId: Long) {
        dao.removeSong(playlistId, songId)
        // Les positions deviennent trouées : on renumérote pour garder un ordre propre.
        val remaining = dao.entries(playlistId).sortedBy { it.position }
        dao.replaceEntries(
            playlistId,
            remaining.mapIndexed { i, e -> e.copy(rowId = 0, position = i) },
            System.currentTimeMillis(),
        )
    }

    suspend fun reorder(playlistId: Long, songIds: List<Long>) {
        val index = musicRepository.songsById.value
        dao.replaceEntries(
            playlistId,
            songIds.mapIndexedNotNull { i, id ->
                val song = index[id] ?: return@mapIndexedNotNull null
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = id,
                    songPath = song.path,
                    position = i,
                )
            },
            System.currentTimeMillis(),
        )
    }

    suspend fun rename(playlistId: Long, name: String) =
        dao.rename(playlistId, name, System.currentTimeMillis())

    suspend fun delete(playlistId: Long) = dao.deletePlaylist(playlistId)

    suspend fun duplicate(playlist: Playlist) =
        create("${playlist.name} (copie)", playlist.songs)

    // --- M3U ---------------------------------------------------------------------------

    /**
     * Écrit une playlist au format M3U étendu dans [target].
     * Les chemins sont absolus : c'est ce que comprennent VLC, foobar2000, Poweramp…
     */
    suspend fun exportM3u(playlist: Playlist, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(target, "wt")?.bufferedWriter()?.use { out ->
                out.appendLine("#EXTM3U")
                out.appendLine("#PLAYLIST:${playlist.name}")
                playlist.songs.forEach { song ->
                    out.appendLine("#EXTINF:${song.durationMs / 1000},${song.displayArtist} - ${song.title}")
                    out.appendLine(song.path)
                }
            } ?: error("Flux de sortie indisponible")
            playlist.songs.size
        }
    }

    /**
     * Importe un M3U/M3U8. On associe chaque ligne à la bibliothèque par chemin absolu,
     * puis, à défaut, par nom de fichier — les playlists exportées d'un autre appareil
     * ont souvent une racine différente.
     */
    suspend fun importM3u(source: Uri, fallbackName: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val lines = context.contentResolver.openInputStream(source)
                ?.bufferedReader()
                ?.use(BufferedReader::readLines)
                ?: error("Fichier illisible")

            var name = fallbackName
            val paths = mutableListOf<String>()
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#PLAYLIST:") -> name = line.removePrefix("#PLAYLIST:").trim()
                    line.startsWith("#") -> Unit
                    else -> paths += line
                }
            }

            val library = musicRepository.library.value.songs
            val byPath = library.associateBy { it.path }
            val byFileName = library.groupBy { it.fileName.lowercase() }

            val songs = paths.mapNotNull { path ->
                val normalized = path.replace('\\', '/')
                byPath[normalized]
                    ?: byFileName[File(normalized).name.lowercase()]?.firstOrNull()
            }

            if (songs.isEmpty()) error("Aucun titre de cette playlist n'a été trouvé dans ta bibliothèque")
            create(name, songs)
        }
    }

    /** Nom de playlist non déjà pris, en suffixant si besoin. */
    fun uniqueName(base: String): String {
        val existing = playlists.value.map { it.name }.toSet()
        if (base !in existing) return base
        var i = 2
        while ("$base ($i)" in existing) i++
        return "$base ($i)"
    }

    val playlistNames: Flow<List<String>> = playlists.map { list -> list.map { it.name } }
}
