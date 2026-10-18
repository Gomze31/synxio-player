package fr.synxio.player.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Photo de la lecture telle que les widgets l'affichent.
 *
 * Le service la publie à chaque changement (piste, lecture/pause, aléatoire, favori…),
 * et périodiquement pendant la lecture pour faire avancer la barre de progression.
 * Elle est autonome : le widget n'a besoin ni de la base, ni de la bibliothèque, ni
 * d'une connexion à la session pour se dessiner — il reste juste après un redémarrage.
 */
data class WidgetSnapshot(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Horloge murale au moment de la photo, pour estimer la position pendant la lecture. */
    val capturedAtMs: Long = 0L,
    val shuffle: Boolean = false,
    /** Valeur `Player.REPEAT_MODE_*`. */
    val repeatMode: Int = 0,
    val favorite: Boolean = false,
    val hasNext: Boolean = false,
    /** Fichier de la pochette réduite préparée pour le widget, vide sans pochette. */
    val artPath: String = "",
    /** Fichier du fond flouté tiré de la pochette, vide sans pochette. */
    val backdropPath: String = "",
    /** Accent ARGB tiré de la pochette, 0 pour l'accent de l'application. */
    val accent: Int = 0,
    /** Vrai pour un flux en direct (webradio) : pas de progression à afficher. */
    val live: Boolean = false,
) {
    val isEmpty: Boolean get() = mediaId.isEmpty()

    /** Progression estimée à [nowMs] : avance avec l'horloge pendant la lecture. */
    fun progressAt(nowMs: Long): Float {
        if (durationMs <= 0L || live) return 0f
        val elapsed = if (isPlaying && capturedAtMs > 0L) (nowMs - capturedAtMs).coerceAtLeast(0L) else 0L
        return ((positionMs + elapsed).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun writeTo(prefs: MutablePreferences) {
        prefs[Keys.MEDIA_ID] = mediaId
        prefs[Keys.TITLE] = title
        prefs[Keys.ARTIST] = artist
        prefs[Keys.ALBUM] = album
        prefs[Keys.PLAYING] = isPlaying
        prefs[Keys.POSITION] = positionMs
        prefs[Keys.DURATION] = durationMs
        prefs[Keys.CAPTURED_AT] = capturedAtMs
        prefs[Keys.SHUFFLE] = shuffle
        prefs[Keys.REPEAT] = repeatMode
        prefs[Keys.FAVORITE] = favorite
        prefs[Keys.HAS_NEXT] = hasNext
        prefs[Keys.ART] = artPath
        prefs[Keys.BACKDROP] = backdropPath
        prefs[Keys.ACCENT] = accent
        prefs[Keys.LIVE] = live
    }

    companion object {
        fun from(prefs: Preferences) = WidgetSnapshot(
            mediaId = prefs[Keys.MEDIA_ID].orEmpty(),
            title = prefs[Keys.TITLE].orEmpty(),
            artist = prefs[Keys.ARTIST].orEmpty(),
            album = prefs[Keys.ALBUM].orEmpty(),
            isPlaying = prefs[Keys.PLAYING] ?: false,
            positionMs = prefs[Keys.POSITION] ?: 0L,
            durationMs = prefs[Keys.DURATION] ?: 0L,
            capturedAtMs = prefs[Keys.CAPTURED_AT] ?: 0L,
            shuffle = prefs[Keys.SHUFFLE] ?: false,
            repeatMode = prefs[Keys.REPEAT] ?: 0,
            favorite = prefs[Keys.FAVORITE] ?: false,
            hasNext = prefs[Keys.HAS_NEXT] ?: false,
            artPath = prefs[Keys.ART].orEmpty(),
            backdropPath = prefs[Keys.BACKDROP].orEmpty(),
            accent = prefs[Keys.ACCENT] ?: 0,
            live = prefs[Keys.LIVE] ?: false,
        )
    }

    /** Clés partagées par l'état Glance de chaque widget. */
    object Keys {
        val MEDIA_ID = stringPreferencesKey("media_id")
        val TITLE = stringPreferencesKey("title")
        val ARTIST = stringPreferencesKey("artist")
        val ALBUM = stringPreferencesKey("album")
        val PLAYING = booleanPreferencesKey("playing")
        val POSITION = longPreferencesKey("position")
        val DURATION = longPreferencesKey("duration")
        val CAPTURED_AT = longPreferencesKey("captured_at")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat")
        val FAVORITE = booleanPreferencesKey("favorite")
        val HAS_NEXT = booleanPreferencesKey("has_next")
        val ART = stringPreferencesKey("art")
        val BACKDROP = stringPreferencesKey("backdrop")
        val ACCENT = intPreferencesKey("accent")
        val LIVE = booleanPreferencesKey("live")
        val DARK = booleanPreferencesKey("dark")
        val AMOLED = booleanPreferencesKey("amoled")
    }
}

/**
 * Dernière photo publiée, hors de l'état Glance.
 *
 * L'état Glance est propre à chaque widget posé : un widget ajouté après coup part
 * vide. Il est amorcé depuis ce stockage, qui survit au redémarrage du téléphone.
 */
internal object WidgetSnapshotStore {
    private const val FILE = "synxio_widget_snapshot"

    fun save(context: Context, s: WidgetSnapshot) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("media_id", s.mediaId)
            .putString("title", s.title)
            .putString("artist", s.artist)
            .putString("album", s.album)
            .putBoolean("playing", s.isPlaying)
            .putLong("position", s.positionMs)
            .putLong("duration", s.durationMs)
            .putLong("captured_at", s.capturedAtMs)
            .putBoolean("shuffle", s.shuffle)
            .putInt("repeat", s.repeatMode)
            .putBoolean("favorite", s.favorite)
            .putBoolean("has_next", s.hasNext)
            .putString("art", s.artPath)
            .putString("backdrop", s.backdropPath)
            .putInt("accent", s.accent)
            .putBoolean("live", s.live)
            .apply()
    }

    fun load(context: Context): WidgetSnapshot {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            mediaId = p.getString("media_id", "").orEmpty(),
            title = p.getString("title", "").orEmpty(),
            artist = p.getString("artist", "").orEmpty(),
            album = p.getString("album", "").orEmpty(),
            isPlaying = p.getBoolean("playing", false),
            positionMs = p.getLong("position", 0L),
            durationMs = p.getLong("duration", 0L),
            capturedAtMs = p.getLong("captured_at", 0L),
            shuffle = p.getBoolean("shuffle", false),
            repeatMode = p.getInt("repeat", 0),
            favorite = p.getBoolean("favorite", false),
            hasNext = p.getBoolean("has_next", false),
            artPath = p.getString("art", "").orEmpty(),
            backdropPath = p.getString("backdrop", "").orEmpty(),
            accent = p.getInt("accent", 0),
            live = p.getBoolean("live", false),
        )
    }
}
