package fr.synxio.player.playback.pulse

/**
 * Tampon circulaire mono. Un seul écrivain (le thread audio) et un seul lecteur
 * (l'analyseur) : pas de verrou, l'index d'écriture est volatile. Une lecture qui
 * chevauche une écriture mélange deux instants ; pour une animation, c'est sans effet.
 */
class PulseRingBuffer(private val capacity: Int = 2048) {
    private val data = FloatArray(capacity)

    @Volatile
    var written: Long = 0L
        private set

    /** Copie [count] échantillons de [samples] ; sans allocation. */
    fun write(samples: FloatArray, count: Int) {
        var index = (written % capacity).toInt()
        for (i in 0 until count) {
            data[index] = samples[i]
            index++
            if (index == capacity) index = 0
        }
        written += count
    }

    /** Remplit [out] avec les `out.size` derniers échantillons, zéros devant si trop tôt. */
    fun readLast(out: FloatArray) {
        val total = written
        val n = out.size
        val available = minOf(total, n.toLong()).toInt()
        val lead = n - available
        for (i in 0 until lead) out[i] = 0f
        var index = ((total - available) % capacity).toInt()
        for (i in 0 until available) {
            out[lead + i] = data[index]
            index++
            if (index == capacity) index = 0
        }
    }
}
