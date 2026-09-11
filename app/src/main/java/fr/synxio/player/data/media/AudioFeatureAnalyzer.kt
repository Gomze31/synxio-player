package fr.synxio.player.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Empreinte sonore d'un morceau.
 *
 * Chaque dimension décrit un aspect du son. Elles sont comparées entre elles, jamais
 * lues isolément : une valeur n'a de sens que rapportée au reste de la bibliothèque.
 */
@JvmInline
value class AudioFeatures(val values: FloatArray) {
    val size: Int get() = values.size
}

/**
 * Décrit le son d'un morceau par un vecteur de caractéristiques.
 *
 * ## Ce que c'est, et ce que ce n'est pas
 *
 * Pas de réseau de neurones ni de modèle entraîné : de l'analyse spectrale classique.
 * On mesure le timbre, l'équilibre des fréquences et l'agitation rythmique, puis on
 * compare ces mesures d'un morceau à l'autre. C'est la mécanique qui sous-tend la
 * plupart des systèmes de « morceaux similaires », et elle a l'avantage de tourner hors
 * ligne, sans modèle à embarquer et sans rien envoyer nulle part.
 *
 * Sa limite mérite d'être dite : elle reconnaît une *sonorité*, pas un *genre*. Deux
 * morceaux au timbre et à l'énergie proches seront rapprochés même si tout les oppose
 * par ailleurs.
 *
 * ## Les dimensions
 *
 * Pour chaque trame, on calcule l'énergie de [BAND_COUNT] bandes réparties de façon
 * logarithmique — l'oreille perçoit les hauteurs ainsi, pas linéairement — plus le
 * centroïde spectral (brillance), la planéité (bruité contre harmonique) et le taux de
 * passages par zéro (percussif contre tenu). Chacune est agrégée en **moyenne et
 * écart-type** sur toutes les trames : un morceau uniforme et un morceau contrasté
 * peuvent partager la même moyenne tout en ne se ressemblant pas du tout.
 */
