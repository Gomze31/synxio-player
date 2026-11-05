package fr.synxio.player.data.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * Une carte « Souvenirs » de l'accueil : une liste de titres à lancer, avec un titre et
 * un sous-titre. Les clés sont stables dans la journée (clés de liste Compose).
 */
sealed interface Memory {
    val key: String
    val title: String
    val subtitle: String
    val songs: List<Song>

    /** Mosaïque : quatre pochettes d'albums distincts, comme les mixs du jour. */
    val artworkUris: List<Any> get() = songs.asSequence().distinctBy { it.albumId }.take(4).map { it.artworkUri }.toList()

    data class ThatDay(val yearsAgo: Int, override val songs: List<Song>) : Memory {
        override val key = "thatday-$yearsAgo"
        override val title = "Ce jour-là, il y a $yearsAgo an" + if (yearsAgo > 1) "s" else ""
        override val subtitle = "${songs.size} titres écoutés ce jour-là"
    }

    data class TimeCapsule(val month: YearMonth, override val songs: List<Song>) : Memory {
        override val key = "capsule-%04d-%02d".format(month.year, month.monthValue)
        override val title = "Capsule : ${month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)} ${month.year}"
        override val subtitle = "${songs.size} titres les plus écoutés ce mois-là"
    }

    data class FirstTimes(override val songs: List<Song>) : Memory {
        override val key = "firsttimes"
        override val title = "Tes premières fois du mois"
        override val subtitle = "${songs.size} découvertes que tu as réécoutées"
    }

    data class UnfinishedAlbum(val album: Album, val playedCount: Int, override val songs: List<Song>) : Memory {
        override val key = "album-${album.id}"
        override val title = "Finir ${album.title}"
        override val subtitle = "$playedCount titres sur ${album.songCount} écoutés"
    }
}

/**
 * Compose les souvenirs à partir de l'historique. Fonction pure : la date du jour et le
 * fuseau sont passés en argument, rien n'est stocké.
 */
object MemoryEngine {

    fun build(songs: List<Song>, albums: List<Album>, history: List<PlayHistory>, today: LocalDate, zone: ZoneId): List<Memory> {
        if (history.isEmpty()) return emptyList()
        val byId = songs.associateBy { it.id }
        val plays = history.filter { it.song.id in byId }.map { Play(it.song.id, it.playedAt.atZone(zone).toLocalDate(), it.listenedMs) }
        return buildList {
            thatDay(plays, byId, today, 1)?.let(::add)
            thatDay(plays, byId, today, 2)?.let(::add)
            timeCapsule(plays, byId, today)?.let(::add)
            firstTimes(plays, byId, today)?.let(::add)
            addAll(unfinishedAlbums(plays, albums, today))
        }
    }

    private class Play(val songId: Long, val date: LocalDate, val listenedMs: Long)

    private fun thatDay(plays: List<Play>, byId: Map<Long, Song>, today: LocalDate, yearsAgo: Int): Memory.ThatDay? {
        val date = today.minusYears(yearsAgo.toLong())
        val ids = plays.filter { it.date == date }
            .groupingBy { it.songId }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        if (ids.size < MIN_CARD) return null
        return Memory.ThatDay(yearsAgo, ids.mapNotNull(byId::get))
    }

    private fun timeCapsule(plays: List<Play>, byId: Map<Long, Song>, today: LocalDate): Memory.TimeCapsule? {
        val newest = YearMonth.from(today.minusMonths(CAPSULE_MIN_MONTHS))
        val oldest = YearMonth.from(today.minusMonths(CAPSULE_MAX_MONTHS))
        val byMonth = plays.groupBy { YearMonth.from(it.date) }
            .filterKeys { it in oldest..newest }
            .filterValues { it.size >= CAPSULE_MIN_PLAYS }
        if (byMonth.isEmpty()) return null
        val top = byMonth.entries.sortedByDescending { e -> e.value.sumOf { it.listenedMs } }.take(3)
        val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + 100 * today.get(IsoFields.WEEK_BASED_YEAR)
        val chosen = top[week % top.size]
        val ids = chosen.value.groupingBy { it.songId }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(CAPSULE_SIZE).map { it.key }
        return Memory.TimeCapsule(chosen.key, ids.mapNotNull(byId::get))
    }

    private fun firstTimes(plays: List<Play>, byId: Map<Long, Song>, today: LocalDate): Memory.FirstTimes? {
        val since = today.minusDays(FIRST_TIMES_DAYS)
        val firstPlay = plays.groupBy { it.songId }.mapValues { (_, p) -> p.minOf { it.date } }
        val counts = plays.groupingBy { it.songId }.eachCount()
        val ids = firstPlay.filterValues { it >= since }.keys
            .sortedWith(compareByDescending<Long> { counts.getValue(it) }.thenBy { it })
            .take(FIRST_TIMES_SIZE)
        if (ids.size < MIN_CARD) return null
        return Memory.FirstTimes(ids.mapNotNull(byId::get))
    }

    private fun unfinishedAlbums(plays: List<Play>, albums: List<Album>, today: LocalDate): List<Memory.UnfinishedAlbum> {
        val cutoff = today.minusDays(UNFINISHED_IDLE_DAYS)
        val lastPlay = plays.groupBy { it.songId }.mapValues { (_, p) -> p.maxOf { it.date } }
        return albums.asSequence()
            .filter { it.songCount >= UNFINISHED_MIN_SONGS }
            .mapNotNull { album ->
                val played = album.songs.filter { it.id in lastPlay }
                if (played.isEmpty() || played.size * 2 >= album.songCount) return@mapNotNull null
                val last = played.maxOf { lastPlay.getValue(it.id) }
                if (last >= cutoff) return@mapNotNull null
                val remaining = album.songs.filter { it.id !in lastPlay }.sortedWith(compareBy({ it.disc }, { it.track }))
                Triple(album, played.size, remaining) to last
            }
            .sortedBy { it.second }
            .take(UNFINISHED_MAX)
            .map { (t, _) -> Memory.UnfinishedAlbum(t.first, t.second, t.third) }
            .toList()
    }

    private const val MIN_CARD = 3
    private const val CAPSULE_MIN_MONTHS = 6L
    private const val CAPSULE_MAX_MONTHS = 24L
    private const val CAPSULE_MIN_PLAYS = 10
    private const val CAPSULE_SIZE = 25
    private const val FIRST_TIMES_DAYS = 30L
    private const val FIRST_TIMES_SIZE = 20
    private const val UNFINISHED_MIN_SONGS = 5
    private const val UNFINISHED_IDLE_DAYS = 30L
    private const val UNFINISHED_MAX = 5
}
