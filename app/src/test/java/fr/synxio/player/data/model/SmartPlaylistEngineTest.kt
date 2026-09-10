package fr.synxio.player.data.model

import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistEngineTest {

    private val now = 1_700_000_000_000L   // millisecondes
    private val dayMs = 86_400_000L

    private fun rules(
        vararg rule: SmartRule,
        matchAll: Boolean = true,
        sort: SmartSort = SmartSort.TITLE,
        descending: Boolean = false,
        limit: Int? = null,
    ) = SmartPlaylistRules(rule.toList(), matchAll, sort, descending, limit)

    private fun stat(songId: Long, playCount: Int = 0, skipCount: Int = 0, lastPlayedAt: Long = 0) =
        songId to PlayStatEntity(songId, "/music/$songId.mp3", playCount, skipCount, lastPlayedAt, 0)

    @Test
    fun `contains est insensible a la casse`() {
        val songs = listOf(song(id = 1, title = "Ainsi bas la vida"), song(id = 2, title = "Macarena"))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.TITLE, RuleOperator.CONTAINS, "AINSI")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `matchAll false combine les regles en OU`() {
        val songs = listOf(
            song(id = 1, artist = "Damso"),
            song(id = 2, artist = "Orelsan"),
            song(id = 3, artist = "Nekfeu"),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(
                SmartRule(RuleField.ARTIST, RuleOperator.EQUALS, "Damso"),
                SmartRule(RuleField.ARTIST, RuleOperator.EQUALS, "Orelsan"),
                matchAll = false,
            ),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L, 2L), result.map { it.id }.sorted())
    }

    @Test
    fun `matchAll true exige toutes les regles`() {
        val songs = listOf(
            song(id = 1, artist = "Damso", year = 2020),
            song(id = 2, artist = "Damso", year = 2017),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(
                SmartRule(RuleField.ARTIST, RuleOperator.EQUALS, "Damso"),
                SmartRule(RuleField.YEAR, RuleOperator.GREATER_THAN, "2018"),
            ),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `play count lit les statistiques et traite l'absence comme zero`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(stat(1, playCount = 5))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "2")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `jamais ecoute remonte les morceaux sans statistique`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(stat(1, playCount = 3))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "1")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(2L), result.map { it.id })
    }

    // lastPlayedAt est en MILLISECONDES (MusicRepository.kt:247)
    @Test
    fun `in last days compare lastPlayedAt en millisecondes`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(
            stat(1, playCount = 1, lastPlayedAt = now - 5 * dayMs),
            stat(2, playCount = 1, lastPlayedAt = now - 40 * dayMs),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.LAST_PLAYED, RuleOperator.IN_LAST_DAYS, "30")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `not in last days ignore les morceaux jamais joues`() {
        // lastPlayedAt = 0 signifie « jamais joué », pas « joué en 1970 » :
        // le preset « Oubliés » ne doit pas se remplir de morceaux jamais écoutés.
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(stat(1, playCount = 1, lastPlayedAt = now - 300 * dayMs))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "180")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    // dateAddedSec est en SECONDES (Song.kt)
    @Test
    fun `date added compare des secondes`() {
        val nowSec = now / 1000
        val songs = listOf(
            song(id = 1, dateAddedSec = nowSec - 5 * 86_400),
            song(id = 2, dateAddedSec = nowSec - 60 * 86_400),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.DATE_ADDED, RuleOperator.IN_LAST_DAYS, "30")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `favori lit l'ensemble des favoris`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")),
            songs, emptyMap(), setOf(2L), now,
        )
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `duration compare des secondes`() {
        val songs = listOf(song(id = 1, durationMs = 120_000), song(id = 2, durationMs = 400_000))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.DURATION, RuleOperator.GREATER_THAN, "300")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `folder compare le dossier parent`() {
        val songs = listOf(
            song(id = 1, path = "/storage/Music/Rap/a.mp3"),
            song(id = 2, path = "/storage/Music/Jazz/b.mp3"),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.FOLDER, RuleOperator.CONTAINS, "Rap")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `tri decroissant et limite`() {
        val songs = (1L..5L).map { song(id = it, year = 2000 + it.toInt()) }
        val result = SmartPlaylistEngine.evaluate(
            rules(sort = SmartSort.YEAR, descending = true, limit = 2),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(5L, 4L), result.map { it.id })
    }

    @Test
    fun `aucune regle retourne toute la bibliotheque`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val result = SmartPlaylistEngine.evaluate(rules(), songs, emptyMap(), emptySet(), now)
        assertEquals(2, result.size)
    }

    @Test
    fun `une valeur numerique illisible ne fait echouer que sa regle`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.YEAR, RuleOperator.GREATER_THAN, "pas un nombre")),
            songs, emptyMap(), emptySet(), now,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `bibliotheque vide ne plante pas`() {
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.TITLE, RuleOperator.CONTAINS, "x")),
            emptyList(), emptyMap(), emptySet(), now,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `le tri aleatoire est stable pour un meme jour`() {
        val songs = (1L..20L).map { song(id = it) }
        val r = rules(sort = SmartSort.RANDOM)
        val a = SmartPlaylistEngine.evaluate(r, songs, emptyMap(), emptySet(), now)
        val b = SmartPlaylistEngine.evaluate(r, songs, emptyMap(), emptySet(), now + 1000)
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `les regles survivent a un aller-retour json`() {
        val original = rules(
            SmartRule(RuleField.ARTIST, RuleOperator.CONTAINS, "Damso"),
            SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "3"),
            matchAll = false, sort = SmartSort.PLAY_COUNT, descending = true, limit = 50,
        )
        assertEquals(original, SmartPlaylistRules.fromJson(original.toJson()))
    }

    @Test
    fun `un json corrompu retombe sur des regles vides`() {
        assertEquals(SmartPlaylistRules(), SmartPlaylistRules.fromJson("{pas du json"))
    }

    @Test
    fun `encode et decode d'un identifiant`() {
        assertEquals(SmartPlaylistId.Preset("recent"), SmartPlaylistId.decode("preset:recent"))
        assertEquals(SmartPlaylistId.Stored(12), SmartPlaylistId.decode("stored:12"))
        assertEquals("preset:recent", SmartPlaylistId.Preset("recent").encode())
        assertEquals("stored:12", SmartPlaylistId.Stored(12).encode())
    }
}
