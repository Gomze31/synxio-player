package fr.synxio.player.data.media

import java.nio.ShortBuffer

/**
 * Tampon d'échantillons mono PCM 16 bits à croissance géométrique.
 *
 * Remplace l'`ArrayList<Short>` du décodeur, qui enveloppait chaque échantillon dans un
 * objet : une fenêtre de 12 s à 44,1 kHz en faisait 530 000. Pur, sans Android.
 */
class PcmBuffer(initialCapacity: Int = 1 shl 16) {

    private var data = ShortArray(initialCapacity.coerceAtLeast(16))

    var size: Int = 0
        private set

    /** Ajoute [shorts] ; plusieurs canaux sont repliés en mono par moyenne, trame par trame. */
    fun append(shorts: ShortBuffer, channels: Int) {
        if (channels <= 1) {
            val n = shorts.remaining()
            ensureCapacity(size + n)
            shorts.get(data, size, n)
            size += n
            return
        }
        val frames = shorts.remaining() / channels
        ensureCapacity(size + frames)
        repeat(frames) {
            var sum = 0
            for (c in 0 until channels) sum += shorts.get()
            data[size++] = (sum / channels).toShort()
        }
    }

    fun toArray(): ShortArray = data.copyOf(size)

    private fun ensureCapacity(needed: Int) {
        if (needed <= data.size) return
        var capacity = data.size
        while (capacity < needed) capacity = capacity shl 1
        data = data.copyOf(capacity)
    }
}
