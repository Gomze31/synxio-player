package fr.synxio.player.data.analysis

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.AudioFeatureDao
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Progression de l'analyse en cours, lue dans WorkManager ; null quand rien ne tourne. */
data class AnalysisRun(val done: Int, val total: Int, val title: String?, val running: Boolean)

/**
 * Enfile, annule et observe [TrackAnalysisWorker].
 *
 * Une seule analyse à la fois (`KEEP`) : une demande pendant une analyse en cours ne fait
 * rien, le Worker reprend de toute façon tout ce qui manque.
 */
@Singleton
class TrackAnalysisScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val audioFeatureDao: AudioFeatureDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    private val onlyWhenCharging: StateFlow<Boolean> = settingsRepository.settings
        .map { it.analyseOnlyWhenCharging }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val progress: StateFlow<AnalysisRun?> by lazy {
        workManager.getWorkInfosForUniqueWorkFlow(NAME)
            .map { infos -> infos.firstOrNull()?.toRun() }
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    fun enqueue(priorityPaths: List<String> = emptyList()) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiresCharging(onlyWhenCharging.value)
            .build()
        val request = OneTimeWorkRequestBuilder<TrackAnalysisWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(TrackAnalysisWorker.KEY_PRIORITY to priorityPaths.toTypedArray()))
            .addTag(NAME)
            .build()
        workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(NAME)
    }

    /**
     * Après une synchronisation de bibliothèque : on n'analyse en arrière-plan que si
     * l'utilisateur a déjà opté (normalisation active, ou empreintes déjà calculées).
     */
    suspend fun enqueueIfOptedIn() {
        val optedIn = settingsRepository.settings.first().normalizeVolume || audioFeatureDao.count() > 0
        if (optedIn) enqueue()
    }

    private fun WorkInfo.toRun(): AnalysisRun? = when (state) {
        WorkInfo.State.RUNNING -> AnalysisRun(
            done = progress.getInt(TrackAnalysisWorker.KEY_DONE, 0),
            total = progress.getInt(TrackAnalysisWorker.KEY_TOTAL, 0),
            title = progress.getString(TrackAnalysisWorker.KEY_TITLE),
            running = true,
        )
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> AnalysisRun(0, 0, null, running = true)
        else -> null
    }

    companion object {
        const val NAME = "track-analysis"
    }
}
