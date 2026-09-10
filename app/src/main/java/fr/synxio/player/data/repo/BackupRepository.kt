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
import fr.synxio.player.di.ApplicationScope
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
    @ApplicationScope private val scope: CoroutineScope
) {

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
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }

                val backupData = json.decodeFromString<BackupData>(jsonString)

                // Restaurer les playlists
                backupData.playlists.forEach { backupPlaylist ->
                    // Trouver les chansons correspondantes dans la bibliothèque
                    val songs = backupPlaylist.songs.mapNotNull { backupSong ->
                        musicRepository.library.value.songs.find { it.id == backupSong.songId }
                    }
                    val playlistId = playlistRepository.create(
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
            } ?: throw IOException("Impossible d'ouvrir le fichier d'entrée")
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
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
                val backupData = json.decodeFromString<BackupData>(jsonString)
                // Vérifier que le fichier contient les champs attendus
                backupData.version == 1
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Récupère les informations sur le fichier de sauvegarde.
     */
    suspend fun getBackupInfo(uri: Uri): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
                val backupData = json.decodeFromString<BackupData>(jsonString)

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
            } ?: throw IOException("Impossible de lire le fichier")
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
            val favorites = favoriteDao.observeAll().first { it.isNotEmpty() }
            favorites.forEach { favorite ->
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
