package fr.synxio.player.data.model

import fr.synxio.player.data.db.PlayStatEntity
import kotlin.random.Random

/**
 * Évaluation d'un jeu de règles contre la bibliothèque.
 *
 * Volontairement sans dépendance Android : c'est la pièce qui mérite le plus d'être
 * testée, autant qu'elle tourne en JVM pure.
 *
 * Attention aux unités : [PlayStatEntity.lastPlayedAt] est en millisecondes,
 * [Song.dateAddedSec] en secondes. Les mélanger décale les comparaisons d'un facteur 1000.
 */
object RulePlaylistEngine {

    private const val DAY_MS = 86_400_000L

    fun evaluate(
        rules: RulePlaylistRules,
        songs: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favorites: Set<Long>,
        now: Long,
    ): List<Song> {
        val matching = when {
            rules.rules.isEmpty() -> songs
            rules.matchAll -> songs.filter { s ->
                rules.rules.all { matches(it, s, stats[s.id], s.id in favorites, now) }
            }
            else -> songs.filter { s ->
                rules.rules.any { matches(it, s, stats[s.id], s.id in favorites, now) }
            }
        }
        val sorted = sort(matching, rules, stats, now)
        return rules.limit?.takeIf { it > 0 }?.let(sorted::take) ?: sorted
    }

    /** Nombre de morceaux correspondants, pour l'aperçu live de l'éditeur. */
    fun count(
        rules: RulePlaylistRules,
        songs: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favorites: Set<Long>,
        now: Long,
    ): Int = evaluate(rules.copy(limit = null), songs, stats, favorites, now).size

    private fun matches(
        rule: SmartRule,
        song: Song,
        stat: PlayStatEntity?,
        isFavorite: Boolean,
        now: Long,
    ): Boolean = when (rule.field) {
        RuleField.TITLE -> text(rule, song.title)
        RuleField.ARTIST -> text(rule, song.artist)
        RuleField.ALBUM -> text(rule, song.album)
        RuleField.ALBUM_ARTIST -> text(rule, song.albumArtist.orEmpty())
        RuleField.GENRE -> text(rule, song.genre.orEmpty())
        RuleField.FOLDER -> text(rule, song.folderPath)
        RuleField.FORMAT -> text(rule, song.extension)

        RuleField.YEAR -> number(rule, song.year.toLong())
        RuleField.DURATION -> number(rule, song.durationMs / 1000)
        RuleField.PLAY_COUNT -> number(rule, (stat?.playCount ?: 0).toLong())
        RuleField.SKIP_COUNT -> number(rule, (stat?.skipCount ?: 0).toLong())
        RuleField.BITRATE -> number(rule, song.approxBitrateKbps.toLong())

        RuleField.LAST_PLAYED -> date(rule, stat?.lastPlayedAt ?: 0L, now)
        RuleField.DATE_ADDED -> date(rule, song.dateAddedSec * 1000L, now)

        RuleField.FAVORITE -> when (rule.operator) {
            RuleOperator.IS_TRUE -> isFavorite
            RuleOperator.IS_FALSE -> !isFavorite
            else -> false
        }
    }

    private fun text(rule: SmartRule, actual: String): Boolean {
        val a = actual.lowercase()
        val b = rule.value.trim().lowercase()
        return when (rule.operator) {
            RuleOperator.CONTAINS -> a.contains(b)
            RuleOperator.NOT_CONTAINS -> !a.contains(b)
            RuleOperator.EQUALS -> a == b
            RuleOperator.NOT_EQUALS -> a != b
            RuleOperator.STARTS_WITH -> a.startsWith(b)
            else -> false
        }
    }

    /** Une valeur non numérique ne fait échouer que sa propre règle. */
    private fun number(rule: SmartRule, actual: Long): Boolean {
        val expected = rule.value.trim().toLongOrNull() ?: return false
        return when (rule.operator) {
            RuleOperator.EQUALS -> actual == expected
            RuleOperator.NOT_EQUALS -> actual != expected
            RuleOperator.GREATER_THAN -> actual > expected
            RuleOperator.LESS_THAN -> actual < expected
            else -> false
        }
    }

    /**
     * [timestampMs] à 0 signifie « jamais » : un morceau jamais joué n'est pas
     * « oublié depuis 180 jours », il n'a simplement pas d'historique.
     */
    private fun date(rule: SmartRule, timestampMs: Long, now: Long): Boolean {
        val days = rule.value.trim().toLongOrNull() ?: return false
        if (timestampMs <= 0L) return false
        val threshold = now - days * DAY_MS
        return when (rule.operator) {
            RuleOperator.IN_LAST_DAYS -> timestampMs >= threshold
            RuleOperator.NOT_IN_LAST_DAYS -> timestampMs < threshold
            else -> false
        }
    }

    private fun sort(
        songs: List<Song>,
        rules: RulePlaylistRules,
        stats: Map<Long, PlayStatEntity>,
        now: Long,
    ): List<Song> {
        if (rules.sort == SmartSort.RANDOM) {
            // Graine sur le jour courant : la playlist reste stable pendant la session
            // au lieu de se réordonner à chaque recomposition.
            return songs.shuffled(Random(now / DAY_MS))
        }
        val comparator: Comparator<Song> = when (rules.sort) {
            SmartSort.TITLE -> compareBy { it.title.lowercase() }
            SmartSort.ARTIST -> compareBy { it.artist.lowercase() }
            SmartSort.ALBUM -> compareBy { it.album.lowercase() }
            SmartSort.YEAR -> compareBy { it.year }
            SmartSort.DATE_ADDED -> compareBy { it.dateAddedSec }
            SmartSort.PLAY_COUNT -> compareBy { stats[it.id]?.playCount ?: 0 }
            SmartSort.LAST_PLAYED -> compareBy { stats[it.id]?.lastPlayedAt ?: 0L }
            SmartSort.RANDOM -> compareBy { it.id }   // inatteignable, exhaustivité du when
        }
        return songs.sortedWith(if (rules.descending) comparator.reversed() else comparator)
    }
}
