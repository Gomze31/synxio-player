package fr.synxio.player.widget

import android.content.Context
import android.content.res.Configuration
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pousse l'état de lecture vers tous les widgets posés.
 *
 * Chaque widget reçoit la photo dans son propre état Glance : c'est ce changement
 * d'état qui provoque la recomposition. Relire la base hors de la composition, comme
 * avant, laissait le widget figé tant que sa session Glance restait ouverte.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: WidgetArtRenderer,
    private val settingsRepository: SettingsRepository,
) {
    private val mutex = Mutex()
    private var lastPublished: WidgetSnapshot? = null
    private var lastDark: Boolean? = null

    /** Vrai si au moins un widget Synxio est posé : sinon inutile de rafraîchir. */
    suspend fun hasWidgets(): Boolean = withContext(Dispatchers.IO) {
        val manager = GlanceAppWidgetManager(context)
        runCatching {
            manager.getGlanceIds(SynxioWidget::class.java).isNotEmpty() ||
                manager.getGlanceIds(SynxioLargeWidget::class.java).isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * Publie [snapshot]. [artworkUri] n'est décodée que lorsque le morceau ou le thème
     * change : un simple passage lecture/pause ne relit pas l'image.
     */
    suspend fun publish(snapshot: WidgetSnapshot, artworkUri: Uri?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dark = isDark()
            val previous = lastPublished
            val prepared = if (previous != null && previous.mediaId == snapshot.mediaId &&
                lastDark == dark && previous.artPath.isNotEmpty()
            ) {
                WidgetArtRenderer.Prepared(previous.artPath, previous.backdropPath, previous.accent)
            } else {
                renderer.prepare(artworkUri, "${snapshot.mediaId}|$artworkUri", dark)
            }
            val full = snapshot.copy(
                artPath = prepared.artPath,
                backdropPath = prepared.backdropPath,
                accent = prepared.accent,
            )
            if (full == lastPublished && dark == lastDark) return@withLock
            lastPublished = full
            lastDark = dark
            WidgetSnapshotStore.save(context, full)
            pushToAll(full, dark)
        }
    }

    /** Redessine les widgets avec la dernière photo (thème de l'app changé, par exemple). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = lastPublished ?: WidgetSnapshotStore.load(context)
            pushToAll(snapshot, isDark())
        }
    }

    private suspend fun pushToAll(snapshot: WidgetSnapshot, dark: Boolean) {
        val manager = GlanceAppWidgetManager(context)
        val amoled = runCatching { settingsRepository.settings.first().themeMode == ThemeMode.AMOLED }
            .getOrDefault(false)
        val widgets: List<GlanceAppWidget> = listOf(SynxioWidget(), SynxioLargeWidget())
        for (widget in widgets) {
            val ids: List<GlanceId> = runCatching { manager.getGlanceIds(widget.javaClass) }.getOrDefault(emptyList())
            for (id in ids) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                        prefs.toMutablePreferences().apply {
                            snapshot.writeTo(this)
                            this[WidgetSnapshot.Keys.DARK] = dark
                            this[WidgetSnapshot.Keys.AMOLED] = amoled
                        }
                    }
                    widget.update(context, id)
                }.onFailure { android.util.Log.w("SynxioWidget", "Widget non rafraîchi", it) }
            }
        }
    }

    /** Thème des widgets : (sombre, AMOLED), d'après les réglages de l'application. */
    suspend fun theme(): Pair<Boolean, Boolean> {
        val amoled = runCatching { settingsRepository.settings.first().themeMode == ThemeMode.AMOLED }
            .getOrDefault(false)
        return isDark() to amoled
    }

    /**
     * Le widget suit le thème choisi DANS Synxio, pas celui du système : avec un mode
     * nuit système en « auto », un widget clair s'affichait sur un écran sombre.
     */
    private suspend fun isDark(): Boolean {
        val theme = runCatching { settingsRepository.settings.first().themeMode }.getOrDefault(ThemeMode.SYSTEM)
        return when (theme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
            ThemeMode.SYSTEM -> context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
