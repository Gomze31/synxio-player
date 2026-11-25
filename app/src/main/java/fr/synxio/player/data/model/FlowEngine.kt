package fr.synxio.player.data.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ce que le Flow sait d'un titre. [bpm] ≤ 0 ou [keyIndex] hors 0..23 = titre sans données
 * (non analysé, ou analyse peu sûre) : il peut s'insérer, mais après ceux qui s'enchaînent.
 */
data class FlowTrack(
    val id: Long,
    val artist: String,
    val bpm: Float,
    val keyIndex: Int,
    val energy: Float,
) {
    val hasData: Boolean get() = bpm > 0f && keyIndex in 0..23
}

/** Forme d'énergie de l'enchaînement. */
enum class FlowArc(val label: String) {
    /** Aucune contrainte : seule la douceur des transitions compte. */
    STEADY("Stable"),
    /** Énergie croissante : les baisses sont pénalisées. */
    RISE("Montée"),
    /** Monte puis descend, point de bascule aux deux tiers. */
    WAVE("Vague"),
}

/** Les trois faits affichés sous « Pourquoi ce titre ». Un fait absent n'est pas connu. */
data class FlowExplanation(val bpm: String?, val key: String?, val energy: String?) {
    val facts: List<String> get() = listOfNotNull(bpm, key, energy)
    val summary: String get() = facts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "titre non analysé"
}

/** Un titre et ses traits (null = non analysé) vus par le Flow ; [id] est choisi par l'appelant. */
fun Song.toFlowTrack(id: Long, traits: TrackTraits?): FlowTrack = FlowTrack(
    id = id,
    artist = displayArtist,
    bpm = traits?.takeIf { it.hasBpm }?.bpm ?: 0f,
    keyIndex = traits?.keyIndex ?: -1,
    energy = traits?.energy ?: 0f,
)

/**
 * Enchaînement par tempo et tonalité compatibles. Pur : aucune dépendance Android.
 *
 * Coût d'une transition : `0,5 × Δbpm_rel + 0,35 × camelot / 3 + 0,15 × |Δénergie|`, où
 * `Δbpm_rel` tolère l'octave (85 et 170 BPM s'enchaînent) et `camelot` est la distance sur
 * la roue (`KeyNames.distance`). Même artiste que l'un des deux précédents : + 0,25. Titre
 * sans données : 0,6 vers n'importe quel autre.
 *
 * Ordre : plus proche voisin glouton depuis le titre de départ, puis une passe 2-opt bornée
 * à [MAX_SWAPS] échanges acceptés. Les arcs ajoutent une pénalité sur les variations
 * d'énergie à contre-sens, dépendante de la position dans la liste.
 */
object FlowEngine {

    const val MAX_SWAPS = 2_000
    /** Au-delà, la passe 2-opt (O(n³) au pire) est sautée : le glouton suffit. */
    const val MAX_TRACKS_FOR_2OPT = 400

    private const val W_BPM = 0.5f
    private const val W_KEY = 0.35f
    private const val W_ENERGY = 0.15f
    private const val NO_DATA_COST = 0.6f
    private const val SAME_ARTIST_PENALTY = 0.25f
    private const val ARC_PENALTY = 0.3f
    private const val SAME_ENERGY_EPS = 0.05f

    /** Coût de `from → to`, pénalité « même artiste » comprise pour ce couple. */
    fun cost(from: FlowTrack, to: FlowTrack): Float {
        val base = if (!from.hasData || !to.hasData) NO_DATA_COST else {
            W_BPM * relativeBpmDelta(from.bpm, to.bpm) +
                W_KEY * KeyNames.distance(from.keyIndex, to.keyIndex) / 3f +
                W_ENERGY * abs(from.energy - to.energy)
        }
        return base + if (sameArtist(from, to)) SAME_ARTIST_PENALTY else 0f
    }

    /** `min(|a − b|, |a − 2b|, |2a − b|) / max(a, b)` : tolérance d'octave. */
    fun relativeBpmDelta(a: Float, b: Float): Float {
        val m = max(a, b)
        if (m <= 0f) return 0f
        return minOf(abs(a - b), abs(a - 2 * b), abs(2 * a - b)) / m
    }

    /**
     * Ordre des identifiants : [startId] en tête s'il est dans la liste (sinon le premier
     * titre). Les doublons d'identifiant ne sont pas pris en charge : l'appelant identifie
     * ses titres par leur position s'il le faut.
     */
    fun order(
        tracks: List<FlowTrack>,
        startId: Long?,
        arc: FlowArc = FlowArc.STEADY,
        /** false = glouton seul (tests). */
        optimize: Boolean = true,
    ): List<Long> {
        if (tracks.size <= 2) {
            val start = tracks.firstOrNull { it.id == startId } ?: return tracks.map { it.id }
            return listOf(start.id) + tracks.filter { it.id != start.id }.map { it.id }
        }
        val path = greedy(tracks, startId, arc)
        if (optimize && tracks.size <= MAX_TRACKS_FOR_2OPT) twoOpt(path, arc)
        return path.map { it.id }
    }

