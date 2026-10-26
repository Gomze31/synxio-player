package fr.synxio.player.data.repo

import fr.synxio.player.data.db.SongRatingDao
import fr.synxio.player.data.db.SongRatingEntity
import fr.synxio.player.data.model.Song
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notes de 1 à 5 étoiles. Gardées dans la base de l'appli et non dans les fichiers :
 * chaque format range la note ailleurs (POPM, RATING, rtng) et l'écriture demanderait
 * une autorisation par fichier.
 */
@Singleton
class RatingRepository @Inject constructor(
    private val dao: SongRatingDao,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Note par identifiant de titre ; un titre absent n'est pas noté. */
    val ratings: StateFlow<Map<Long, Int>> = dao.observeAll()
        .map { list -> list.associate { it.songId to it.stars } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** [stars] entre 1 et 5 ; 0 efface la note. */
    suspend fun setRating(song: Song, stars: Int) {
        val clamped = stars.coerceIn(0, MAX_STARS)
        if (clamped == 0) dao.remove(song.id)
        else dao.put(SongRatingEntity(song.id, song.path, clamped, System.currentTimeMillis()))
    }

    companion object {
        const val MAX_STARS = 5
    }
}