@Singleton
class AudioFeatureAnalyzer @Inject constructor(
    private val decoder: PcmDecoder,
) {

    suspend fun analyse(path: String): AudioFeatures? = withContext(Dispatchers.Default) {
        val windows = decoder.decodeWindows(path, WINDOW_COUNT, WINDOW_MS)
        if (windows.isEmpty()) return@withContext null

        val bands = Array(BAND_COUNT) { ArrayList<Float>() }
        val centroids = ArrayList<Float>()
        val flatnesses = ArrayList<Float>()
        val crossings = ArrayList<Float>()
        val fluxes = ArrayList<Float>()

        windows.forEach { window ->
            val edges = bandEdges(window.sampleRate)
            var previous: FloatArray? = null

            frames(window.samples).forEach { frame ->
                val spectrum = magnitudeSpectrum(frame)

                edges.forEachIndexed { index, range ->
                    var sum = 0f
                    for (bin in range) sum += spectrum[bin]
                    // Échelle logarithmique : l'énergie brute s'étale sur plusieurs ordres
                    // de grandeur et une seule bande écraserait toutes les autres.
                    bands[index].add(ln(1f + sum))
                }

                centroids.add(centroid(spectrum))
                flatnesses.add(flatness(spectrum))
                crossings.add(zeroCrossingRate(frame))
                previous?.let { fluxes.add(flux(it, spectrum)) }
                previous = spectrum
            }
        }

        if (centroids.isEmpty()) return@withContext null

        val values = ArrayList<Float>(BAND_COUNT * 2 + 7)
        bands.forEach { band ->
            values.add(band.mean())
            values.add(band.deviation())
        }
        values.add(centroids.mean()); values.add(centroids.deviation())
        values.add(flatnesses.mean()); values.add(flatnesses.deviation())
        values.add(crossings.mean()); values.add(crossings.deviation())
        values.add(fluxes.mean())

        AudioFeatures(values.toFloatArray())
    }

    // --- Découpage -------------------------------------------------------------------

    /** Trames se recouvrant de moitié : sans recouvrement, un transitoire à cheval sur
     *  deux trames serait compté deux fois à moitié et perdrait sa signature. */
    private fun frames(samples: ShortArray): Sequence<FloatArray> = sequence {
        var offset = 0
        while (offset + FRAME_SIZE <= samples.size) {
            val frame = FloatArray(FRAME_SIZE)
            for (i in 0 until FRAME_SIZE) {
                // Fenêtre de Hann : couper net un signal périodique créerait de fausses
                // raies spectrales sur toute la bande.
                val hann = 0.5f * (1f - kotlin.math.cos(2.0 * Math.PI * i / (FRAME_SIZE - 1)).toFloat())
                frame[i] = samples[offset + i] / PCM16_FULL_SCALE * hann
            }
            yield(frame)
            offset += FRAME_SIZE / 2
        }
    }

    /** Bornes de bandes réparties logarithmiquement entre 40 Hz et la fréquence de Nyquist. */
    private fun bandEdges(sampleRate: Int): List<IntRange> {
        val nyquist = sampleRate / 2f
        val binWidth = nyquist / (FRAME_SIZE / 2)
        val low = ln(LOWEST_HZ)
        val high = ln(nyquist.coerceAtLeast(LOWEST_HZ * 2))

        return (0 until BAND_COUNT).map { index ->
            val from = exp(low + (high - low) * index / BAND_COUNT)
            val to = exp(low + (high - low) * (index + 1) / BAND_COUNT)
            val firstBin = (from / binWidth).toInt().coerceIn(0, FRAME_SIZE / 2 - 1)
            val lastBin = (to / binWidth).toInt().coerceIn(firstBin, FRAME_SIZE / 2 - 1)
            firstBin..lastBin
        }
    }

    // --- Descripteurs ----------------------------------------------------------------

    private fun centroid(spectrum: FloatArray): Float {
        var weighted = 0f
        var total = 0f
        spectrum.forEachIndexed { bin, magnitude ->
            weighted += bin * magnitude
            total += magnitude
        }
        // Normalisé sur le nombre de bins : rend la valeur comparable entre fichiers
        // échantillonnés différemment.
        return if (total > 0f) weighted / total / spectrum.size else 0f
    }

    /**
     * Planéité spectrale : rapport de la moyenne géométrique à la moyenne arithmétique.
     * Proche de 1 pour un bruit, proche de 0 pour un son harmonique.
     */
    private fun flatness(spectrum: FloatArray): Float {
        var logSum = 0.0
        var sum = 0.0
        spectrum.forEach { magnitude ->
            // Plancher indispensable : ln(0) diverge et un seul bin vide suffirait à
            // annihiler la moyenne géométrique.
            logSum += ln((magnitude + EPSILON).toDouble())
            sum += magnitude + EPSILON
        }
        val geometric = exp(logSum / spectrum.size)
        val arithmetic = sum / spectrum.size
        return if (arithmetic > 0) (geometric / arithmetic).toFloat() else 0f
    }

    private fun zeroCrossingRate(frame: FloatArray): Float {
        var crossings = 0
        for (i in 1 until frame.size) {
            if ((frame[i] >= 0f) != (frame[i - 1] >= 0f)) crossings++
        }
        return crossings.toFloat() / frame.size
    }

    /** Flux spectral : ampleur du changement entre deux trames, proxy de l'agitation. */
    private fun flux(previous: FloatArray, current: FloatArray): Float {
        var sum = 0f
        for (i in current.indices) sum += abs(current[i] - previous[i])
        return sum / current.size
    }

    // --- FFT -------------------------------------------------------------------------

    /**
     * Transformée de Fourier rapide, radix-2 en place, puis module.
     *
     * Écrite ici plutôt qu'importée : une dépendance DSP complète pour une seule FFT
     * alourdirait l'APK sans rien apporter d'autre.
     */
    private fun magnitudeSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val real = frame.copyOf()
        val imaginary = FloatArray(n)

        // Permutation binaire inversée : réordonne les échantillons pour que les
        // papillons suivants s'appliquent sur des voisins contigus.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imaginary[i] = imaginary[j].also { imaginary[j] = imaginary[i] }
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * Math.PI / length
            val stepReal = kotlin.math.cos(angle).toFloat()
            val stepImaginary = kotlin.math.sin(angle).toFloat()
            var start = 0
            while (start < n) {
                var wReal = 1f
                var wImaginary = 0f
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tReal = wReal * real[b] - wImaginary * imaginary[b]
                    val tImaginary = wReal * imaginary[b] + wImaginary * real[b]
                    real[b] = real[a] - tReal
                    imaginary[b] = imaginary[a] - tImaginary
                    real[a] += tReal
                    imaginary[a] += tImaginary
                    val nextReal = wReal * stepReal - wImaginary * stepImaginary
                    wImaginary = wReal * stepImaginary + wImaginary * stepReal
                    wReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }

        // Seule la moitié basse porte de l'information : au-delà de Nyquist le spectre
        // d'un signal réel est le miroir conjugué de la première moitié.
        return FloatArray(n / 2) { sqrt(real[it] * real[it] + imaginary[it] * imaginary[it]) }
    }

    private fun List<Float>.mean(): Float = if (isEmpty()) 0f else sum() / size

    private fun List<Float>.deviation(): Float {
        if (size < 2) return 0f
        val mean = mean()
        var sum = 0.0
        forEach { value -> sum += (value - mean).toDouble() * (value - mean) }
        return sqrt(sum / size).toFloat()
    }

    companion object {
        private const val BAND_COUNT = 10

        /**
         * Taille du vecteur produit, utile aux consommateurs pour périmer un cache.
         *
         * Déclarée après [BAND_COUNT] : Kotlin initialise les constantes d'un companion
         * dans l'ordre d'écriture, et l'inverse ne compile pas.
         */
        const val DIMENSIONS = BAND_COUNT * 2 + 7

        /** Puissance de deux, imposée par la FFT radix-2. */
        private const val FRAME_SIZE = 2048
        private const val WINDOW_COUNT = 6
        private const val WINDOW_MS = 2_000L
        private const val PCM16_FULL_SCALE = 32768f
        private const val LOWEST_HZ = 40f
        private const val EPSILON = 1e-10f
    }
}
