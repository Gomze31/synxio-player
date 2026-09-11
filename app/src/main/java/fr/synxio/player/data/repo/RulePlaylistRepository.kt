package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.db.RulePlaylistDao
import fr.synxio.player.data.db.RulePlaylistEntity
import fr.synxio.player.data.model.RulePlaylistEngine
import fr.synxio.player.data.model.RulePlaylistRules
import fr.synxio.player.data.model.Song
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Une playlist à règles créée par l'utilisateur, prête à afficher : son nom, ses règles et son résultat courant. */
data class RulePlaylist(
    val id: Long,
    val name: String,
    val rules: RulePlaylistRules,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
}

/**
 * Instantané bibliothèque + stats + favoris, en un seul objet.
 *
 * [RulePlaylistRepository.countMatching] lit cet instantané hors du flux réactif : le
 * regrouper en un seul objet immuable derrière un unique champ `@Volatile` garantit que
 * la lecture voit des stats et des favoris cohérents entre eux, plutôt que deux champs
 * séparés qui pourraient être mis à jour l'un sans l'autre entre deux lectures.
 */
private data class LibrarySnapshot(
    val songs: List<Song>,
    val stats: Map<Long, PlayStatEntity>,
    val favorites: Set<Long>,
)

/**
 * Croise les règles utilisateur avec la bibliothèque, les statistiques et les favoris.
 *
 * Tout est réactif : jouer un morceau ou cocher un favori met les playlists à jour sans
 * que l'utilisateur ait à rafraîchir quoi que ce soit.
 */
@Singleton
class RulePlaylistRepository @Inject constructor(
    private val dao: RulePlaylistDao,
    private val musicRepository: MusicRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // Alimenté par le même `combine` que [rulePlaylists] : `countMatching` s'en sert pour
    // que l'aperçu live de l'éditeur voie les vraies statistiques et les vrais favoris,
    // pas une bibliothèque amnésique.
    @Volatile
    private var snapshot = LibrarySnapshot(emptyList(), emptyMap(), emptySet())

    val rulePlaylists: StateFlow<List<RulePlaylist>> = combine(
        musicRepository.library.map { it.songs },
        musicRepository.allStats,
        musicRepository.favoriteIds,
        dao.observeAll(),
    ) { songs, stats, favoriteIds, stored ->
        snapshot = LibrarySnapshot(songs, stats, favoriteIds)
        val now = System.currentTimeMillis()
        stored.map { entity ->
            val rules = RulePlaylistRules.fromJson(entity.rulesJson)
            RulePlaylist(
                id = entity.id,
                name = entity.name,
                rules = rules,
                songs = RulePlaylistEngine.evaluate(rules, songs, stats, favoriteIds, now),
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observe(id: Long): Flow<RulePlaylist?> =
        rulePlaylists.map { list -> list.firstOrNull { it.id == id } }

    /** Aperçu live de l'éditeur : combien de titres la règle en cours attrape. */
    fun countMatching(rules: RulePlaylistRules): Int {
        val current = snapshot
        return RulePlaylistEngine.count(
            rules = rules,
            songs = current.songs,
            stats = current.stats,
            favorites = current.favorites,
            now = System.currentTimeMillis(),
        )
    }

    suspend fun load(id: Long): Pair<String, RulePlaylistRules>? =
        dao.get(id)?.let { it.name to RulePlaylistRules.fromJson(it.rulesJson) }

    /** [id] à 0 ou moins crée une nouvelle playlist ; sinon met à jour l'existante. */
    suspend fun save(id: Long, name: String, rules: RulePlaylistRules): Long {
        val now = System.currentTimeMillis()
        return if (id > 0) {
            val existing = dao.get(id)
            dao.update(
                RulePlaylistEntity(
                    id = id,
                    name = name,
                    rulesJson = rules.toJson(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
            id
        } else {
            dao.insert(
                RulePlaylistEntity(
                    name = name, rulesJson = rules.toJson(), createdAt = now, updatedAt = now,
                )
            )
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
