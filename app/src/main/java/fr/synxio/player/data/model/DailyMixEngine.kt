package fr.synxio.player.data.model

import androidx.compose.runtime.Immutable
import fr.synxio.player.data.db.PlayStatEntity
import kotlin.random.Random

enum class DailyMixKind { DAILY, SIMILAR, ARTIST, DISCOVERY }

/**
 * Un mix renouvelé chaque jour.
 *
 * À la différence des playlists intelligentes, exhaustives et triées, un mix est court,
 * mélangé et varié : c'est une écoute à lancer telle quelle, pas un inventaire.
 */
@Immutable
data class DailyMix(
    /** Clé stable dans la journée : clé de liste Compose et de navigation. */
    val key: String,
    val kind: DailyMixKind,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
) {
    val artworkUris get() = songs.asSequence().distinctBy { it.albumId }.take(4).map { it.artworkUri }.toList()
}

/**
 * Compose les mixs du jour à partir de la bibliothèque et des statistiques.
 *
 * Fonction pure : le hasard vient d'une graine tirée du numéro du jour, si bien qu'un mix
 * reste identique toute la journée (on peut y revenir) et change le lendemain. Rien n'est
 * stocké.
 */
object DailyMixEngine {

    const val MIX_SIZE = 25
    const val DAY_MS = 24L * 3600 * 1000
    private const val MIN_SIZE = 8
    private const val MAX_PER_ARTIST = 2
    private const val FAMILIAR_SHARE = 15
    private const val ARTIST_OWN_SONGS = 10
    private const val ARTIST_MIXES = 3

    /**
     * [similarSeed] et [similar] : titre de départ et voisins sonores déjà calculés par
     * l'analyse du son. Sans eux, le mix « Dans la lignée de… » est simplement absent.
     */
    fun build(
        songs: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favoriteIds: Set<Long>,
        day: Long,
        similarSeed: Song? = null,
        similar: List<Song> = emptyList(),
    ): List<DailyMix> {
        val pool = songs.filterNot { stats[it.id].isDisliked() }
        if (pool.isEmpty()) return emptyList()
        return buildList {
            add(daily(pool, stats, favoriteIds, day))
            if (similarSeed != null) add(similarMix(similarSeed, similar, stats, day))
            addAll(artistMixes(pool, stats, day))
            add(discovery(pool, stats, day))
        }.filter { it.songs.size >= MIN_SIZE }
    }

    // --- Les mixs ----------------------------------------------------------------------

    private fun daily(
        pool: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favoriteIds: Set<Long>,
        day: Long,
    ): DailyMix {
        val (familiar, fresh) = pool.partition { it.id in favoriteIds || stats.plays(it) >= 2 }
        val random = Random(seed(day, 1))
        val picker = Picker()
        picker.takeFrom(familiar.shuffled(random), FAMILIAR_SHARE)
        picker.takeFrom(fresh.shuffled(random), MIX_SIZE)
        picker.takeFrom(familiar.shuffled(random), MIX_SIZE)
        return DailyMix(
            key = "daily",
            kind = DailyMixKind.DAILY,
            title = "Ton mix du jour",
            subtitle = "Tes classiques et quelques titres à redécouvrir",
            songs = picker.result.shuffled(random).spreadArtists(),
        )
    }

    private fun discovery(pool: List<Song>, stats: Map<Long, PlayStatEntity>, day: Long): DailyMix {
        val picker = Picker(maxPerAlbum = 1)
        picker.takeFrom(pool.filter { stats.plays(it) <= 1 }.shuffled(Random(seed(day, 2))), MIX_SIZE)
        return DailyMix(
            key = "discovery",
            kind = DailyMixKind.DISCOVERY,
            title = "Découverte du jour",
            subtitle = "Des titres de ta bibliothèque que tu n'écoutes presque jamais",
            songs = picker.result.spreadArtists(),
        )
    }

