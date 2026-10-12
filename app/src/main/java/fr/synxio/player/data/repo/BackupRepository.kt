package fr.synxio.player.data.repo

import android.content.Context
import android.net.Uri
import fr.synxio.player.core.prefs.Settings
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.FavoriteEntity
import fr.synxio.player.data.db.PlayHistoryEntity
import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.db.PlaylistEntity
import fr.synxio.player.data.db.PlaylistSongEntity
import fr.synxio.player.data.model.BackupData
import fr.synxio.player.data.model.BackupFavorite
import fr.synxio.player.data.model.BackupPlayHistory
import fr.synxio.player.data.model.BackupPlayStat
import fr.synxio.player.data.model.BackupPlaylist
import fr.synxio.player.data.model.BackupPlaylistSong
import fr.synxio.player.data.model.BackupSettings
import fr.synxio.player.data.db.SynxioDatabase
import fr.synxio.player.di.ApplicationScope
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour gérer la sauvegarde et la restauration des données.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    private val favoriteDao: fr.synxio.player.data.db.FavoriteDao,
    private val playStatDao: fr.synxio.player.data.db.PlayStatDao,
    private val playHistoryDao: fr.synxio.player.data.db.PlayHistoryDao,
    private val settingsRepository: SettingsRepository,
    private val database: SynxioDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {

    /**
     * Lit un fichier de sauvegarde en refusant tout ce qui dépasse [MAX_BACKUP_BYTES] :
     * la taille annoncée est vérifiée d'abord, puis la lecture elle-même est bornée
     * (un fournisseur de contenu peut mentir ou ne pas connaître la taille).
     */
    private fun readBackupText(uri: Uri): String {
        val declared = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()
        if (declared != null && declared > MAX_BACKUP_BYTES) {
            throw IOException("Fichier de sauvegarde trop volumineux")
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Impossible d'ouvrir le fichier d'entrée")
        return input.use { readCapped(it, MAX_BACKUP_BYTES) }.toString(Charsets.UTF_8)
    }

    internal companion object {
        /** 20 Mo : très au-delà d'une sauvegarde réelle (quelques centaines de Ko). */
        const val MAX_BACKUP_BYTES = 20L * 1024 * 1024

        /** Lit au plus [maxBytes] octets ; au-delà, lève une IOException. */
        fun readCapped(input: java.io.InputStream, maxBytes: Long): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("Fichier de sauvegarde trop volumineux")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Exporte toutes les données vers un fichier.
     */
    suspend fun exportToFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Collecter toutes les données
                val playlists = playlistRepository.playlists.value
                val favorites = favoriteDao.observeAll().first()
                val stats = playStatDao.observeAll().first()
                val history = playHistoryDao.observeAll().first()
                val settings = settingsRepository.settings.first()

                // Convertir en format BackupData
                val backupData = BackupData(
                    version = 1,
                    exportedAt = System.currentTimeMillis(),
                    playlists = playlists.map { playlist ->
                        BackupPlaylist(
                            id = playlist.id,
                            name = playlist.name,
                            createdAt = playlist.createdAt,
                            updatedAt = playlist.updatedAt,
                            songs = playlist.songs.mapIndexed { index, song ->
                                BackupPlaylistSong(
                                    songId = song.id,
                                    songPath = song.path,
                                    position = index
                                )
                            }
                        )
                    },
                    favorites = favorites.map { favorite ->
                        BackupFavorite(
                            songId = favorite.songId,
                            songPath = favorite.songPath,
                            addedAt = favorite.addedAt
                        )
                    },
                    stats = stats.map { stat ->
                        BackupPlayStat(
                            songId = stat.songId,
                            songPath = stat.songPath,
                            playCount = stat.playCount,
                            skipCount = stat.skipCount,
                            lastPlayedAt = stat.lastPlayedAt,
                            totalListenedMs = stat.totalListenedMs
                        )
                    },
                    history = history.map { historyItem ->
                        BackupPlayHistory(
                            songId = historyItem.songId,
                            songPath = historyItem.songPath,
                            playedAt = historyItem.playedAt,
                            listenedMs = historyItem.listenedMs
                        )
                    },
                    settings = BackupSettings(
                        themeMode = settings.themeMode.name,
                        accentSource = settings.accentSource.name,
                        nowPlayingSkin = settings.nowPlayingSkin.name,
                        primaryColor = settings.primaryColor.takeIf { it.isNotBlank() },
                        secondaryColor = settings.secondaryColor.takeIf { it.isNotBlank() }
                    )
                )

                // Écrire dans le fichier
                val jsonString = json.encodeToString(backupData)
                outputStream.bufferedWriter().use { writer ->
                    writer.write(jsonString)
                }
            } ?: throw IOException("Impossible d'ouvrir le fichier de sortie")
        }
    }

    /**
     * Importe les données depuis un fichier.
     */
    suspend fun importFromFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            run {
                // Taille plafonnée : un fichier énorme ne doit pas saturer la mémoire.
                val jsonString = readBackupText(uri)

                val backupData = json.decodeFromString<BackupData>(jsonString)

                // Noms déjà présents, lus avant la transaction : une playlist du même nom
                // n'est pas recréée lors d'un second import du même fichier.
                val existingNames = database.playlistDao().observePlaylists().first()
                    .map { it.name }
                    .toMutableSet()
                val songsById = musicRepository.library.value.songs.associateBy { it.id }

                // Base de données : tout ou rien. Une erreur au milieu de l'import
                // n'y laisse pas de données à moitié restaurées.
                database.withTransaction {

                // Restaurer les playlists
                backupData.playlists.forEach { backupPlaylist ->
                    if (!existingNames.add(backupPlaylist.name)) return@forEach
                    // Trouver les chansons correspondantes dans la bibliothèque
                    val songs = backupPlaylist.songs.mapNotNull { backupSong ->
                        songsById[backupSong.songId]
                    }
                    playlistRepository.create(
                        name = backupPlaylist.name,
                        songs = songs
                    )
                }

                // Restaurer les favoris
                backupData.favorites.forEach { backupFavorite ->
                    favoriteDao.add(
                        FavoriteEntity(
                            songId = backupFavorite.songId,
                            songPath = backupFavorite.songPath,
                            addedAt = backupFavorite.addedAt
                        )
                    )
                }

                // Restaurer les statistiques
                backupData.stats.forEach { backupStat ->
                    playStatDao.upsert(
                        PlayStatEntity(
                            songId = backupStat.songId,
                            songPath = backupStat.songPath,
                            playCount = backupStat.playCount,
                            skipCount = backupStat.skipCount,
                            lastPlayedAt = backupStat.lastPlayedAt,
                            totalListenedMs = backupStat.totalListenedMs
                        )
                    )
                }

                // Restaurer l'historique
                backupData.history.forEach { backupHistory ->
                    playHistoryDao.add(
                        PlayHistoryEntity(
                            songId = backupHistory.songId,
                            songPath = backupHistory.songPath,
                            playedAt = backupHistory.playedAt,
                            listenedMs = backupHistory.listenedMs,
                            durationMs = 0 // On ne peut pas restaurer la durée
                        )
                    )
                }

                } // fin de la transaction

                // Restaurer les réglages
                backupData.settings?.let { backupSettings ->
                    // Appliquer les réglages un par un
                    try {
                        val themeMode = fr.synxio.player.data.model.ThemeMode.valueOf(backupSettings.themeMode)
                        settingsRepository.setThemeMode(themeMode)
                    } catch (e: IllegalArgumentException) {
                        // Ignorer si le thème n'est pas valide
                    }

                    try {
                        val accentSource = fr.synxio.player.data.model.AccentSource.valueOf(backupSettings.accentSource)
                        settingsRepository.setAccentSource(accentSource)
                    } catch (e: IllegalArgumentException) {
                        // Ignorer si la source n'est pas valide
                    }

                    try {
                        val nowPlayingSkin = fr.synxio.player.data.model.NowPlayingSkin.valueOf(backupSettings.nowPlayingSkin)
                        settingsRepository.setNowPlayingSkin(nowPlayingSkin)
                    } catch (e: IllegalArgumentException) {
                        // Ignorer si le skin n'est pas valide
                    }

                    backupSettings.primaryColor?.let { color ->
                        try {
                            val themeColor = fr.synxio.player.data.model.ThemeColor.entries
                                .find { it.value == color }
                                ?: fr.synxio.player.data.model.ThemeColor.DEFAULT
                            settingsRepository.setPrimaryColor(themeColor.value)
                        } catch (e: Exception) {
                            // Ignorer
                        }
                    }

                    backupSettings.secondaryColor?.let { color ->
                        try {
                            val themeColor = fr.synxio.player.data.model.ThemeColor.entries
                                .find { it.value == color }
                                ?: fr.synxio.player.data.model.ThemeColor.entries[1]
                            settingsRepository.setSecondaryColor(themeColor.value)
                        } catch (e: Exception) {
                            // Ignorer
                        }
                    }
                }
            }
            Unit
        }
    }

    /**
     * Crée un nom de fichier de sauvegarde par défaut.
     */
    fun getDefaultBackupFilename(): String {
        val timestamp = System.currentTimeMillis()
        return "synxio_backup_$timestamp.synxio"
    }

    /**
     * Vérifie si un fichier est un fichier de sauvegarde Synxio valide.
     */
    suspend fun isValidBackupFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val backupData = json.decodeFromString<BackupData>(readBackupText(uri))
            // Vérifier que le fichier contient les champs attendus
            backupData.version == 1
        }.getOrDefault(false)
    }

    /**
     * Récupère les informations sur le fichier de sauvegarde.
     */
    suspend fun getBackupInfo(uri: Uri): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            run {
                val backupData = json.decodeFromString<BackupData>(readBackupText(uri))

                // Compter les éléments
                val playlistCount = backupData.playlists.size
                val favoriteCount = backupData.favorites.size
                val statsCount = backupData.stats.size
                val historyCount = backupData.history.size

                // Formater la date
                val exportedDate = java.text.SimpleDateFormat.getDateTimeInstance()
                    .format(java.util.Date(backupData.exportedAt))

                BackupInfo(
                    uri = uri,
                    version = backupData.version,
                    exportedAt = exportedDate,
                    playlistCount = playlistCount,
                    favoriteCount = favoriteCount,
                    statsCount = statsCount,
                    historyCount = historyCount,
                    hasSettings = backupData.settings != null
                )
            }
        }
    }

    /**
     * Informations sur un fichier de sauvegarde.
     */
    data class BackupInfo(
        val uri: Uri,
        val version: Int,
        val exportedAt: String,
        val playlistCount: Int,
        val favoriteCount: Int,
        val statsCount: Int,
        val historyCount: Int,
        val hasSettings: Boolean
    ) {
        val totalItems: Int get() = playlistCount + favoriteCount + statsCount + historyCount
    }

    /**
     * Efface toutes les données locales.
     */
    suspend fun clearAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Supprimer toutes les playlists
            playlistRepository.playlists.value.forEach { playlist ->
                playlistRepository.delete(playlist.id)
            }

            // Supprimer tous les favoris
            // first() et non first { isNotEmpty() } : sans favori, ce dernier ne rendait
            // jamais la main et la suite (stats, historique, réglages) n'était pas effacée.
            favoriteDao.observeAll().first().forEach { favorite ->
                favoriteDao.remove(favorite.songId)
            }

            // Supprimer toutes les statistiques
            playStatDao.clear()

            // Supprimer tout l'historique
            playHistoryDao.clear()

            // Réinitialiser les réglages aux valeurs par défaut
            settingsRepository.setThemeMode(fr.synxio.player.data.model.ThemeMode.SYSTEM)
            settingsRepository.setAccentSource(fr.synxio.player.data.model.AccentSource.ARTWORK)
            settingsRepository.setNowPlayingSkin(fr.synxio.player.data.model.NowPlayingSkin.IMMERSIVE)
        }
    }

    /**
     * Exporte les playlists vers un fichier M3U8.
     */
    suspend fun exportPlaylistToM3u8(playlist: fr.synxio.player.data.model.Playlist, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write("#EXTM3U\n")
                        writer.write("# Generated by Synxio Player\n")
                        writer.write("\n")

                        playlist.songs.forEach { song ->
                            writer.write("#EXTINF:${song.durationMs / 1000},${song.displayArtist} - ${song.title}\n")
                            writer.write("#EXTALB:${song.displayAlbum}\n")
                            writer.write("#EXTART:${song.displayArtist}\n")
                            writer.write("#EXTGENRE:${song.genre ?: "Unknown"}\n")
                            writer.write("${song.path}\n")
                            writer.write("\n")
                        }
                    }
                } ?: throw IOException("Impossible d'ouvrir le fichier de sortie")
            }
        }

    /**
     * Importe un fichier M3U8 et crée une playlist.
     */
    suspend fun importM3u8File(uri: Uri, playlistName: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val songs = mutableListOf<fr.synxio.player.data.model.Song>()
                var currentSong: fr.synxio.player.data.model.Song? = null

                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("#EXTINF:")) {
                            // Parse EXTINF: duration, artist - title
                            val durationPart = line.substringAfter("#EXTINF:").substringBefore(",")
                            val infoPart = line.substringAfter(",")
                            // On ne peut pas créer un Song sans ID, donc on va juste stocker les paths
                            // et les résoudre plus tard
                        } else if (!line.startsWith("#") && line.isNotBlank()) {
                            // C'est un chemin de fichier
                            // On va essayer de trouver le morceau dans la bibliothèque
                            val library = musicRepository.library.value
                            val song = library.songs.find { it.path == line }
                            song?.let { songs.add(it) }
                        }
                    }
                }

                // Créer la playlist avec les morceaux trouvés
                val playlistId = playlistRepository.create(playlistName, songs)
                songs.size
            } ?: throw IOException("Impossible d'ouvrir le fichier d'entrée")
        }
    }
}
