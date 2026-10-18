package fr.synxio.player.widget

import android.content.Context
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pousse l'état de lecture vers tous les widgets posés.
 *
 * Chaque widget reçoit la photo dans son propre état Glance : c'est ce changement
 * d'état qui provoque la recomposition. Relire la base hors de la composition, comme
 * avant, laissait le widget figé tant que sa session Glance restait ouverte.
 *
 * Sobriété : le service publie souvent (sauvegarde de position toutes les dix secondes,
 * minuteur de progression). Une photo qui dessinerait le même widget est écartée ici,
 * sans réveiller le lanceur ni refaire transiter les images ; et sans widget posé, rien
 * n'est décodé.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: WidgetArtRenderer,
    private val settingsRepository: SettingsRepository,
) {
    private val mutex = Mutex()
    private var lastPublished: WidgetSnapshot? = null
    private var lastTheme: WidgetTheme? = null

    /** Vrai si au moins un widget Synxio est posé : sinon inutile de rafraîchir. */
    suspend fun hasWidgets(): Boolean = withContext(Dispatchers.IO) {
        val manager = GlanceAppWidgetManager(context)
        runCatching {
            manager.getGlanceIds(SynxioWidget::class.java).isNotEmpty() ||
                manager.getGlanceIds(SynxioLargeWidget::class.java).isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * Publie [snapshot]. [artworkUri] n'est décodée que lorsque la pochette change :
     * un simple passage lecture/pause ne relit pas l'image.
     */
    suspend fun publish(snapshot: WidgetSnapshot, artworkUri: Uri?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val theme = themeMode()
            val incoming = snapshot.copy(artUri = artworkUri?.toString().orEmpty())
            val previous = lastPublished
            if (previous != null && theme == lastTheme && previous.displaysSameAs(incoming)) return@withLock

            // Aucun widget posé : on garde la photo pour amorcer un futur widget, sans
            // décoder la pochette (préparée à la pose, voir ensurePrepared).
            if (!hasWidgets()) {
                val kept = withPreparedFrom(previous, incoming) ?: incoming
                lastPublished = kept
                lastTheme = theme
                WidgetSnapshotStore.save(context, kept)
                return@withLock
            }

            val full = withPreparedFrom(previous, incoming) ?: prepared(incoming)
            lastPublished = full
            lastTheme = theme
            WidgetSnapshotStore.save(context, full)
            pushToAll(full, theme)
        }
    }

    /**
     * Images de la photo précédente, si elles valent pour [incoming] (même pochette,
     * fichiers toujours là) ; null s'il faut les refaire.
     */
    private fun withPreparedFrom(previous: WidgetSnapshot?, incoming: WidgetSnapshot): WidgetSnapshot? {
        if (previous == null || previous.mediaId != incoming.mediaId || previous.artUri != incoming.artUri) return null
        if (!isCurrentRender(previous)) return null
        return incoming.copy(
            artPath = previous.artPath,
            backdropPath = previous.backdropPath,
            accent = previous.accent,
            accentLight = previous.accentLight,
        )
    }

    private fun prepared(s: WidgetSnapshot): WidgetSnapshot {
        if (s.isEmpty) return s.copy(artPath = "", backdropPath = "", accent = 0, accentLight = 0)
        val uri = s.artUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val p = renderer.prepare(uri, "${s.mediaId}|${s.artUri}")
        return s.copy(artPath = p.artPath, backdropPath = p.glowPath, accent = p.accentDark, accentLight = p.accentLight)
    }

    /**
     * Photo prête à dessiner pour un widget qui se compose : images préparées si elles
     * manquent (widget posé alors qu'aucun n'existait, fichiers d'une version
     * précédente). Appelé hors composition, depuis `provideGlance`.
     */
    internal suspend fun ensurePrepared(snapshot: WidgetSnapshot): WidgetSnapshot = withContext(Dispatchers.IO) {
        if (snapshot.isEmpty || isCurrentRender(snapshot)) return@withContext snapshot
        mutex.withLock {
            val full = prepared(snapshot)
            // La photo a pu changer pendant l'attente du verrou : on ne remplace que la même.
            val last = lastPublished
            if (last == null || last.mediaId == full.mediaId) {
                lastPublished = full
                WidgetSnapshotStore.save(context, full)
            }
            full
        }
    }

    /** Fichiers du rendu actuel présents ; ceux d'une ancienne version sont refaits. */
    private fun isCurrentRender(s: WidgetSnapshot): Boolean =
        s.artPath.endsWith(".png") && s.backdropPath.endsWith(".png") &&
            File(s.artPath).exists() && File(s.backdropPath).exists()

    /** Redessine les widgets avec la dernière photo (thème de l'app changé, par exemple). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val theme = themeMode()
            val snapshot = lastPublished ?: WidgetSnapshotStore.load(context)
            lastTheme = theme
            pushToAll(snapshot, theme)
        }
    }

    private suspend fun pushToAll(snapshot: WidgetSnapshot, theme: WidgetTheme) {
        val manager = GlanceAppWidgetManager(context)
        val widgets: List<GlanceAppWidget> = listOf(SynxioWidget(), SynxioLargeWidget())
        for (widget in widgets) {
            val ids: List<GlanceId> = runCatching { manager.getGlanceIds(widget.javaClass) }.getOrDefault(emptyList())
            for (id in ids) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                        prefs.toMutablePreferences().apply {
                            snapshot.writeTo(this)
                            this[WidgetSnapshot.Keys.THEME] = theme.ordinal
                        }
                    }
                    widget.update(context, id)
                }.onFailure { android.util.Log.w("SynxioWidget", "Widget non rafraîchi", it) }
            }
        }
    }

    /**
     * Thème des widgets.
     *
     * « Système » (le réglage par défaut) n'est pas résolu ici : le widget porte ses
     * couleurs jour et nuit, et suit le mode sombre du téléphone même app fermée. Un
     * thème forcé dans Synxio (clair, sombre, AMOLED) s'impose au widget, comme à l'app.
     */
    internal suspend fun themeMode(): WidgetTheme {
        val mode = runCatching { settingsRepository.settings.first().themeMode }.getOrDefault(ThemeMode.SYSTEM)
        return when (mode) {
            ThemeMode.SYSTEM -> WidgetTheme.SYSTEM
            ThemeMode.LIGHT -> WidgetTheme.LIGHT
            ThemeMode.DARK -> WidgetTheme.DARK
            ThemeMode.AMOLED -> WidgetTheme.AMOLED
        }
    }
}
