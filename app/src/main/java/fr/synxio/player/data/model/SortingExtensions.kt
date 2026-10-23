package fr.synxio.player.data.model

import androidx.compose.ui.graphics.Color
import java.text.DateFormat
import java.time.Instant
import java.util.Date

// ============================================================================
// STATISTIQUES ET HISTORIQUE
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

/** Statistiques pour un artiste */
data class ArtistStats(
    val artist: Artist,
    val playCount: Int,
    val totalListenedMs: Long
)

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

/** Élément d'historique de lecture */
data class PlayHistory(
    val song: Song,
    val playedAt: Instant,
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
// UTILITAIRES
// ============================================================================

/**
 * Formateurs de date réutilisés par fil d'exécution.
 *
 * `DateFormat` n'est pas sûr entre fils, mais en créer un par ligne d'historique —
 * l'ancien comportement — allouait et analysait le motif de la locale à chaque
 * recomposition de chaque ligne.
 */
private val DATE_FORMAT = object : ThreadLocal<DateFormat>() {
    override fun initialValue(): DateFormat = DateFormat.getDateInstance()
}

private val DATE_TIME_FORMAT = object : ThreadLocal<DateFormat>() {
    override fun initialValue(): DateFormat = DateFormat.getDateTimeInstance()
}

/** Formate une date */
fun Instant.formatDate(): String = DATE_FORMAT.get()!!.format(Date(toEpochMilli()))

/** Formate une date et heure */
fun Instant.formatDateTime(): String = DATE_TIME_FORMAT.get()!!.format(Date(toEpochMilli()))
