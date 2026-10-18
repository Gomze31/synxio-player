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
 * Le service la publie à chaque changement (piste, lecture/pause, aléatoire, favori…).
 * Ses publications périodiques pendant la lecture (seule la position avance) sont
 * écartées par [WidgetUpdater], voir [displaysSameAs] : le widget ne se redessine qu'aux
 * vrais changements. Elle est autonome : le widget n'a besoin ni de la base, ni de la
 * bibliothèque, ni d'une connexion à la session pour se dessiner — il reste juste après
 * un redémarrage.
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
    /** Fichier de la pochette réduite (coins arrondis) préparée pour le widget. */
    val artPath: String = "",
    /** Fichier du halo lumineux tiré de la pochette (calque transparent). */
    val backdropPath: String = "",
    /** Accent ARGB tiré de la pochette pour la scène sombre, 0 pour la braise Synxio. */
    val accent: Int = 0,
    /** Vrai pour un flux en direct (webradio) : pas de progression à afficher. */
    val live: Boolean = false,
    /** Accent ARGB pour la scène claire (papier), 0 pour l'accent de l'application. */
    val accentLight: Int = 0,
    /**
     * URI source de la pochette : un widget posé alors qu'aucun n'existait prépare ses
     * images à partir d'elle, sans attendre le prochain changement de piste.
     */
    val artUri: String = "",
) {
    val isEmpty: Boolean get() = mediaId.isEmpty()

    /** Progression estimée à [nowMs] : avance avec l'horloge pendant la lecture. */
    fun progressAt(nowMs: Long): Float {
        if (durationMs <= 0L || live) return 0f
        val elapsed = if (isPlaying && capturedAtMs > 0L) (nowMs - capturedAtMs).coerceAtLeast(0L) else 0L
        return ((positionMs + elapsed).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /**
     * Barre de progression affichée : en pause seulement, et jamais pour un direct.
     *
     * Une barre qui avance demanderait de republier le widget sans arrêt (chaque
     * publication réveille le lanceur et refait transiter les images) : pendant la
     * lecture elle serait donc figée, donc fausse. En pause elle est exacte et montre
     * l'endroit où l'écoute reprendra.
     */
    val showsProgress: Boolean get() = !isPlaying && !live && durationMs > 0L

    /** La place de la barre reste réservée en lecture : les boutons ne bougent pas sous le doigt. */
    val reservesProgress: Boolean get() = !live && durationMs > 0L

    /**
     * Vrai si les deux photos dessinent le même widget : seule la position diffère, et
     * elle n'est pas affichée pendant la lecture. En pause, la position compte.
     */
    fun displaysSameAs(other: WidgetSnapshot): Boolean {
        if (stripped() != other.stripped()) return false
        return isPlaying || positionMs == other.positionMs
    }

    /** Sans l'horloge ni les images dérivées (recalculées à partir de [artUri]). */
    private fun stripped() = copy(
        positionMs = 0L,
        capturedAtMs = 0L,
        artPath = "",
        backdropPath = "",
        accent = 0,
        accentLight = 0,
    )

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
        prefs[Keys.ACCENT_LIGHT] = accentLight
        prefs[Keys.ART_URI] = artUri
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
            accentLight = prefs[Keys.ACCENT_LIGHT] ?: 0,
            artUri = prefs[Keys.ART_URI].orEmpty(),
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
        val ACCENT_LIGHT = intPreferencesKey("accent_light")
        val ART_URI = stringPreferencesKey("art_uri")
        /** Ordinal de [WidgetTheme] : suivre le système, ou le thème forcé dans Synxio. */
        val THEME = intPreferencesKey("theme")
    }
}

/**
 * Thème d'un widget.
 *
 * [SYSTEM] ne fige aucune couleur : le widget porte ses deux palettes (encre et papier)
 * et c'est le lanceur qui choisit au moment de l'afficher. Un passage en mode sombre,
 * même la nuit avec l'app fermée, est donc suivi sans republier le widget (Android 12+ ;
 * avant, la couleur est résolue à la publication). Les autres valeurs reprennent le
 * thème forcé dans les réglages de Synxio.
 */
internal enum class WidgetTheme {
    SYSTEM, LIGHT, DARK, AMOLED;

    companion object {
        fun of(ordinal: Int?): WidgetTheme = entries.getOrNull(ordinal ?: -1) ?: SYSTEM
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
            .putInt("accent_light", s.accentLight)
            .putString("art_uri", s.artUri)
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
            accentLight = p.getInt("accent_light", 0),
            artUri = p.getString("art_uri", "").orEmpty(),
        )
    }
}
