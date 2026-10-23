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
        // Copie ou import M3U : on garde la liste telle quelle, doublons voulus compris.
        if (songs.isNotEmpty()) addSongs(id, songs, skipExisting = false)
        return id
    }

    /**
     * Ajoute [songs] en fin de playlist, dans l'ordre donné. Avec [skipExisting], un titre
     * déjà présent (ou répété dans [songs]) n'est pas ajouté une seconde fois.
     */
    suspend fun addSongs(playlistId: Long, songs: List<Song>, skipExisting: Boolean = true): AddResult {
        if (songs.isEmpty()) return AddResult(emptyList(), 0)
        var skipped = 0
        val rowIds = dao.appendEntries(playlistId, System.currentTimeMillis()) { start, present ->
            val fresh = if (skipExisting) {
                val distinct = songs.distinctBy { it.id }
                distinct.filter { it.id !in present }.also { skipped = distinct.size - it.size }
            } else songs
            fresh.mapIndexed { i, song ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = song.id,
                    songPath = song.path,
                    position = start + i,
                )
            }
        }
        return AddResult(rowIds, skipped)
    }

    /** Annule un ajout : retire exactement les lignes qu'il avait créées. */
    suspend fun undoAdd(playlistId: Long, result: AddResult) {
        if (result.rowIds.isEmpty()) return
        dao.deleteEntries(result.rowIds)
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

    /**
     * Déplace le titre visible n° [from] à la place n° [to].
     *
     * Les indices sont ceux de la liste affichée, qui masque les titres devenus
     * indisponibles. Ces derniers restent en base, à leur place relative : un fichier
     * retrouvé (carte SD remise) réapparaît là où il était.
     */
    suspend fun moveSong(playlistId: Long, from: Int, to: Int) {
        val index = musicRepository.songsById.value
        val entries = dao.entries(playlistId).sortedBy { it.position }
        val moved = moveVisible(entries, { it.songId in index }, from, to)
        if (moved === entries) return
        dao.replaceEntries(
            playlistId,
            moved.mapIndexed { i, e -> e.copy(rowId = 0, position = i) },
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
            // Même lecture que l'import de l'écran Sauvegarde : taille bornée (un fichier
            // énorme choisi par erreur ne fait pas exploser la mémoire), BOM et chemins
            // file:// gérés, correspondance stricte sans doublon.
            val bytes = context.contentResolver.openInputStream(source)
                ?.use { BackupRepository.readCapped(it, M3u8Format.MAX_BYTES) }
                ?: error("Fichier illisible")
            val parsed = M3u8Format.parse(bytes.toString(Charsets.UTF_8))
            if (parsed.entries.isEmpty()) error("Ce fichier ne contient aucun titre")

            val library = musicRepository.library.value
            if (!library.hasScanned) error("La bibliothèque est encore en cours de chargement, réessaie dans un instant")
            val songs = M3u8Format.match(parsed.entries, library.songs)

            if (songs.isEmpty()) error("Aucun titre de cette playlist n'a été trouvé dans ta bibliothèque")
            create(uniqueName(parsed.name?.takeIf { it.isNotBlank() } ?: fallbackName), songs)
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

/** Bilan d'un ajout à une playlist : lignes créées (pour l'annuler) et titres déjà présents. */
data class AddResult(val rowIds: List<Long>, val skipped: Int) {
    val added: Int get() = rowIds.size
}

/**
 * Déplace l'élément visible n° [from] à la place visible n° [to], sans toucher à l'ordre
 * relatif des éléments masqués. Rend [entries] tel quel si le déplacement est impossible.
 */
internal fun <E> moveVisible(entries: List<E>, isVisible: (E) -> Boolean, from: Int, to: Int): List<E> {
    val visible = entries.indices.filter { isVisible(entries[it]) }
    if (from !in visible.indices || to !in visible.indices || from == to) return entries
    val result = entries.toMutableList()
    val item = result.removeAt(visible[from])
    val visibleAfter = result.indices.filter { isVisible(result[it]) }
    val insertAt = if (to < visibleAfter.size) {
        // Vers le bas, on se place juste après l'élément qui occupait la place visée.
        if (to > from) visibleAfter[to - 1] + 1 else visibleAfter[to]
    } else {
        visibleAfter.last() + 1
    }
    result.add(insertAt, item)
    return result
}
