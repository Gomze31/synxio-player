package fr.synxio.player.data.repo

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Numéro du jour local, réémis juste après minuit : ce qui dépend de la date (souvenirs,
 * insights) se recalcule avec elle sans attendre une écoute.
 */
internal fun localDays(): Flow<Long> = flow {
    while (true) {
        emit(LocalDate.now().toEpochDay())
        val untilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay())
        delay(untilMidnight.toMillis() + 1_000)
    }
}
