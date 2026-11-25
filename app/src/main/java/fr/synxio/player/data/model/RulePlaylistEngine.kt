package fr.synxio.player.data.model

import fr.synxio.player.core.util.normalizeForSearch
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
        ratings: Map<Long, Int> = emptyMap(),
        traits: Map<String, TrackTraits> = emptyMap(),
    ): List<Song> {
        val matching = if (rules.rules.isEmpty() && rules.anyOf.isEmpty()) songs else songs.filter { s ->
            fun test(rule: SmartRule) = matches(rule, s, stats[s.id], s.id in favorites, ratings[s.id] ?: 0, now, traits[s.path])
            val main = when {
                rules.rules.isEmpty() -> true
                rules.matchAll -> rules.rules.all(::test)
                else -> rules.rules.any(::test)
            }
            // « L'un de » : un OU à l'intérieur du ET général, quelle que soit matchAll.
            main && (rules.anyOf.isEmpty() || rules.anyOf.any(::test))
        }
        val sorted = sort(matching, rules, stats, now, traits)
        return rules.limit?.takeIf { it > 0 }?.let(sorted::take) ?: sorted
    }

    /** Nombre de morceaux correspondants, pour l'aperçu live de l'éditeur. */
    fun count(
        rules: RulePlaylistRules,
        songs: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favorites: Set<Long>,
        now: Long,
        ratings: Map<Long, Int> = emptyMap(),
        traits: Map<String, TrackTraits> = emptyMap(),
    ): Int = evaluate(rules.copy(limit = null), songs, stats, favorites, now, ratings, traits).size

    private fun matches(
        rule: SmartRule,
        song: Song,
        stat: PlayStatEntity?,
        isFavorite: Boolean,
        stars: Int,
        now: Long,
        traits: TrackTraits?,
    ): Boolean = when (rule.field) {
        // Empreinte 2 : un titre non analysé ne correspond jamais (plutôt que « 0 BPM »).
        RuleField.BPM -> traits?.takeIf { it.hasBpm }?.let { number(rule, it.bpm.toLong()) } ?: false
        RuleField.ENERGY -> traits?.let { number(rule, (it.energy * 100).toLong()) } ?: false
        RuleField.KEY -> traits?.takeIf { it.hasKey }?.let { key(rule, it) } ?: false

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
        RuleField.RATING -> number(rule, stars.toLong())

        RuleField.LAST_PLAYED -> date(rule, stat?.lastPlayedAt ?: 0L, now)
        RuleField.DATE_ADDED -> date(rule, song.dateAddedSec * 1000L, now)

        RuleField.FAVORITE -> when (rule.operator) {
            RuleOperator.IS_TRUE -> isFavorite
            RuleOperator.IS_FALSE -> !isFavorite
            else -> false
        }
    }

    /** « est 8A » / « est la mineur » comparent la tonalité ; « contient mineur » filtre le mode. */
    private fun key(rule: SmartRule, traits: TrackTraits): Boolean {
        val wanted = KeyNames.parse(rule.value)
        return when (rule.operator) {
            RuleOperator.EQUALS -> wanted != null && wanted == traits.keyIndex
            RuleOperator.NOT_EQUALS -> wanted != null && wanted != traits.keyIndex
            else -> text(rule, "${traits.keyName} ${traits.camelot}")
        }
    }

    /** Insensible à la casse et aux accents : « métal » attrape « Metal » comme « Métal ». */
    private fun text(rule: SmartRule, actual: String): Boolean {
        val a = actual.normalizeForSearch()
        val b = rule.value.trim().normalizeForSearch()
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
        traits: Map<String, TrackTraits> = emptyMap(),
    ): List<Song> {
        if (rules.sort == SmartSort.RANDOM) {
            // Graine sur le jour courant : la playlist reste stable pendant la session
            // au lieu de se réordonner à chaque recomposition.
            return songs.shuffled(Random(now / DAY_MS))
        }
        if (rules.sort == SmartSort.FLOW) return flowOrder(songs, traits)
        val comparator: Comparator<Song> = when (rules.sort) {
            SmartSort.TITLE -> compareBy { it.title.lowercase() }
            SmartSort.ARTIST -> compareBy { it.artist.lowercase() }
            SmartSort.ALBUM -> compareBy { it.album.lowercase() }
            SmartSort.YEAR -> compareBy { it.year }
            SmartSort.DATE_ADDED -> compareBy { it.dateAddedSec }
            SmartSort.PLAY_COUNT -> compareBy { stats[it.id]?.playCount ?: 0 }
            SmartSort.LAST_PLAYED -> compareBy { stats[it.id]?.lastPlayedAt ?: 0L }
            SmartSort.BPM -> compareBy { traits[it.path]?.bpm ?: 0f }
            SmartSort.ENERGY -> compareBy { traits[it.path]?.energy ?: 0f }
            SmartSort.RANDOM, SmartSort.FLOW -> compareBy { it.id }   // inatteignables, exhaustivité du when
        }
        return songs.sortedWith(if (rules.descending) comparator.reversed() else comparator)
    }

    /**
     * Ordre Flow : départ sur le premier titre analysé, puis plus proche voisin et 2-opt
     * (`FlowEngine`). Les titres non analysés se retrouvent en fin. Un même titre peut
     * apparaître une seule fois dans une playlist à règles : la position sert d'identifiant.
     */
    private fun flowOrder(songs: List<Song>, traits: Map<String, TrackTraits>): List<Song> {
        if (songs.size < 3) return songs
        val tracks = songs.mapIndexed { index, song -> song.toFlowTrack(index.toLong(), traits[song.path]) }
        val start = tracks.firstOrNull { it.hasData }?.id
        return FlowEngine.order(tracks, startId = start).map { songs[it.toInt()] }
    }
}
