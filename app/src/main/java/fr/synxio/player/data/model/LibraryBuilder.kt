package fr.synxio.player.data.model

import fr.synxio.player.data.repo.Library

/** Filtres utilisateur appliqués à la publication, jamais au scan (voir `LibrarySongEntity`). */
data class LibraryFilters(
    val minDurationMs: Long,
    val excludedFolders: Set<String>,
    val audiobookFolders: Set<String>,
)

/**
 * Construit la bibliothèque à partir des titres bruts.
 *
 * Pur : aucune dépendance Android, pour être testé en JVM. Les regroupements (albums,
 * artistes, genres, dossiers) sont ceux de l'ancien `MusicRepository.buildLibrary`.
 */
object LibraryBuilder {

    fun build(raw: List<Song>, filters: LibraryFilters, isRefreshing: Boolean = false): Library {
        val allSongs = raw.asSequence()
            .filter { it.durationMs >= filters.minDurationMs }
            .filter { song -> filters.excludedFolders.none { song.path.startsWith(it) } }
            .map { song ->
                if (!song.isAudiobook && filters.audiobookFolders.any { song.path.startsWith(it) }) song.copy(isAudiobook = true) else song
            }
            .toList()

        val podcasts = allSongs.filter { it.isPodcast }.sortedBy { it.title.lowercase() }
        val audiobooks = allSongs.filter { it.isAudiobook }.sortedBy { it.title.lowercase() }
        val musicSongs = allSongs.filter { !it.isPodcast && !it.isAudiobook }

        val albums = musicSongs
            .groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val head = albumSongs.first()
                Album(
                    id = albumId,
                    title = head.displayAlbum,
                    artist = head.albumArtist?.takeIf { it.isNotBlank() } ?: head.displayArtist,
                    artistId = head.artistId,
                    year = albumSongs.maxOf { it.year },
                    // Ordre naturel d'écoute d'un album : disque puis piste.
                    songs = albumSongs.sortedWith(
                        compareBy({ it.disc }, { it.track }, { it.title.lowercase() })
                    ),
                    artworkOverride = albumSongs.firstNotNullOfOrNull { it.artworkOverride },
                )
            }

        // Un morceau peut compter pour plusieurs artistes : on indexe chaque nom
        // séparément plutôt que de créer un artiste « A/B/C » illisible.
        val albumsByArtistKey = HashMap<String, MutableSet<Album>>()
        val displayNameByKey = HashMap<String, String>()
        val artistIdByKey = HashMap<String, Long>()

        albums.forEach { album ->
            val names = album.songs
                .flatMap { song ->
                    splitArtistTag(song.albumArtist?.takeIf { it.isNotBlank() } ?: song.displayArtist)
                }
                .ifEmpty { listOf(album.artist) }

            names.distinctBy { it.lowercase() }.forEach { name ->
                val key = name.lowercase()
                albumsByArtistKey.getOrPut(key) { linkedSetOf() } += album
                displayNameByKey.putIfAbsent(key, name)
                artistIdByKey.putIfAbsent(key, album.artistId)
            }
        }

        val artists = albumsByArtistKey.map { (key, artistAlbums) ->
            Artist(
                id = artistIdByKey[key] ?: 0L,
                name = displayNameByKey[key] ?: key,
                albums = artistAlbums.sortedByDescending { it.year },
            )
        }

        val genres = musicSongs
            .groupBy { it.genre?.takeIf { g -> g.isNotBlank() } ?: Song.UNKNOWN_GENRE }
            .map { (name, genreSongs) -> Genre(name, genreSongs) }
            .sortedBy { it.name.lowercase() }

        val folders = musicSongs
            .groupBy { it.folderPath }
            .map { (path, folderSongs) -> Folder(path, folderSongs) }
            .sortedBy { it.path.lowercase() }

        return Library(
            songs = musicSongs,
            albums = albums,
            artists = artists,
            genres = genres,
            folders = folders,
            podcasts = podcasts,
            audiobooks = audiobooks,
            isLoading = false,
            hasScanned = true,
            isRefreshing = isRefreshing,
        )
    }
}
