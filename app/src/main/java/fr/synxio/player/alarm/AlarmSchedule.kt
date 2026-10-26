package fr.synxio.player.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendrier des réveils : jours codés en masque de bits (lundi = bit 0 … dimanche =
 * bit 6), 0 pour un réveil qui ne sonne qu'une fois.
 */
object AlarmSchedule {

    const val EVERY_DAY = 0b111_1111
    const val WEEKDAYS = 0b001_1111
    private const val WEEKEND = 0b110_0000

    fun mask(vararg days: DayOfWeek): Int = days.fold(0) { acc, day -> acc or bit(day) }

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun has(days: Int, day: DayOfWeek): Boolean = days and bit(day) != 0

    /**
     * Prochain déclenchement strictement après [now].
     *
     * L'heure exacte de [now] compte comme passée : reprogrammer un réveil au moment où il
     * sonne doit viser la fois suivante, pas le refaire sonner aussitôt.
     */
    fun nextTrigger(hour: Int, minute: Int, days: Int, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(hour, minute)
        if (days == 0) return if (today.isAfter(now)) today else today.plusDays(1)
        for (offset in 0L..7L) {
            val candidate = today.plusDays(offset)
            if (candidate.isAfter(now) && has(days, candidate.dayOfWeek)) return candidate
        }
        error("Masque de jours invalide : $days")
    }

    fun daysLabel(days: Int): String = when (days) {
        0 -> "Une fois"
        EVERY_DAY -> "Tous les jours"
        WEEKDAYS -> "En semaine"
        WEEKEND -> "Le week-end"
        else -> DayOfWeek.entries
            .filter { has(days, it) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.FRENCH) }
    }
}
