package fr.synxio.player.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.AccentSource
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.LibraryTab
import fr.synxio.player.data.model.NowPlayingSkin
import fr.synxio.player.data.model.PlaylistSort
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.data.model.TextSize
import fr.synxio.player.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "synxio_settings")

/** Instantané complet des réglages : un seul objet à observer côté UI. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentSource: AccentSource = AccentSource.ARTWORK,
    val nowPlayingSkin: NowPlayingSkin = NowPlayingSkin.IMMERSIVE,
    val blurBackground: Boolean = true,
    val showVisualizer: Boolean = true,
    
    // Couleurs personnalisées
    val primaryColor: String = "",
    val secondaryColor: String = "",
    
    // Taille du texte
    val textSize: String = TextSize.NORMAL.name,

    val crossfadeMs: Int = 0,
    val gaplessEnabled: Boolean = true,
    val skipSilence: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val audioFocusPause: Boolean = true,
    val resumeOnHeadsetConnect: Boolean = false,
    val rememberQueue: Boolean = true,
    
    // Nouvelle option : reprendre après un appel
    val resumeAfterCall: Boolean = true,
    
    // Nouvelle option : arrêter après inactivité (en minutes, 0 = désactivé)
    val stopAfterInactivityMin: Int = 0,
    
    // Volume
    val defaultVolume: Float = 0.8f,
    val maxVolumeLimit: Boolean = false,
    val maxVolumeValue: Float = 1.0f,

    val minDurationSec: Int = 30,
    val defaultTab: LibraryTab = LibraryTab.SONGS,
    val songSort: SongSort = SongSort.TITLE,
    val songSortDescending: Boolean = false,
    val albumSort: AlbumSort = AlbumSort.TITLE,
    val albumSortDescending: Boolean = false,
    val artistSort: ArtistSort = ArtistSort.NAME,
    val artistSortDescending: Boolean = false,
    val albumGridColumns: Int = 2,

    // Nouvelle option : afficher les albums vides
    val showEmptyAlbums: Boolean = false,
    
    // Nouvelle option : regrouper les albums par artiste
    val groupAlbumsByArtist: Boolean = false,
    
    // Nouvelle option : scan automatique
    val autoScan: Boolean = true,
    
    // Nouvelle option : scan en arrière-plan
    val scanInBackground: Boolean = true,
    
    // Nouvelle option : masquer les doublons
    val hideDuplicates: Boolean = false,

    /** Normalisation du volume entre morceaux. */
    val normalizeVolume: Boolean = false,
    /**
     * Niveau cible en dBFS. -14 est la valeur retenue par la plupart des plateformes
     * de streaming : assez haut pour rester dynamique, assez bas pour que la majorite
     * des fichiers puisse y etre ramenee sans ecretage.
     */
    val normalizeTargetDbfs: Float = -14f,

    /** Ecarte de la lecture aleatoire les titres que l'on coupe systematiquement. */
    val shuffleSkipsDisliked: Boolean = false,

    /** Verification automatique des mises a jour, au plus une fois par jour. */
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,

    /**
     * Publication du morceau en cours dans un salon Discord.
     *
     * L'URL de webhook est un secret : quiconque la detient peut ecrire dans le salon.
     */
    val discordEnabled: Boolean = false,
    val discordWebhookUrl: String = "",

    /** Statut d'activite Discord, via le client Discord installe sur l'appareil. */
    val discordPresenceEnabled: Boolean = false,

    val lyricsOnlineEnabled: Boolean = true,
    val autoTranslateLyrics: Boolean = true,
    val scrobbleEnabled: Boolean = false,
    val lastFmSessionKey: String = "",
    val lastFmUsername: String = "",

    val equalizerEnabled: Boolean = false,
    val equalizerPreset: Int = -1,
    val equalizerBands: String = "",
    /** Identifiant du profil d'écoute appliqué (voir EqCurves), vide si réglage manuel. */
    val equalizerCurve: String = "",
    /** Thème visuel complet (tonalité de base + accent), voir ThemePreset. */
    val themePreset: String = "SYNXIO",
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val reverbPreset: Int = 0,
    val loudnessGain: Int = 0,
    
    // Nouvelles options pour les playlists
    val defaultPlaylistSort: String = PlaylistSort.NAME.name,
    val showPlaylistDuration: Boolean = true,
    val allowDuplicateSongs: Boolean = false,
    
    // Statistiques
    val defaultStatsPeriod: String = fr.synxio.player.data.model.StatsPeriod.ALL_TIME.name,
    
    // Accessibilité
    val reduceAnimations: Boolean = false,
    
    // Expérimental
    val developerMode: Boolean = false,

    // Widget Liquid Glass
    val widgetShowWave: Boolean = true,
    val widgetGlassOpacity: Float = 0.75f,
    val widgetWaveTint: String = "ACCENT",

    // Audio & Lecture avancée
    val autoRewindSec: Int = 0,
    val keepScreenOnNowPlaying: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_SOURCE = stringPreferencesKey("accent_source")
        val NP_SKIN = stringPreferencesKey("np_skin")
        val BLUR_BACKGROUND = booleanPreferencesKey("blur_background")
        val SHOW_VISUALIZER = booleanPreferencesKey("show_visualizer")
        
        // Couleurs personnalisées
        val PRIMARY_COLOR = stringPreferencesKey("primary_color")
        val SECONDARY_COLOR = stringPreferencesKey("secondary_color")
        val TEXT_SIZE = stringPreferencesKey("text_size")

        val CROSSFADE_MS = intPreferencesKey("crossfade_ms")
        val GAPLESS = booleanPreferencesKey("gapless")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val AUDIO_FOCUS_PAUSE = booleanPreferencesKey("audio_focus_pause")
        val RESUME_ON_HEADSET = booleanPreferencesKey("resume_on_headset")
        val REMEMBER_QUEUE = booleanPreferencesKey("remember_queue")
        
        // Nouvelle option : reprendre après un appel
        val RESUME_AFTER_CALL = booleanPreferencesKey("resume_after_call")
        
        // Nouvelle option : arrêter après inactivité
        val STOP_AFTER_INACTIVITY = intPreferencesKey("stop_after_inactivity_min")
        
        // Volume
        val DEFAULT_VOLUME = floatPreferencesKey("default_volume")
        val MAX_VOLUME_LIMIT = booleanPreferencesKey("max_volume_limit")
        val MAX_VOLUME_VALUE = floatPreferencesKey("max_volume_value")

        val MIN_DURATION = intPreferencesKey("min_duration_sec")
        val DEFAULT_TAB = stringPreferencesKey("default_tab")
        val SONG_SORT = stringPreferencesKey("song_sort")
        val SONG_SORT_DESC = booleanPreferencesKey("song_sort_desc")
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val ALBUM_SORT_DESC = booleanPreferencesKey("album_sort_desc")
        val ARTIST_SORT = stringPreferencesKey("artist_sort")
        val ARTIST_SORT_DESC = booleanPreferencesKey("artist_sort_desc")
        val ALBUM_COLUMNS = intPreferencesKey("album_columns")

        // Nouvelles options pour la bibliothèque
        val SHOW_EMPTY_ALBUMS = booleanPreferencesKey("show_empty_albums")
        val GROUP_ALBUMS_BY_ARTIST = booleanPreferencesKey("group_albums_by_artist")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val SCAN_IN_BACKGROUND = booleanPreferencesKey("scan_in_background")
        val HIDE_DUPLICATES = booleanPreferencesKey("hide_duplicates")
        val NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")
        val NORMALIZE_TARGET_DBFS = floatPreferencesKey("normalize_target_dbfs")
        val SHUFFLE_SKIPS_DISLIKED = booleanPreferencesKey("shuffle_skips_disliked")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val DISCORD_ENABLED = booleanPreferencesKey("discord_enabled")
        val DISCORD_PRESENCE_ENABLED = booleanPreferencesKey("discord_presence_enabled")
        val DISCORD_WEBHOOK_URL = stringPreferencesKey("discord_webhook_url")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")

        val LYRICS_ONLINE = booleanPreferencesKey("lyrics_online")
        val AUTO_TRANSLATE_LYRICS = booleanPreferencesKey("auto_translate_lyrics")
        val SCROBBLE = booleanPreferencesKey("scrobble")
        val LASTFM_SESSION = stringPreferencesKey("lastfm_session")
        val LASTFM_USER = stringPreferencesKey("lastfm_user")

        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = intPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val EQ_CURVE = stringPreferencesKey("eq_curve")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val VIRTUALIZER = intPreferencesKey("virtualizer")
        val REVERB_PRESET = intPreferencesKey("reverb_preset")
        val LOUDNESS = intPreferencesKey("loudness")
        
        // Nouvelles options pour les playlists
        val DEFAULT_PLAYLIST_SORT = stringPreferencesKey("default_playlist_sort")
        val SHOW_PLAYLIST_DURATION = booleanPreferencesKey("show_playlist_duration")
        val ALLOW_DUPLICATE_SONGS = booleanPreferencesKey("allow_duplicate_songs")
        
        // Statistiques
        val DEFAULT_STATS_PERIOD = stringPreferencesKey("default_stats_period")
        
        // Accessibilité
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        
        // Expérimental
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        // Widget Liquid Glass
        val WIDGET_SHOW_WAVE = booleanPreferencesKey("widget_show_wave")
        val WIDGET_GLASS_OPACITY = floatPreferencesKey("widget_glass_opacity")
        val WIDGET_WAVE_TINT = stringPreferencesKey("widget_wave_tint")

        // Audio & Lecture avancée
        val AUTO_REWIND_SEC = intPreferencesKey("auto_rewind_sec")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_now_playing")

        val LAST_SCAN = longPreferencesKey("last_scan")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            Settings(
                themeMode = p.enum(Keys.THEME_MODE, ThemeMode.SYSTEM),
                accentSource = p.enum(Keys.ACCENT_SOURCE, AccentSource.ARTWORK),
                nowPlayingSkin = p.enum(Keys.NP_SKIN, NowPlayingSkin.IMMERSIVE),
                blurBackground = p[Keys.BLUR_BACKGROUND] ?: true,
                showVisualizer = p[Keys.SHOW_VISUALIZER] ?: true,
                
                // Couleurs personnalisées
                primaryColor = p[Keys.PRIMARY_COLOR].orEmpty(),
                secondaryColor = p[Keys.SECONDARY_COLOR].orEmpty(),
                textSize = p[Keys.TEXT_SIZE] ?: TextSize.NORMAL.name,

                crossfadeMs = p[Keys.CROSSFADE_MS] ?: 0,
                gaplessEnabled = p[Keys.GAPLESS] ?: true,
                skipSilence = p[Keys.SKIP_SILENCE] ?: false,
                playbackSpeed = p[Keys.SPEED] ?: 1f,
                playbackPitch = p[Keys.PITCH] ?: 1f,
                audioFocusPause = p[Keys.AUDIO_FOCUS_PAUSE] ?: true,
                resumeOnHeadsetConnect = p[Keys.RESUME_ON_HEADSET] ?: false,
                rememberQueue = p[Keys.REMEMBER_QUEUE] ?: true,
                
                // Nouvelle option : reprendre après un appel
                resumeAfterCall = p[Keys.RESUME_AFTER_CALL] ?: true,
                
                // Nouvelle option : arrêter après inactivité
                stopAfterInactivityMin = p[Keys.STOP_AFTER_INACTIVITY] ?: 0,
                
                // Volume
                defaultVolume = p[Keys.DEFAULT_VOLUME] ?: 0.8f,
                maxVolumeLimit = p[Keys.MAX_VOLUME_LIMIT] ?: false,
                maxVolumeValue = p[Keys.MAX_VOLUME_VALUE] ?: 1.0f,

                minDurationSec = p[Keys.MIN_DURATION] ?: 30,
                defaultTab = p.enum(Keys.DEFAULT_TAB, LibraryTab.SONGS),
                songSort = p.enum(Keys.SONG_SORT, SongSort.TITLE),
                songSortDescending = p[Keys.SONG_SORT_DESC] ?: false,
                albumSort = p.enum(Keys.ALBUM_SORT, AlbumSort.TITLE),
                albumSortDescending = p[Keys.ALBUM_SORT_DESC] ?: false,
                artistSort = p.enum(Keys.ARTIST_SORT, ArtistSort.NAME),
                artistSortDescending = p[Keys.ARTIST_SORT_DESC] ?: false,
                albumGridColumns = p[Keys.ALBUM_COLUMNS] ?: 2,

                // Nouvelles options pour la bibliothèque
                showEmptyAlbums = p[Keys.SHOW_EMPTY_ALBUMS] ?: false,
                groupAlbumsByArtist = p[Keys.GROUP_ALBUMS_BY_ARTIST] ?: false,
                autoScan = p[Keys.AUTO_SCAN] ?: true,
                scanInBackground = p[Keys.SCAN_IN_BACKGROUND] ?: true,
                hideDuplicates = p[Keys.HIDE_DUPLICATES] ?: false,
                normalizeVolume = p[Keys.NORMALIZE_VOLUME] ?: false,
                normalizeTargetDbfs = p[Keys.NORMALIZE_TARGET_DBFS] ?: -14f,
                shuffleSkipsDisliked = p[Keys.SHUFFLE_SKIPS_DISLIKED] ?: false,
                autoCheckUpdates = p[Keys.AUTO_CHECK_UPDATES] ?: true,
                discordEnabled = p[Keys.DISCORD_ENABLED] ?: false,
                discordPresenceEnabled = p[Keys.DISCORD_PRESENCE_ENABLED] ?: false,
                discordWebhookUrl = p[Keys.DISCORD_WEBHOOK_URL] ?: "",
                lastUpdateCheck = p[Keys.LAST_UPDATE_CHECK] ?: 0L,

                lyricsOnlineEnabled = p[Keys.LYRICS_ONLINE] ?: true,
                autoTranslateLyrics = p[Keys.AUTO_TRANSLATE_LYRICS] ?: true,
                scrobbleEnabled = p[Keys.SCROBBLE] ?: false,
                lastFmSessionKey = p[Keys.LASTFM_SESSION].orEmpty(),
                lastFmUsername = p[Keys.LASTFM_USER].orEmpty(),

                equalizerEnabled = p[Keys.EQ_ENABLED] ?: false,
                equalizerPreset = p[Keys.EQ_PRESET] ?: -1,
                equalizerBands = p[Keys.EQ_BANDS].orEmpty(),
                equalizerCurve = p[Keys.EQ_CURVE].orEmpty(),
                themePreset = p[Keys.THEME_PRESET] ?: "SYNXIO",
                bassBoost = p[Keys.BASS_BOOST] ?: 0,
                virtualizer = p[Keys.VIRTUALIZER] ?: 0,
                reverbPreset = p[Keys.REVERB_PRESET] ?: 0,
                loudnessGain = p[Keys.LOUDNESS] ?: 0,
                
                // Nouvelles options pour les playlists
                defaultPlaylistSort = p[Keys.DEFAULT_PLAYLIST_SORT] ?: PlaylistSort.NAME.name,
                showPlaylistDuration = p[Keys.SHOW_PLAYLIST_DURATION] ?: true,
                allowDuplicateSongs = p[Keys.ALLOW_DUPLICATE_SONGS] ?: false,
                
                // Statistiques
                defaultStatsPeriod = p[Keys.DEFAULT_STATS_PERIOD] ?: fr.synxio.player.data.model.StatsPeriod.ALL_TIME.name,
                
                // Accessibilité
                reduceAnimations = p[Keys.REDUCE_ANIMATIONS] ?: false,
                
                // Expérimental
                developerMode = p[Keys.DEVELOPER_MODE] ?: false,

                // Widget Liquid Glass
                widgetShowWave = p[Keys.WIDGET_SHOW_WAVE] ?: true,
                widgetGlassOpacity = p[Keys.WIDGET_GLASS_OPACITY] ?: 0.75f,
                widgetWaveTint = p[Keys.WIDGET_WAVE_TINT] ?: "ACCENT",

                // Audio & Lecture avancée
                autoRewindSec = p[Keys.AUTO_REWIND_SEC] ?: 0,
                keepScreenOnNowPlaying = p[Keys.KEEP_SCREEN_ON] ?: false,
            )
        }

    private inline fun <reified E : Enum<E>> Preferences.enum(
        key: Preferences.Key<String>,
        default: E,
    ): E = this[key]?.let { name -> runCatching { enumValueOf<E>(name) }.getOrNull() } ?: default

    suspend fun setThemeMode(value: ThemeMode) = put(Keys.THEME_MODE, value.name)
    suspend fun setAccentSource(value: AccentSource) = put(Keys.ACCENT_SOURCE, value.name)
    suspend fun setNowPlayingSkin(value: NowPlayingSkin) = put(Keys.NP_SKIN, value.name)
    suspend fun setBlurBackground(value: Boolean) = put(Keys.BLUR_BACKGROUND, value)
    suspend fun setShowVisualizer(value: Boolean) = put(Keys.SHOW_VISUALIZER, value)

    suspend fun setCrossfadeMs(value: Int) = put(Keys.CROSSFADE_MS, value)
    suspend fun setGapless(value: Boolean) = put(Keys.GAPLESS, value)
    suspend fun setSkipSilence(value: Boolean) = put(Keys.SKIP_SILENCE, value)
    suspend fun setSpeed(value: Float) = put(Keys.SPEED, value)
    suspend fun setPitch(value: Float) = put(Keys.PITCH, value)
    suspend fun setAudioFocusPause(value: Boolean) = put(Keys.AUDIO_FOCUS_PAUSE, value)
    suspend fun setResumeOnHeadsetConnect(value: Boolean) = put(Keys.RESUME_ON_HEADSET, value)
    suspend fun setRememberQueue(value: Boolean) = put(Keys.REMEMBER_QUEUE, value)

    suspend fun setMinDurationSec(value: Int) = put(Keys.MIN_DURATION, value)
    suspend fun setDefaultTab(value: LibraryTab) = put(Keys.DEFAULT_TAB, value.name)
    suspend fun setSongSort(sort: SongSort, descending: Boolean) {
        context.dataStore.edit {
            it[Keys.SONG_SORT] = sort.name
            it[Keys.SONG_SORT_DESC] = descending
        }
    }

    suspend fun setAlbumSort(sort: AlbumSort, descending: Boolean) {
        context.dataStore.edit {
            it[Keys.ALBUM_SORT] = sort.name
            it[Keys.ALBUM_SORT_DESC] = descending
        }
    }

    suspend fun setArtistSort(sort: ArtistSort, descending: Boolean) {
        context.dataStore.edit {
            it[Keys.ARTIST_SORT] = sort.name
            it[Keys.ARTIST_SORT_DESC] = descending
        }
    }

    suspend fun setAlbumColumns(value: Int) = put(Keys.ALBUM_COLUMNS, value)

    // Couleurs personnalisées
    suspend fun setPrimaryColor(value: String) = put(Keys.PRIMARY_COLOR, value)
    suspend fun setSecondaryColor(value: String) = put(Keys.SECONDARY_COLOR, value)
    suspend fun setTextSize(value: String) = put(Keys.TEXT_SIZE, value)

    suspend fun setLyricsOnline(value: Boolean) = put(Keys.LYRICS_ONLINE, value)
    suspend fun setAutoTranslateLyrics(value: Boolean) = put(Keys.AUTO_TRANSLATE_LYRICS, value)
    suspend fun setScrobbleEnabled(value: Boolean) = put(Keys.SCROBBLE, value)
    suspend fun setLastFmSession(sessionKey: String, username: String) {
        context.dataStore.edit {
            it[Keys.LASTFM_SESSION] = sessionKey
            it[Keys.LASTFM_USER] = username
        }
    }

    suspend fun setEqualizerEnabled(value: Boolean) = put(Keys.EQ_ENABLED, value)
    suspend fun setEqualizerPreset(value: Int) = put(Keys.EQ_PRESET, value)
    suspend fun setEqualizerBands(levels: List<Short>) =
        put(Keys.EQ_BANDS, levels.joinToString(","))

    suspend fun setEqualizerCurve(curveId: String) = put(Keys.EQ_CURVE, curveId)

    suspend fun setThemePreset(preset: String) = put(Keys.THEME_PRESET, preset)

    suspend fun setBassBoost(value: Int) = put(Keys.BASS_BOOST, value)
    suspend fun setVirtualizer(value: Int) = put(Keys.VIRTUALIZER, value)
    suspend fun setReverbPreset(value: Int) = put(Keys.REVERB_PRESET, value)
    suspend fun setLoudnessGain(value: Int) = put(Keys.LOUDNESS, value)
    suspend fun setLastScan(value: Long) = put(Keys.LAST_SCAN, value)

    // Nouvelles options pour la lecture
    suspend fun setResumeAfterCall(value: Boolean) = put(Keys.RESUME_AFTER_CALL, value)
    suspend fun setStopAfterInactivity(value: Int) = put(Keys.STOP_AFTER_INACTIVITY, value)
    
    // Volume
    suspend fun setDefaultVolume(value: Float) = put(Keys.DEFAULT_VOLUME, value)
    suspend fun setMaxVolumeLimit(value: Boolean) = put(Keys.MAX_VOLUME_LIMIT, value)
    suspend fun setMaxVolumeValue(value: Float) = put(Keys.MAX_VOLUME_VALUE, value)

    // Nouvelles options pour la bibliothèque
    suspend fun setShowEmptyAlbums(value: Boolean) = put(Keys.SHOW_EMPTY_ALBUMS, value)
    suspend fun setGroupAlbumsByArtist(value: Boolean) = put(Keys.GROUP_ALBUMS_BY_ARTIST, value)
    suspend fun setAutoScan(value: Boolean) = put(Keys.AUTO_SCAN, value)
    suspend fun setScanInBackground(value: Boolean) = put(Keys.SCAN_IN_BACKGROUND, value)
    suspend fun setHideDuplicates(value: Boolean) = put(Keys.HIDE_DUPLICATES, value)
    suspend fun setNormalizeVolume(value: Boolean) = put(Keys.NORMALIZE_VOLUME, value)
    suspend fun setNormalizeTargetDbfs(value: Float) = put(Keys.NORMALIZE_TARGET_DBFS, value)
    suspend fun setShuffleSkipsDisliked(value: Boolean) = put(Keys.SHUFFLE_SKIPS_DISLIKED, value)
    suspend fun setAutoCheckUpdates(value: Boolean) = put(Keys.AUTO_CHECK_UPDATES, value)
    suspend fun setDiscordEnabled(value: Boolean) = put(Keys.DISCORD_ENABLED, value)
    suspend fun setDiscordPresenceEnabled(value: Boolean) =
        put(Keys.DISCORD_PRESENCE_ENABLED, value)
    suspend fun setDiscordWebhookUrl(value: String) = put(Keys.DISCORD_WEBHOOK_URL, value.trim())
    suspend fun setLastUpdateCheck(value: Long) = put(Keys.LAST_UPDATE_CHECK, value)
    
    // Nouvelles options pour les playlists
    suspend fun setDefaultPlaylistSort(value: String) = put(Keys.DEFAULT_PLAYLIST_SORT, value)
    suspend fun setShowPlaylistDuration(value: Boolean) = put(Keys.SHOW_PLAYLIST_DURATION, value)
    suspend fun setAllowDuplicateSongs(value: Boolean) = put(Keys.ALLOW_DUPLICATE_SONGS, value)
    
    // Statistiques
    suspend fun setDefaultStatsPeriod(value: String) = put(Keys.DEFAULT_STATS_PERIOD, value)
    
    // Accessibilité
    suspend fun setReduceAnimations(value: Boolean) = put(Keys.REDUCE_ANIMATIONS, value)
    
    // Expérimental
    suspend fun setDeveloperMode(value: Boolean) = put(Keys.DEVELOPER_MODE, value)

    // Widget Liquid Glass
    suspend fun setWidgetShowWave(value: Boolean) = put(Keys.WIDGET_SHOW_WAVE, value)
    suspend fun setWidgetGlassOpacity(value: Float) = put(Keys.WIDGET_GLASS_OPACITY, value)
    suspend fun setWidgetWaveTint(value: String) = put(Keys.WIDGET_WAVE_TINT, value)

    // Audio & Lecture avancée
    suspend fun setAutoRewindSec(value: Int) = put(Keys.AUTO_REWIND_SEC, value)
    suspend fun setKeepScreenOnNowPlaying(value: Boolean) = put(Keys.KEEP_SCREEN_ON, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}

/** Décode la chaîne stockée pour l'égaliseur en niveaux de bandes. */
fun String.toBandLevels(): List<Short> =
    if (isBlank()) emptyList()
    else split(',').mapNotNull { it.trim().toShortOrNull() }
