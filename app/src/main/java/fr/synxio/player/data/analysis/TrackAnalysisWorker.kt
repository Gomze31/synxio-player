package fr.synxio.player.data.analysis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.synxio.player.data.db.AudioFeatureDao
import fr.synxio.player.data.db.AudioFeatureEntity
import fr.synxio.player.data.db.LibrarySongDao
import fr.synxio.player.data.db.LoudnessDao
import fr.synxio.player.data.db.LoudnessEntity
import fr.synxio.player.data.media.AudioFeatureAnalyzer
import fr.synxio.player.data.media.LoudnessAnalyzer
import fr.synxio.player.data.media.TrackTraitsAnalyzer
import fr.synxio.player.data.model.AnalysisItem
import fr.synxio.player.data.model.TrackTraits
import fr.synxio.player.data.model.AnalysisPlanner
import fr.synxio.player.data.model.LibraryBuilder
import fr.synxio.player.data.repo.LibraryFiltersSource
import fr.synxio.player.data.repo.ReplayGain
import fr.synxio.player.data.repo.toSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Analyse de fond des titres : niveau sonore (normalisation) et empreinte 27-D
 * (similarité, ambiances). Séquentiel : chaque mesure instancie un décodeur matériel,
 * et les décodeurs de l'appareil sont en nombre limité.
 *
 * Chaque résultat est écrit en base aussitôt : si le système arrête le Worker, le
 * prochain enfilage reprend là où il en était.
 */
@HiltWorker
class TrackAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val songDao: LibrarySongDao,
    private val filters: LibraryFiltersSource,
    private val loudnessDao: LoudnessDao,
    private val audioFeatureDao: AudioFeatureDao,
    private val loudnessAnalyzer: LoudnessAnalyzer,
    private val featureAnalyzer: AudioFeatureAnalyzer,
    private val traitsAnalyzer: TrackTraitsAnalyzer,
) : CoroutineWorker(appContext, params) {

    /** Entités `audio_features` connues, mises à jour au fil des écritures. */
    private val featureByPath = HashMap<String, AudioFeatureEntity>()

    override suspend fun doWork(): Result {
        val priority = inputData.getStringArray(KEY_PRIORITY)?.toSet().orEmpty()
        val library = LibraryBuilder.build(songDao.all().map { it.toSong() }, filters.current())
        val songs = library.songs + library.podcasts + library.audiobooks

        val loudness = loudnessDao.all().associate { it.songPath to it.fileModifiedSec }
        val features = audioFeatureDao.all()
        featureByPath.clear()
        features.associateByTo(featureByPath) { it.songPath }
        val plan = AnalysisPlanner.plan(
            songs = songs,
            loudnessModified = loudness,
            featureModified = features.associate { it.songPath to it.fileModifiedSec },
            featureDimensions = features.associate { it.songPath to it.vector.split(',').size },
            expectedDimensions = AudioFeatureAnalyzer.DIMENSIONS,
            priorityPaths = priority,
            traitsVersion = features.associate { it.songPath to it.analysisVersion },
        )
        if (plan.isEmpty()) return Result.success()
        Log.i(TAG, "Analyse : ${plan.size} titre(s) à traiter")

        val total = plan.size
        val foreground = total > FOREGROUND_THRESHOLD
        if (foreground) runCatching { setForeground(foregroundInfo(0, total)) }
            .onFailure { Log.w(TAG, "Notification de progression refusée", it) }

        plan.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            setProgress(workDataOf(KEY_DONE to index, KEY_TOTAL to total, KEY_TITLE to item.song.title))
            if (foreground && index % 10 == 0) runCatching { setForeground(foregroundInfo(index, total)) }
            try {
                analyse(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Analyse impossible : ${item.song.title}", e)
            }
        }

        setProgress(workDataOf(KEY_DONE to total, KEY_TOTAL to total))
        Log.i(TAG, "Analyse terminée : $total titre(s)")
        return Result.success(workDataOf(KEY_DONE to total, KEY_TOTAL to total))
    }

    private suspend fun analyse(item: AnalysisItem) {
        val song = item.song
        val now = System.currentTimeMillis()
        if (item.needsLoudness) {
            // Un fichier illisible est enregistré avec une valeur sentinelle plutôt
            // qu'ignoré : sinon il repasserait dans « à analyser » à chaque fois.
            val dbfs = ReplayGain.dbfs(song.path)
                ?: loudnessAnalyzer.analyse(song.path)
                ?: ReplayGain.UNMEASURABLE_DBFS
            loudnessDao.put(LoudnessEntity(song.path, dbfs, song.dateModifiedSec, now))
        }
        if (item.needsFeatures) {
            featureAnalyzer.analyse(song.path)?.let { features ->
                val entity = AudioFeatureEntity(
                    songPath = song.path,
                    vector = features.values.joinToString(",") { it.toString() },
                    fileModifiedSec = song.dateModifiedSec,
                    analysedAt = now,
                )
                audioFeatureDao.put(entity)
                featureByPath[song.path] = entity
            }
        }
        if (item.needsTraits) {
            // Les traits se greffent sur l'entité du vecteur : sans vecteur (fichier illisible), rien à écrire.
            val base = featureByPath[song.path] ?: return
            val traits = traitsAnalyzer.analyse(song.path, song.durationMs) ?: return
            val entity = base.copy(
                bpm = traits.bpm,
                bpmConfidence = traits.bpmConfidence,
                keyIndex = traits.keyIndex,
                keyConfidence = traits.keyConfidence,
                energy = traits.energy,
                introMs = traits.introMs,
                outroMs = traits.outroMs,
                analysisVersion = TrackTraits.VERSION,
            )
            audioFeatureDao.put(entity)
            featureByPath[song.path] = entity
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Analyses", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Niveau sonore et empreintes de la bibliothèque"
                }
            )
        }
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Analyse de la bibliothèque")
            .setContentText(if (total > 0) "$done / $total titres" else "Préparation…")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Arrêter", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_PRIORITY = "priority"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_TITLE = "title"
        private const val TAG = "TrackAnalysis"
        private const val CHANNEL_ID = "analyses"
        private const val NOTIFICATION_ID = 7_201
        /** En dessous, pas de notification : l'analyse finit avant qu'on la remarque. */
        private const val FOREGROUND_THRESHOLD = 50
    }
}
