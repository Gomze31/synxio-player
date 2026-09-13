package fr.synxio.player.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

// ============================================================================
// NOUVEAUX MODÈLES POUR LES STATISTIQUES ET L'HISTORIQUE
// ============================================================================

/** Période pour les statistiques */
enum class StatsPeriod(val label: String) {
    TODAY("Aujourd'hui"),
    WEEK("Cette semaine"),
    MONTH("Ce mois"),
    YEAR("Cette année"),
    ALL_TIME("Tout le temps")
}

/** Type de playlist intelligente */
enum class SmartPlaylistType(val label: String, val description: String) {
    MOST_PLAYED("Les plus écoutés", "Tes morceaux les plus écoutés"),
    RECENTLY_PLAYED("Récemment écoutés", "Tes derniers morceaux écoutés"),
    RECENTLY_ADDED("Ajoutés récemment", "Tes morceaux récemment ajoutés"),
    NEVER_PLAYED("Jamais écoutés", "Tes morceaux jamais écoutés"),
    LONG_TIME_NO_PLAY("Pas écoutés depuis longtemps", "Tes morceaux non écoutés depuis longtemps"),
    SHORTEST("Les plus courts", "Tes morceaux les plus courts"),
    LONGEST("Les plus longs", "Tes morceaux les plus longs"),
    HIGHEST_BITRATE("Meilleure qualité", "Tes morceaux avec le meilleur débit"),
    FAVORITES("Favoris", "Tes morceaux préférés")
}

/** Type de tri pour les playlists */
enum class PlaylistSort(val label: String) {
    NAME("Nom"),
    CREATION_DATE("Date de création"),
    UPDATE_DATE("Date de modification"),
    SONG_COUNT("Nombre de morceaux"),
    TOTAL_DURATION("Durée totale")
}

// ============================================================================
// STATISTIQUES
// ============================================================================

/** Statistiques pour un artiste */
data class ArtistStats(
    val artist: Artist,
    val playCount: Int,
    val totalListenedMs: Long
) {
    val percentage: Float get() = if (playCount > 0) 1f else 0f
}

/** Statistiques pour un album */
data class AlbumStats(
    val album: Album,
    val playCount: Int
)

/** Résumé des statistiques */
data class StatsSummary(
    val totalSongs: Int = 0,
    val totalPlayTimeMs: Long = 0,
    val totalPlayCount: Int = 0,
    val topArtists: List<ArtistStats> = emptyList(),
    val topAlbums: List<AlbumStats> = emptyList(),
    val topGenres: List<Pair<String, Int>> = emptyList(),
    val recentActivity: List<PlayHistory> = emptyList()
)

// ============================================================================
// HISTORIQUE
// ============================================================================

/** Élément d'historique de lecture */
data class PlayHistory(
    val song: Song,
    val playedAt: java.time.Instant,
    val listenedMs: Long
)

// ============================================================================
// BACKUP
// ============================================================================

/** Données de sauvegarde */
@kotlinx.serialization.Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
    val stats: List<BackupPlayStat> = emptyList(),
    val history: List<BackupPlayHistory> = emptyList(),
    val settings: BackupSettings? = null
)

@kotlinx.serialization.Serializable
data class BackupPlaylist(
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songs: List<BackupPlaylistSong> = emptyList()
)

@kotlinx.serialization.Serializable
data class BackupPlaylistSong(
    val songId: Long,
    val songPath: String,
    val position: Int
)

@kotlinx.serialization.Serializable
data class BackupFavorite(
    val songId: Long,
    val songPath: String,
    val addedAt: Long
)

@kotlinx.serialization.Serializable
data class BackupPlayStat(
    val songId: Long,
    val songPath: String,
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAt: Long,
    val totalListenedMs: Long
)

@kotlinx.serialization.Serializable
data class BackupPlayHistory(
    val songId: Long,
    val songPath: String,
    val playedAt: Long,
    val listenedMs: Long
)

@kotlinx.serialization.Serializable
data class BackupSettings(
    val themeMode: String,
    val accentSource: String,
    val nowPlayingSkin: String,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val textSize: String? = null
)

// ============================================================================
// COULEURS PRÉDÉFINIES
// ============================================================================

enum class ThemeColor(
    val label: String,
    val value: String
) {
    DEFAULT("Défaut", "#673AB7"),
    RED("Rouge", "#F44336"),
    PINK("Rose", "#E91E63"),
    PURPLE("Violet", "#9C27B0"),
    DEEP_PURPLE("Violet foncé", "#673AB7"),
    INDIGO("Indigo", "#3F51B5"),
    BLUE("Bleu", "#2196F3"),
    LIGHT_BLUE("Bleu clair", "#03A9F4"),
    CYAN("Cyan", "#00BCD4"),
    TEAL("Vert eau", "#009688"),
    GREEN("Vert", "#4CAF50"),
    LIGHT_GREEN("Vert clair", "#8BC34A"),
    LIME("Vert citron", "#CDDC39"),
    YELLOW("Jaune", "#FFEB3B"),
    AMBER("Ambre", "#FFC107"),
    ORANGE("Orange", "#FF9800"),
    DEEP_ORANGE("Orange foncé", "#FF5722"),
    BROWN("Marron", "#795548"),
    GREY("Gris", "#9E9E9E"),
    BLUE_GREY("Gris bleu", "#607D8B"),
    BLACK("Noir", "#000000");

    fun toColor(): Color = Color(android.graphics.Color.parseColor(value))
}

/** Taille du texte */
enum class TextSize(val label: String, val scale: Float) {
    SMALL("Petit", 0.8f),
    NORMAL("Normal", 1.0f),
    LARGE("Grand", 1.2f),
    XLARGE("Très grand", 1.5f)
}

// ============================================================================
// FILTRES DE RECHERCHE
// ============================================================================

/** Filtre pour la recherche avancée */
data class SearchFilter(
    val query: String = "",
    val artists: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val minDurationMs: Long = 0,
    val maxDurationMs: Long = Long.MAX_VALUE,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val favoriteOnly: Boolean = false
)

// ============================================================================
// NOTIFICATIONS
// ============================================================================

/** Type de notification */
enum class NotificationType {
    PLAYBACK,
    SLEEP_TIMER,
    BACKUP
}

// ============================================================================
// UTILITAIRES
// ============================================================================

/** Convertit des millisecondes en durée lisible */
fun Long.asDuration(): String {
    val hours = this / (1000 * 60 * 60)
    val minutes = (this % (1000 * 60 * 60)) / (1000 * 60)
    val seconds = (this % (1000 * 60)) / 1000
    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%dh %02dmin %02ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.getDefault(), "%dmin %02ds", minutes, seconds)
        else -> String.format(Locale.getDefault(), "%ds", seconds)
    }
}

/** Formate un nombre de chansons */
fun Int.pluralSongs(): String = when {
    this == 0 -> "Aucun titre"
    this == 1 -> "1 titre"
    else -> "$this titres"
}

/** Formate une date */
fun java.time.Instant.formatDate(): String {
    val date = java.util.Date(this.toEpochMilli())
    val format = java.text.SimpleDateFormat.getDateInstance()
    return format.format(date)
}

/** Formate une date et heure */
fun java.time.Instant.formatDateTime(): String {
    val date = java.util.Date(this.toEpochMilli())
    val format = java.text.SimpleDateFormat.getDateTimeInstance()
    return format.format(date)
}
