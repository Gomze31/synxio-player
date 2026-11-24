package fr.synxio.player.data.repo

import fr.synxio.player.data.analysis.TrackAnalysisScheduler
import fr.synxio.player.data.db.LoudnessDao
import fr.synxio.player.data.model.Song
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

data class LoudnessProgress(
    val done: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
) {
    val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/**
 * Normalisation du volume entre morceaux.
 *
 * Une bibliothèque constituée de rips mélange des fichiers masterisés à des niveaux très
 * différents : passer d'un titre à l'autre oblige à reprendre le volume à chaque fois.
 * On mesure donc le niveau de chaque fichier une bonne fois, et on applique à la lecture
 * l'écart avec un niveau cible commun.
 *
 * Deux sources de mesure, par ordre de préférence :
 *  1. le tag **ReplayGain** s'il est présent — gratuit, standard, calculé par l'outil qui
 *     a encodé le fichier ([ReplayGain]) ;
 *  2. sinon une mesure locale par décodage (`LoudnessAnalyzer`).
 *
 * La mesure elle-même tourne dans `TrackAnalysisWorker` (WorkManager) : ce repository
 * lit les résultats et expose la progression du Worker.
 */
@Singleton
class LoudnessRepository @Inject constructor(
    private val dao: LoudnessDao,
    private val scheduler: TrackAnalysisScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {

    val progress: StateFlow<LoudnessProgress> = scheduler.progress
        .map { run -> LoudnessProgress(run?.done ?: 0, run?.total ?: 0, run?.running == true) }
        .stateIn(scope, SharingStarted.Eagerly, LoudnessProgress())

    /** Niveau mesuré par chemin de fichier, en dBFS. */
    val levels: StateFlow<Map<String, Float>> = dao.observeAll()
        .map { list -> list.associate { it.songPath to it.dbfs } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Facteur de volume à appliquer à [song], entre 0 et 1.
     *
     * On ne dépasse jamais 1 : ExoPlayer écrête au-delà, ce qui distordrait au lieu
     * d'amplifier. Un morceau plus faible que la cible reste donc plus faible — mais
     * tous les morceaux plus forts sont ramenés à son niveau, ce qui supprime les
     * écarts dans le sens qui gêne vraiment à l'écoute.
     */
    fun gainFor(song: Song, targetDbfs: Float): Float {
        val measured = levels.value[song.path] ?: return 1f
        if (measured == ReplayGain.UNMEASURABLE_DBFS) return 1f
        val deltaDb = targetDbfs - measured
        if (deltaDb >= 0f) return 1f
        return 10.0.pow(deltaDb / 20.0).toFloat().coerceIn(MIN_GAIN, 1f)
    }

    fun hasMeasure(song: Song): Boolean = song.path in levels.value

    /** Nombre de morceaux encore non mesurés. */
    fun pendingCount(songs: List<Song>): Int {
        val known = levels.value
        return songs.count { it.path !in known }
    }

    /** Lance (ou laisse courir) l'analyse de fond ; le Worker décide lui-même de ce qui manque. */
    @Suppress("UNUSED_PARAMETER")
    fun analyseLibrary(songs: List<Song>) = scheduler.enqueue()

    fun cancel() = scheduler.cancel()

    suspend fun clear() {
        cancel()
        dao.clear()
    }

    private companion object {
        /** En dessous, on n'atténue plus : le morceau deviendrait inaudible. */
        const val MIN_GAIN = 0.15f
    }
}
