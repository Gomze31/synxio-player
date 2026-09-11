package fr.synxio.player.data.repo

import android.util.Log
import fr.synxio.player.data.db.AudioFeatureDao
import fr.synxio.player.data.db.AudioFeatureEntity
import fr.synxio.player.data.media.AudioFeatureAnalyzer
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class AnalysisProgress(
    val done: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
) {
    val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/**
 * Recherche de morceaux au son proche.
 *
 * Chaque morceau est décrit par un vecteur produit par [AudioFeatureAnalyzer], puis
 * comparé aux autres par distance euclidienne.
 *
 * ## La normalisation, sans laquelle rien ne fonctionne
 *
 * Les dimensions brutes n'ont ni la même unité ni la même amplitude : une énergie de
 * bande varie sur plusieurs unités quand une planéité spectrale reste confinée entre 0
 * et 1. Sans mise à l'échelle, les bandes écraseraient purement et simplement tous les
 * autres descripteurs et la similarité se réduirait à « même volume par bande ».
 *
 * Chaque dimension est donc centrée-réduite **sur l'ensemble de la bibliothèque** :
 * chacune contribue alors à proportion de sa capacité à distinguer les morceaux entre
 * eux, et non de son unité de mesure.
 */
@Singleton
class SimilarityRepository @Inject constructor(
    private val dao: AudioFeatureDao,
    private val analyzer: AudioFeatureAnalyzer,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _progress = MutableStateFlow(AnalysisProgress())
    val progress: StateFlow<AnalysisProgress> = _progress.asStateFlow()

    private var job: Job? = null

    /** Empreintes brutes par chemin de fichier. */
    private val vectors: StateFlow<Map<String, FloatArray>> = dao.observeAll()
        .map { list -> list.associate { it.songPath to it.vector.toVector() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val analysedCount: StateFlow<Int> = vectors.map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    fun hasFingerprint(song: Song): Boolean = song.path in vectors.value

    /**
     * Empreinte brute d'un morceau, si elle a été calculée.
     *
     * Exposée pour le widget, qui dessine une onde à partir des énergies de bande : le
     * tracé reflète alors le spectre réel du morceau et non une décoration générique.
     */
    fun featuresFor(song: Song): FloatArray? = vectors.value[song.path]

    fun pendingCount(songs: List<Song>): Int {
        val known = vectors.value
        return songs.count { it.path !in known }
    }

    /**
     * Analyse les morceaux dépourvus d'empreinte à jour.
     *
     * Séquentiel : chaque analyse instancie un décodeur matériel, et les paralléliser
     * provoque des échecs d'allocation plutôt qu'un gain de temps.
     */
    fun analyseLibrary(songs: List<Song>) {
        if (job?.isActive == true) return
        job = scope.launch {
            val known = withContext(Dispatchers.IO) { dao.all().associateBy { it.songPath } }
            val todo = songs.filter { song ->
                val entry = known[song.path]
                entry == null ||
                    entry.fileModifiedSec != song.dateModifiedSec ||
                    entry.vector.toVector().size != AudioFeatureAnalyzer.DIMENSIONS
            }

            _progress.value = AnalysisProgress(0, todo.size, running = true)
            var failures = 0

            todo.forEachIndexed { index, song ->
                val features = analyzer.analyse(song.path)
                if (features != null) {
                    dao.put(
                        AudioFeatureEntity(
                            songPath = song.path,
                            vector = features.values.joinToString(",") { it.toString() },
                            fileModifiedSec = song.dateModifiedSec,
                            analysedAt = System.currentTimeMillis(),
                        )
                    )
                } else {
                    failures++
                }
                _progress.value = AnalysisProgress(index + 1, todo.size, running = true)
            }

            Log.i(TAG, "Empreintes calculées : ${todo.size - failures}/${todo.size}")
            _progress.value = AnalysisProgress(todo.size, todo.size, running = false)
        }
    }

    fun cancel() {
        job?.cancel()
        _progress.value = AnalysisProgress(running = false)
    }

    suspend fun clear() {
        cancel()
        dao.clear()
    }

    /**
     * Morceaux les plus proches de [seed], du plus ressemblant au moins ressemblant.
     *
     * [maxPerArtist] borne la présence d'un même artiste. Sans cette limite, les plus
     * proches voisins d'un titre sont presque toujours les autres pistes du même album —
     * enregistrées le même jour, au même mastering — et la radio ne quitte jamais le
     * disque dont elle est partie.
     */
    suspend fun similarTo(
        seed: Song,
        candidates: List<Song>,
        limit: Int = 30,
        maxPerArtist: Int = 2,
    ): List<Song> = withContext(Dispatchers.Default) {
        val raw = vectors.value
        val seedVector = raw[seed.path] ?: return@withContext emptyList()

        val pool = candidates.filter { it.path != seed.path && it.path in raw }
        if (pool.isEmpty()) return@withContext emptyList()

        val stats = standardisation(raw.values)
        val seedScaled = seedVector.standardise(stats)

        val ranked = pool
            .map { song -> song to distance(seedScaled, raw.getValue(song.path).standardise(stats)) }
            .sortedBy { it.second }

        val perArtist = HashMap<String, Int>()
        val result = ArrayList<Song>(limit)
        for ((song, _) in ranked) {
            val key = song.displayArtist.lowercase()
            val count = perArtist.getOrDefault(key, 0)
            if (count >= maxPerArtist) continue
            perArtist[key] = count + 1
            result += song
            if (result.size == limit) break
        }
        result
    }

    // --- Normalisation ---------------------------------------------------------------

    private data class Standardisation(val means: FloatArray, val deviations: FloatArray)

    private fun standardisation(all: Collection<FloatArray>): Standardisation {
        val dimensions = AudioFeatureAnalyzer.DIMENSIONS
        val means = FloatArray(dimensions)
        val deviations = FloatArray(dimensions)
        val usable = all.filter { it.size == dimensions }
        if (usable.isEmpty()) return Standardisation(means, FloatArray(dimensions) { 1f })

        for (d in 0 until dimensions) {
            var sum = 0.0
            usable.forEach { sum += it[d] }
            means[d] = (sum / usable.size).toFloat()
        }
        for (d in 0 until dimensions) {
            var sum = 0.0
            usable.forEach { sum += (it[d] - means[d]).toDouble() * (it[d] - means[d]) }
            // Écart-type nul : dimension constante sur toute la bibliothèque, sans
            // pouvoir discriminant. On la neutralise au lieu de diviser par zéro.
            deviations[d] = sqrt(sum / usable.size).toFloat().takeIf { it > 1e-6f } ?: 1f
        }
        return Standardisation(means, deviations)
    }

    private fun FloatArray.standardise(stats: Standardisation): FloatArray =
        FloatArray(size) { (this[it] - stats.means[it]) / stats.deviations[it] }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0.0
        val n = minOf(a.size, b.size)
        for (i in 0 until n) sum += (a[i] - b[i]).toDouble() * (a[i] - b[i])
        return sqrt(sum).toFloat()
    }

    private fun String.toVector(): FloatArray =
        split(',').mapNotNull { it.toFloatOrNull() }.toFloatArray()

    private companion object {
        const val TAG = "Similarity"
    }
}