    /** Coût total d'un chemin, pénalités d'artiste (deux précédents) et d'arc comprises. */
    fun totalCost(path: List<FlowTrack>, arc: FlowArc = FlowArc.STEADY): Float {
        var sum = 0f
        for (i in 1 until path.size) sum += edgeCost(path, i, arc)
        return sum
    }

    fun explain(from: FlowTrack, to: FlowTrack): FlowExplanation {
        if (!from.hasData || !to.hasData) return FlowExplanation(null, null, null)
        val bpm = "${from.bpm.roundToInt()} → ${to.bpm.roundToInt()} BPM"
        val fromKey = KeyNames.camelot(from.keyIndex)
        val toKey = KeyNames.camelot(to.keyIndex)
        val key = when (KeyNames.distance(from.keyIndex, to.keyIndex)) {
            0 -> "$fromKey, même tonalité"
            1 -> "$fromKey → $toKey, " + if (fromKey.dropLast(1) == toKey.dropLast(1)) "relatives" else "voisines"
            2 -> "$fromKey → $toKey, proches"
            else -> "$fromKey → $toKey, éloignées"
        }
        val delta = to.energy - from.energy
        val energy = if (abs(delta) < SAME_ENERGY_EPS) "même énergie" else {
            val sign = if (delta > 0) "+" else "−"
            "énergie $sign" + String.format(Locale.FRANCE, "%.1f", abs(delta))
        }
        return FlowExplanation(bpm, key, energy)
    }

    // --- Interne ---------------------------------------------------------------------------

    private fun sameArtist(a: FlowTrack, b: FlowTrack): Boolean =
        a.artist.isNotBlank() && a.artist.equals(b.artist, ignoreCase = true)

    /** Coût de l'arête qui arrive en `path[i]`, dans le contexte du chemin. */
    private fun edgeCost(path: List<FlowTrack>, i: Int, arc: FlowArc): Float {
        val to = path[i]
        val from = path[i - 1]
        var c = cost(from, to)
        if (i >= 2 && sameArtist(path[i - 2], to)) c += SAME_ARTIST_PENALTY
        c += arcPenalty(from, to, i, path.size, arc)
        return c
    }

    private fun arcPenalty(from: FlowTrack, to: FlowTrack, position: Int, size: Int, arc: FlowArc): Float {
        if (!from.hasData || !to.hasData) return 0f
        val direction = when (arc) {
            FlowArc.STEADY -> return 0f
            FlowArc.RISE -> 1f
            FlowArc.WAVE -> if (position * 3 < size * 2) 1f else -1f
        }
        val against = -direction * (to.energy - from.energy)
        return if (against > 0f) ARC_PENALTY * against else 0f
    }

    private fun greedy(tracks: List<FlowTrack>, startId: Long?, arc: FlowArc): MutableList<FlowTrack> {
        val remaining = tracks.toMutableList()
        val start = remaining.firstOrNull { it.id == startId } ?: remaining.first()
        remaining.remove(start)
        val path = ArrayList<FlowTrack>(tracks.size)
        path += start
        val size = tracks.size
        while (remaining.isNotEmpty()) {
            val prev = path.last()
            val prev2 = path.getOrNull(path.size - 2)
            val position = path.size
            var bestIndex = 0
            var bestCost = Float.MAX_VALUE
            for (i in remaining.indices) {
                val candidate = remaining[i]
                var c = cost(prev, candidate)
                if (prev2 != null && sameArtist(prev2, candidate)) c += SAME_ARTIST_PENALTY
                c += arcPenalty(prev, candidate, position, size, arc)
                if (c < bestCost) { bestCost = c; bestIndex = i }
            }
            path += remaining.removeAt(bestIndex)
        }
        return path
    }

    /**
     * 2-opt : inverse le segment `[i, j]` quand le chemin y gagne. Le titre de départ
     * (index 0) ne bouge pas. Les pénalités dépendent du contexte (deux précédents, arc),
     * on recalcule donc toutes les arêtes que l'inversion peut toucher : celles qui arrivent
     * en `i .. j + 2`.
     */
    private fun twoOpt(path: MutableList<FlowTrack>, arc: FlowArc) {
        val n = path.size
        var swaps = 0
        var improved = true
        while (improved && swaps < MAX_SWAPS) {
            improved = false
            for (i in 1 until n - 1) {
                for (j in i + 1 until n) {
                    val hi = minOf(n - 1, j + 2)
                    val before = segmentCost(path, i, hi, arc)
                    path.subList(i, j + 1).reverse()
                    val after = segmentCost(path, i, hi, arc)
                    if (after < before - 1e-6f) {
                        improved = true
                        swaps++
                        if (swaps >= MAX_SWAPS) return
                    } else {
                        path.subList(i, j + 1).reverse()
                    }
                }
            }
        }
    }

    /** Somme des arêtes arrivant en `from .. to`. */
    private fun segmentCost(path: List<FlowTrack>, from: Int, to: Int, arc: FlowArc): Float {
        var sum = 0f
        for (k in from.coerceAtLeast(1)..to) sum += edgeCost(path, k, arc)
        return sum
    }
}
