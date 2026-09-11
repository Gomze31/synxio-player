package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.model.Song
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Identifiants stables : ils servent de clé de navigation et de clé de liste Compose. */
enum class SmartPlaylistId {
    NEVER_PLAYED,
    FORGOTTEN_FAVORITES,
    ON_REPEAT,
    HIDDEN_GEMS,
    MOST_SKIPPED,
    FRESH,
}

/**
 * Une playlist calculée à la volée depuis la bibliothèque et les statistiques.
 *
 * Rien n'est stocké : la liste se recalcule à chaque écoute, donc « Jamais écoutés »
 * rétrécit au fur et à mesure. C'est le comportement attendu — une playlist figée au
 * moment de sa création aurait perdu son sens dès le premier morceau joué.
 */
data class SmartPlaylist(
    val id: SmartPlaylistId,
    val title: String,
    val description: String,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
    val durationMs: Long get() = songs.sumOf { it.durationMs }
    val artworkUris get() = songs.distinctBy { it.albumId }.take(4).map { it.artworkUri }
}

@Singleton
class SmartPlaylistRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * Les six règles, recalculées dès que la bibliothèque, les stats ou les favoris bougent.
     *
     * Les listes vides sont retirées : une playlist « Les plus zappés » sans morceau
     * zappé n'a rien à dire, et une grille de cartes vides donne l'impression d'un bug.
     */
    val playlists: StateFlow<List<SmartPlaylist>> = combine(
        musicRepository.library.map { it.songs },
        musicRepository.allStats,
        musicRepository.favoriteIds,
    ) { songs, stats, favoriteIds ->
        if (songs.isEmpty()) return@combine emptyList()
        buildList {
            add(neverPlayed(songs, stats))
            add(fresh(songs))
            add(onRepeat(songs, stats))
            add(hiddenGems(songs, stats))
            add(forgottenFavorites(songs, stats, favoriteIds))
            add(mostSkipped(songs, stats))
        }.filter { it.songs.isNotEmpty() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun byId(id: SmartPlaylistId): SmartPlaylist? = playlists.value.firstOrNull { it.id == id }

    // --- Les règles ------------------------------------------------------------------

    /** Jamais lancés une seule fois. Les plus récents d'abord : ce sont les plus tentants. */
    private fun neverPlayed(songs: List<Song>, stats: Map<Long, PlayStatEntity>) = SmartPlaylist(
        id = SmartPlaylistId.NEVER_PLAYED,
        title = "Jamais écoutés",
        description = "Des titres de ta bibliothèque que tu n'as encore jamais lancés",
        songs = songs
            .filter { (stats[it.id]?.playCount ?: 0) == 0 }
            .sortedByDescending { it.dateAddedSec }
            .take(MAX_SONGS),
    )

    /** Ajoutés dans le mois : la réponse à « qu'est-ce que j'ai mis récemment ? ». */
    private fun fresh(songs: List<Song>): SmartPlaylist {
        val cutoff = (System.currentTimeMillis() / 1000) - THIRTY_DAYS_SEC
        return SmartPlaylist(
            id = SmartPlaylistId.FRESH,
            title = "Arrivés ce mois-ci",
            description = "Ajoutés à ta bibliothèque depuis moins de 30 jours",
            songs = songs
                .filter { it.dateAddedSec >= cutoff }
                .sortedByDescending { it.dateAddedSec }
                .take(MAX_SONGS),
        )
    }

    /** Ce que tu écoutes en ce moment : joué récemment *et* souvent. */
    private fun onRepeat(songs: List<Song>, stats: Map<Long, PlayStatEntity>): SmartPlaylist {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        return SmartPlaylist(
            id = SmartPlaylistId.ON_REPEAT,
            title = "En boucle",
            description = "Tes obsessions des trente derniers jours",
            songs = songs
                .mapNotNull { song -> stats[song.id]?.let { song to it } }
                .filter { (_, stat) -> stat.lastPlayedAt >= cutoff && stat.playCount >= 3 }
                .sortedByDescending { (_, stat) -> stat.playCount }
                .map { it.first }
                .take(MAX_SONGS),
        )
    }

    /**
     * Peu lancés, mais jamais coupés.
     *
     * Un faible compteur ne dit rien à lui seul : il peut signaler un titre que tu zappes.
     * On le croise donc avec le taux d'écoute réel — durée écoutée rapportée à ce qu'elle
     * aurait été si le morceau avait été joué en entier à chaque fois.
     */
    private fun hiddenGems(songs: List<Song>, stats: Map<Long, PlayStatEntity>) = SmartPlaylist(
        id = SmartPlaylistId.HIDDEN_GEMS,
        title = "Pépites oubliées",
        description = "Rarement lancés, mais jamais coupés avant la fin",
        songs = songs
            .mapNotNull { song -> stats[song.id]?.let { song to it } }
            .filter { (song, stat) ->
                stat.playCount in 1..3 &&
                    stat.skipCount == 0 &&
                    completionRatio(song, stat) >= 0.8f
            }
            .sortedByDescending { (_, stat) -> stat.lastPlayedAt }
            .map { it.first }
            .take(MAX_SONGS),
    )

    /** Favoris délaissés : marqués comme aimés, mais pas joués depuis deux mois. */
    private fun forgottenFavorites(
        songs: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favoriteIds: Set<Long>,
    ): SmartPlaylist {
        val cutoff = System.currentTimeMillis() - SIXTY_DAYS_MS
        return SmartPlaylist(
            id = SmartPlaylistId.FORGOTTEN_FAVORITES,
            title = "Favoris délaissés",
            description = "Tu les as aimés, tu ne les as pas lancés depuis deux mois",
            songs = songs
                .filter { it.id in favoriteIds }
                .filter { (stats[it.id]?.lastPlayedAt ?: 0L) < cutoff }
                .sortedBy { stats[it.id]?.lastPlayedAt ?: 0L }
                .take(MAX_SONGS),
        )
    }

    /**
     * Les titres que tu coupes systématiquement.
     *
     * Utile pour faire le ménage : c'est la liste des candidats à la suppression ou à
     * l'exclusion de l'aléatoire.
     */
    private fun mostSkipped(songs: List<Song>, stats: Map<Long, PlayStatEntity>) = SmartPlaylist(
        id = SmartPlaylistId.MOST_SKIPPED,
        title = "Souvent zappés",
        description = "Coupés plus souvent qu'écoutés jusqu'au bout",
        songs = songs
            .mapNotNull { song -> stats[song.id]?.let { song to it } }
            .filter { (_, stat) -> stat.skipCount >= 2 && stat.skipCount > stat.playCount }
            .sortedByDescending { (_, stat) -> stat.skipCount }
            .map { it.first }
            .take(MAX_SONGS),
    )

    /**
     * Part du morceau réellement écoutée, moyennée sur toutes les lectures.
     *
     * Plafonnée à 1 : réécouter un passage en boucle peut faire dépasser la durée du
     * fichier, ce qui gonflerait artificiellement le score.
     */
    private fun completionRatio(song: Song, stat: PlayStatEntity): Float {
        if (song.durationMs <= 0 || stat.playCount <= 0) return 0f
        val expected = song.durationMs.toFloat() * stat.playCount
        return min(1f, stat.totalListenedMs / expected)
    }

    private companion object {
        const val MAX_SONGS = 100
        const val THIRTY_DAYS_SEC = 30L * 24 * 3600
        const val THIRTY_DAYS_MS = 30L * 24 * 3600 * 1000
        const val SIXTY_DAYS_MS = 60L * 24 * 3600 * 1000
    }
}
