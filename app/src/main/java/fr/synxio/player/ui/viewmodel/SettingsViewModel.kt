package fr.synxio.player.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.lastfm.LastFmScrobbler
import fr.synxio.player.data.model.AccentSource
import fr.synxio.player.data.model.LibraryTab
import fr.synxio.player.data.model.NowPlayingSkin
import fr.synxio.player.data.model.PlaylistSort
import fr.synxio.player.data.model.ThemeColor
import fr.synxio.player.data.model.ThemeMode
import fr.synxio.player.data.model.TextSize
import fr.synxio.player.data.repo.ArtworkColorRepository
import fr.synxio.player.data.repo.AnalysisProgress
import fr.synxio.player.data.repo.LoudnessProgress
import fr.synxio.player.data.repo.LoudnessRepository
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.SimilarityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val scrobbler: LastFmScrobbler,
    @ApplicationContext private val context: Context,
    private val artworkColorRepository: ArtworkColorRepository,
    private val loudness: LoudnessRepository,
    private val similarity: SimilarityRepository,
    private val music: MusicRepository,
) : ViewModel() {

    // ========================================================================
    // EMPREINTES SONORES
    // ========================================================================

    val similarityProgress: StateFlow<AnalysisProgress> = similarity.progress

    fun pendingFingerprintCount(): Int = similarity.pendingCount(music.library.value.songs)

    fun analyseFingerprints() = similarity.analyseLibrary(music.library.value.songs)

    fun cancelFingerprints() = similarity.cancel()

    /** Efface et relance : garder des empreintes perimees n'aurait aucun interet. */
    fun resetFingerprints() = viewModelScope.launch {
        similarity.clear()
        similarity.analyseLibrary(music.library.value.songs)
    }

    // ========================================================================
    // NORMALISATION DU VOLUME
    // ========================================================================

    val loudnessProgress: StateFlow<LoudnessProgress> = loudness.progress

    /** Morceaux encore sans mesure : sert a annoncer le travail restant. */
    fun pendingLoudnessCount(): Int = loudness.pendingCount(music.library.value.songs)

    fun setNormalizeVolume(value: Boolean) = viewModelScope.launch {
        settings.setNormalizeVolume(value)
        // Activer l'option sans mesure ne changerait rien : on lance l'analyse dans la
        // foulee plutot que de laisser l'utilisateur devant un reglage sans effet.
        if (value) loudness.analyseLibrary(music.library.value.songs)
    }

    fun setNormalizeTargetDbfs(value: Float) = viewModelScope.launch {
        settings.setNormalizeTargetDbfs(value)
    }

    fun analyseLoudness() = loudness.analyseLibrary(music.library.value.songs)

    fun cancelLoudnessAnalysis() = loudness.cancel()

    /** Efface les mesures et relance immediatement : un effacement seul laisserait
     *  la normalisation active mais sans effet. */
    fun resetLoudness() = viewModelScope.launch {
        loudness.clear()
        loudness.analyseLibrary(music.library.value.songs)
    }

    fun setShuffleSkipsDisliked(value: Boolean) = viewModelScope.launch {
        settings.setShuffleSkipsDisliked(value)
    }

    /** Sans clé d'API compilée, l'option Last.fm est masquée plutôt que cassée. */
    val lastFmAvailable: Boolean get() = scrobbler.isConfigured

    // ========================================================================
    // APPARENCE
    // ========================================================================

    fun setThemeMode(value: ThemeMode) = update { settings.setThemeMode(value) }

    /**
     * Thème visuel complet : tonalité de base + accent + traitement des surfaces.
     *
     * On bascule aussi la source d'accent : sinon la teinte extraite de la pochette
     * continuerait d'écraser la couleur du thème, et choisir un thème n'aurait
     * aucun effet visible.
     */
    fun setThemePreset(value: fr.synxio.player.ui.theme.ThemePreset) = update {
        settings.setThemePreset(value.name)
        settings.setAccentSource(AccentSource.SYNXIO)
    }
    fun setAccentSource(value: AccentSource) = update { settings.setAccentSource(value) }
    fun setSkin(value: NowPlayingSkin) = update { settings.setNowPlayingSkin(value) }
    fun setBlurBackground(value: Boolean) = update { settings.setBlurBackground(value) }
    fun setShowVisualizer(value: Boolean) = update { settings.setShowVisualizer(value) }
    fun setAlbumColumns(value: Int) = update { settings.setAlbumColumns(value) }

    /** Couleur primaire personnalisée */
    fun setPrimaryColor(value: ThemeColor) = update { settings.setPrimaryColor(value.value) }
    fun setPrimaryColorString(value: String) = update { settings.setPrimaryColor(value) }

    /** Couleur secondaire personnalisée */
    fun setSecondaryColor(value: ThemeColor) = update { settings.setSecondaryColor(value.value) }
    fun setSecondaryColorString(value: String) = update { settings.setSecondaryColor(value) }

    /** Taille du texte */
    fun setTextSize(value: TextSize) = update { settings.setTextSize(value.name) }

    // ========================================================================
    // LECTURE
    // ========================================================================

    fun setCrossfade(ms: Int) = update { settings.setCrossfadeMs(ms) }
    fun setGapless(value: Boolean) = update { settings.setGapless(value) }
    fun setSkipSilence(value: Boolean) = update { settings.setSkipSilence(value) }
    fun setRememberQueue(value: Boolean) = update { settings.setRememberQueue(value) }

    /** Reprendre la lecture quand un casque est branché ou appairé */
    fun setResumeOnHeadsetConnect(value: Boolean) =
        update { settings.setResumeOnHeadsetConnect(value) }

    /** Reprendre après un appel */
    fun setResumeAfterCall(value: Boolean) = update { settings.setResumeAfterCall(value) }

    /** Arrêter après inactivité (en minutes, 0 = désactivé) */
    fun setStopAfterInactivity(value: Int) = update { settings.setStopAfterInactivity(value) }

    /** Volume par défaut */
    fun setDefaultVolume(value: Float) = update { settings.setDefaultVolume(value) }

    /** Limiter le volume maximum */
    fun setMaxVolumeLimit(value: Boolean) = update { settings.setMaxVolumeLimit(value) }
    fun setMaxVolumeValue(value: Float) = update { settings.setMaxVolumeValue(value) }

    // ========================================================================
    // BIBLIOTHÈQUE
    // ========================================================================

    fun setMinDuration(seconds: Int) = update { settings.setMinDurationSec(seconds) }
    fun setDefaultTab(tab: LibraryTab) = update { settings.setDefaultTab(tab) }

    /** Afficher les albums vides */
    fun setShowEmptyAlbums(value: Boolean) = update { settings.setShowEmptyAlbums(value) }

    /** Regrouper les albums par artiste */
    fun setGroupAlbumsByArtist(value: Boolean) = update { settings.setGroupAlbumsByArtist(value) }

    /** Scan automatique */
    fun setAutoScan(value: Boolean) = update { settings.setAutoScan(value) }

    /** Scan en arrière-plan */
    fun setScanInBackground(value: Boolean) = update { settings.setScanInBackground(value) }

    /** Masquer les doublons */
    fun setHideDuplicates(value: Boolean) = update { settings.setHideDuplicates(value) }

    // ========================================================================
    // PLAYLISTS
    // ========================================================================

    /** Tri par défaut des playlists */
    fun setDefaultPlaylistSort(value: PlaylistSort) = update { settings.setDefaultPlaylistSort(value.name) }

    /** Afficher la durée totale des playlists */
    fun setShowPlaylistDuration(value: Boolean) = update { settings.setShowPlaylistDuration(value) }

    /** Autoriser les doublons dans les playlists */
    fun setAllowDuplicateSongs(value: Boolean) = update { settings.setAllowDuplicateSongs(value) }

    // ========================================================================
    // STATISTIQUES
    // ========================================================================

    /** Période par défaut pour les statistiques */
    fun setDefaultStatsPeriod(value: fr.synxio.player.data.model.StatsPeriod) = 
        update { settings.setDefaultStatsPeriod(value.name) }

    // ========================================================================
    // PAROLES ET SCROBBLING
    // ========================================================================

    fun setLyricsOnline(value: Boolean) = update { settings.setLyricsOnline(value) }
    fun setScrobbleEnabled(value: Boolean) = update { settings.setScrobbleEnabled(value) }

    // ========================================================================
    // STOCKAGE
    // ========================================================================

    /** Effacer le cache des pochettes */
    fun clearArtworkCache() = viewModelScope.launch {
        artworkColorRepository.clearCache()
    }

    /** Effacer le cache des paroles */
    fun clearLyricsCache() = update { 
        // À implémenter dans LyricsRepository
    }

    // ========================================================================
    // ACCESSIBILITÉ
    // ========================================================================

    /** Réduire les animations */
    fun setReduceAnimations(value: Boolean) = update { settings.setReduceAnimations(value) }

    // ========================================================================
    // EXPÉRIMENTAL
    // ========================================================================

    /** Mode développeur */
    fun setDeveloperMode(value: Boolean) = update { settings.setDeveloperMode(value) }

    // ========================================================================
    // UTILITAIRES
    // ========================================================================

    fun loginLastFm(username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(scrobbler.login(username, password).isSuccess)
        }
    }

    fun logoutLastFm() = update { scrobbler.logout() }

    /**
     * Récupère l'espace utilisé par les caches.
     */
    val usedStorage: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    // À implémenter : calculer l'espace utilisé

    /**
     * Version de l'application.
     */
    val appVersion: String by lazy {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "2.0.0"
        } catch (e: Exception) {
            "2.0.0"
        }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