    /** Un mix par artiste parmi les plus écoutés : ses titres, puis son genre. */
    private fun artistMixes(pool: List<Song>, stats: Map<Long, PlayStatEntity>, day: Long): List<DailyMix> {
        // Regroupés par artiste principal : « Grand Corps Malade ft. Suzane » nourrit le
        // mix de Grand Corps Malade au lieu d'en ouvrir un pour ce seul duo.
        val byArtist = pool
            .filter { it.displayArtist != Song.UNKNOWN_ARTIST }
            .groupBy { it.primaryArtist }
        val top = byArtist
            .mapValues { (_, list) -> list.sumOf { stats.plays(it) } }
            .filterValues { it > 0 }
            .entries
            .sortedByDescending { it.value }
            .take(ARTIST_MIXES)
            .map { it.key }

        return top.mapIndexed { index, artist ->
            val random = Random(seed(day, 10 + index))
            val own = byArtist.getValue(artist).shuffled(random).take(ARTIST_OWN_SONGS)
            val genres = own.mapNotNull { it.genre?.takeIf(String::isNotBlank)?.lowercase() }.toSet()
            val others = pool.filter { it.primaryArtist != artist }
            val (sameGenre, rest) = others.partition { it.genre?.lowercase() in genres }

            val picker = Picker()
            picker.result += own
            picker.takeFrom(sameGenre.shuffled(random), MIX_SIZE)
            picker.takeFrom(rest.shuffled(random), MIX_SIZE)
            DailyMix(
                key = "artist-$artist",
                kind = DailyMixKind.ARTIST,
                title = "Mix $artist",
                subtitle = "$artist et des titres dans la même veine",
                songs = picker.result.shuffled(random).spreadArtists(),
            )
        }
    }

    private fun similarMix(
        seed: Song,
        similar: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        day: Long,
    ): DailyMix {
        val picker = Picker()
        picker.result += seed
        picker.takeFrom(similar.filterNot { it.id == seed.id || stats[it.id].isDisliked() }, MIX_SIZE)
        return DailyMix(
            key = "similar-${seed.id}-$day",
            kind = DailyMixKind.SIMILAR,
            title = "Dans la lignée de ${seed.title}",
            subtitle = "Des titres au son proche de ton coup de cœur de la semaine",
            songs = picker.result,
        )
    }

    // --- Outils ------------------------------------------------------------------------

    /** Accumule des titres sans doublon, en limitant chaque artiste (et album, au besoin). */
    private class Picker(private val maxPerAlbum: Int = Int.MAX_VALUE) {
        val result = ArrayList<Song>(MIX_SIZE)

        fun takeFrom(candidates: List<Song>, upTo: Int) {
            for (song in candidates) {
                if (result.size >= upTo || result.size >= MIX_SIZE) return
                if (result.any { it.id == song.id }) continue
                if (result.count { it.displayArtist == song.displayArtist } >= MAX_PER_ARTIST) continue
                if (result.count { it.albumId == song.albumId } >= maxPerAlbum) continue
                result += song
            }
        }
    }

    /**
     * Évite deux titres du même artiste d'affilée quand c'est possible.
     *
     * À chaque place, parmi les titres d'un autre artiste que le précédent, on sert celui
     * dont l'artiste a le plus de titres en attente : le prendre au hasard laissait souvent
     * deux titres du même artiste pour la fin, forcés de se suivre.
     */
    private fun List<Song>.spreadArtists(): List<Song> {
        val remaining = toMutableList()
        val out = ArrayList<Song>(size)
        while (remaining.isNotEmpty()) {
            val last = out.lastOrNull()?.displayArtist
            val waiting = remaining.groupingBy { it.displayArtist }.eachCount()
            val index = remaining.indices
                .filter { remaining[it].displayArtist != last }
                .maxByOrNull { waiting.getValue(remaining[it].displayArtist) }
                ?: 0
            out += remaining.removeAt(index)
        }
        return out
    }

    /** L'artiste avant « feat. », « ft. », « & » ou une virgule. */
    private val Song.primaryArtist: String
        get() = displayArtist.split(COLLAB_SPLIT).first().trim().ifEmpty { displayArtist }

    private val COLLAB_SPLIT = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|&)\s+|,\s*""")

    private fun Map<Long, PlayStatEntity>.plays(song: Song) = this[song.id]?.playCount ?: 0

    /** Même critère que « Souvent zappés » et que l'aléatoire de la bibliothèque. */
    private fun PlayStatEntity?.isDisliked() = this != null && skipCount >= 2 && skipCount > playCount

    private fun seed(day: Long, salt: Int): Long = day * 1_000 + salt
}
