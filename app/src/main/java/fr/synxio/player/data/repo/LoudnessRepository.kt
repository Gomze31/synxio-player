package fr.synxio.player.data.repo

import android.util.Log
import fr.synxio.player.data.db.LoudnessDao
import fr.synxio.player.data.db.LoudnessEntity
import fr.synxio.player.data.media.LoudnessAnalyzer
import fr.synxio.player.data.model.Song
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
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
 *     a encodé le fichier ;
 *  2. sinon une mesure locale par décodage ([LoudnessAnalyzer]).
 */
@Singleton
class LoudnessRepository @Inject constructor(
    private val dao: LoudnessDao,
    private val analyzer: LoudnessAnalyzer,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _progress = MutableStateFlow(LoudnessProgress())
    val progress: StateFlow<LoudnessProgress> = _progress.asStateFlow()

    private var job: Job? = null

    /** Niveau mesuré par chemin de fichier, tenu à jour depuis la base. */
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
        if (measured == UNMEASURABLE_DBFS) return 1f
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

    /**
     * Mesure tous les morceaux qui n'ont pas encore de valeur à jour.
     *
     * Séquentiel et non parallèle : chaque mesure instancie un MediaCodec matériel, et
     * les décodeurs de l'appareil sont en nombre limité. Les paralléliser provoque des
     * échecs d'allocation plutôt qu'un gain de temps.
     */
    fun analyseLibrary(songs: List<Song>) {
        if (job?.isActive == true) return
        job = scope.launch {
            val known = withContext(Dispatchers.IO) { dao.all().associateBy { it.songPath } }
            val todo = songs.filter { song ->
                val entry = known[song.path]
                entry == null || entry.fileModifiedSec != song.dateModifiedSec
            }

            _progress.value = LoudnessProgress(0, todo.size, running = true)
            val measured = mutableListOf<Float>()

            todo.forEachIndexed { index, song ->
                // Un fichier illisible est enregistré avec une valeur sentinelle plutôt
                // qu'ignoré : sans ça il repasserait dans « à analyser » à chaque
                // ouverture des réglages, et le compteur ne tomberait jamais à zéro.
                val dbfs = replayGainDbfs(song)
                    ?: analyzer.analyse(song.path)
                    ?: UNMEASURABLE_DBFS

                if (dbfs != UNMEASURABLE_DBFS) measured += dbfs
                dao.put(
                    LoudnessEntity(
                        songPath = song.path,
                        dbfs = dbfs,
                        fileModifiedSec = song.dateModifiedSec,
                        analysedAt = System.currentTimeMillis(),
                    )
                )
                _progress.value = LoudnessProgress(index + 1, todo.size, running = true)
            }

            if (measured.isNotEmpty()) {
                val sorted = measured.sorted()
                Log.i(
                    TAG,
                    "Niveaux mesurés sur ${measured.size} titres : " +
                        "min %.1f dBFS, médiane %.1f, max %.1f (%d illisibles)".format(
                            sorted.first(),
                            sorted[sorted.size / 2],
                            sorted.last(),
                            todo.size - measured.size,
                        )
                )
            }
            _progress.value = LoudnessProgress(todo.size, todo.size, running = false)
        }
    }

    fun cancel() {
        job?.cancel()
        _progress.value = LoudnessProgress(running = false)
    }

    suspend fun clear() {
        cancel()
        dao.clear()
    }

    /**
     * Convertit un tag ReplayGain en niveau dBFS équivalent.
     *
     * ReplayGain stocke une *correction* relative à sa référence (-18 dBFS chez la
     * plupart des encodeurs, selon la spécification RG2 / EBU R128). Le niveau du
     * fichier est donc la référence moins la correction annoncée.
     */
    private suspend fun replayGainDbfs(song: Song): Float? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(song.path)
            if (!file.canRead()) return@runCatching null
            val tag = AudioFileIO.read(file).tag ?: return@runCatching null

            val raw = REPLAY_GAIN_KEYS.firstNotNullOfOrNull { key ->
                tag.getFirst(key)?.takeIf { it.isNotBlank() }
            } ?: return@runCatching null

            // Format typique : « -7.06 dB ».
            val gain = raw.replace("dB", "", ignoreCase = true).trim().toFloatOrNull()
                ?: return@runCatching null

            (REPLAY_GAIN_REFERENCE_DBFS - gain).coerceIn(-70f, 0f)
        }.getOrElse {
            Log.d(TAG, "Pas de tag ReplayGain lisible pour ${song.title}")
            null
        }
    }

    private companion object {
        const val TAG = "LoudnessRepository"
        const val REPLAY_GAIN_REFERENCE_DBFS = -18f
        /**
         * Valeur sentinelle pour « fichier illisible ».
         *
         * Positive, donc impossible pour un vrai signal numérique, qui ne peut pas
         * dépasser 0 dBFS. Aucune mesure légitime ne peut entrer en collision.
         */
        const val UNMEASURABLE_DBFS = 1f
        /** En dessous, on n'atténue plus : le morceau deviendrait inaudible. */
        const val MIN_GAIN = 0.15f
        val REPLAY_GAIN_KEYS = listOf("REPLAYGAIN_TRACK_GAIN", "replaygain_track_gain")
    }
}
