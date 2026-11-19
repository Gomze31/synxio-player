package fr.synxio.player.wear.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Photo de la lecture telle que la montre la reçoit.
 *
 * La montre ne reçoit pas de tick par seconde : elle extrapole la position depuis
 * [positionMs] et [capturedAtMs] tant que [isPlaying]. Le téléphone republie à chaque
 * transition (piste, lecture/pause, favori, volume), jamais en continu.
 */
@Serializable
data class WearState(
    val protocolVersion: Int = WearPaths.PROTOCOL_VERSION,
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val favorite: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Horloge murale (`System.currentTimeMillis`) de la photo. */
    val capturedAtMs: Long = 0L,
    val live: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    /** Volume du flux musique, de 0 à 1. */
    val volume: Float = 0f,
    /** Accent ARGB lu sur fond sombre ; 0 quand la pochette n'en donne pas. */
    val accent: Int = 0,
    val accentLight: Int = 0,
) {
    val isEmpty: Boolean get() = mediaId.isEmpty()

    /** Position estimée à [nowMs], bornée à la durée. */
    fun progressAt(nowMs: Long): Long {
        val elapsed = if (isPlaying && capturedAtMs > 0L) (nowMs - capturedAtMs).coerceAtLeast(0L) else 0L
        val position = positionMs + elapsed
        return if (durationMs > 0L) position.coerceIn(0L, durationMs) else position.coerceAtLeast(0L)
    }

    /** Fraction 0..1 pour un anneau de progression ; 0 pour un direct ou sans durée. */
    fun progressFractionAt(nowMs: Long): Float {
        if (durationMs <= 0L || live) return 0f
        return (progressAt(nowMs).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /**
     * Vrai si l'état dit « en lecture » depuis plus de [STALE_MS] sans republication :
     * le téléphone a sans doute redémarré. La montre l'affiche alors en pause.
     */
    fun isStaleAt(nowMs: Long): Boolean = isPlaying && nowMs - capturedAtMs > STALE_MS

    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val STALE_MS = 6 * 60 * 60 * 1000L

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** `null` si le JSON est illisible : la montre garde son état précédent. */
        fun fromJson(text: String): WearState? =
            runCatching { json.decodeFromString<WearState>(text) }.getOrNull()
    }
}
