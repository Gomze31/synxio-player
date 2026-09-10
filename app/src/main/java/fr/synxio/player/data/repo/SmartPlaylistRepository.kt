package fr.synxio.player.data.repo

import fr.synxio.player.data.db.FavoriteDao
import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.db.SmartPlaylistDao
import fr.synxio.player.data.db.SmartPlaylistEntity
import fr.synxio.player.data.model.SmartPlaylist
import fr.synxio.player.data.model.SmartPlaylistEngine
import fr.synxio.player.data.model.SmartPlaylistId
import fr.synxio.player.data.model.SmartPlaylistPresets
import fr.synxio.player.data.model.SmartPlaylistRules
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

/**
 * Croise les règles avec la bibliothèque, les statistiques et les favoris.
 *
 * Tout est réactif : jouer un morceau ou cocher un favori met les playlists à jour
 * sans que l'utilisateur ait à rafraîchir quoi que ce soit.
 */
@Singleton
class SmartPlaylistRepository @Inject constructor(
    private val dao: SmartPlaylistDao,
    private val musicRepository: MusicRepository,
    private val playStatDao: PlayStatDao,
    private val favoriteDao: FavoriteDao,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // Alimentés par le même `combine` que [smartPlaylists] : `countMatching` s'en sert
    // pour que l'aperçu live de l'éditeur voie les vraies statistiques et favoris, pas
    // une bibliothèque amnésique. La bibliothèque étant déjà entièrement en mémoire, le
    // coût de ce cache est imperceptible.
    @Volatile private var statsCache: Map<Long, PlayStatEntity> = emptyMap()
    @Volatile private var favoritesCache: Set<Long> = emptySet()

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
        favoriteDao.observeAll(),
        dao.observeAll(),
    ) { library, stats, favorites, stored ->
        val statsById = stats.associateBy { it.songId }
        val favoriteIds = favorites.map { it.songId }.toSet()
        statsCache = statsById
        favoritesCache = favoriteIds
        val now = System.currentTimeMillis()

        val presets = SmartPlaylistPresets.all.map { preset ->
            SmartPlaylist(
                id = SmartPlaylistId.Preset(preset.key),
                name = preset.name,
                rules = preset.rules,
                songs = SmartPlaylistEngine.evaluate(
                    preset.rules, library.songs, statsById, favoriteIds, now,
                ),
            )
        }

        val custom = stored.map { entity ->
            val rules = SmartPlaylistRules.fromJson(entity.rulesJson)
            SmartPlaylist(
                id = SmartPlaylistId.Stored(entity.id),
                name = entity.name,
                rules = rules,
                songs = SmartPlaylistEngine.evaluate(
                    rules, library.songs, statsById, favoriteIds, now,
                ),
            )
        }

        custom + presets
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun observe(id: SmartPlaylistId): Flow<SmartPlaylist?> =
        smartPlaylists.map { list -> list.firstOrNull { it.id == id } }

    /** Aperçu live de l'éditeur : combien de titres la règle en cours attrape. */
    fun countMatching(rules: SmartPlaylistRules): Int = SmartPlaylistEngine.count(
        rules = rules,
        songs = musicRepository.library.value.songs,
        stats = statsCache,
        favorites = favoritesCache,
        now = System.currentTimeMillis(),
    )

    suspend fun load(id: Long): Pair<String, SmartPlaylistRules>? =
        dao.get(id)?.let { it.name to SmartPlaylistRules.fromJson(it.rulesJson) }

    /** [id] à 0 ou moins crée une nouvelle playlist ; sinon met à jour l'existante. */
    suspend fun save(id: Long, name: String, rules: SmartPlaylistRules): Long {
        val now = System.currentTimeMillis()
        return if (id > 0) {
            val existing = dao.get(id)
            dao.update(
                SmartPlaylistEntity(
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
                SmartPlaylistEntity(
                    name = name, rulesJson = rules.toJson(), createdAt = now, updatedAt = now,
                )
            )
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
