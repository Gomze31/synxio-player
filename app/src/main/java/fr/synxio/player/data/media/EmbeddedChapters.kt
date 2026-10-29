package fr.synxio.player.data.media

import androidx.compose.runtime.Immutable
import java.nio.charset.Charset

/** Chapitre interne à un fichier audio, bornes en millisecondes depuis le début du fichier. */
@Immutable
data class Chapter(val title: String, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** Accès aléatoire en lecture à un fichier, ou à un tableau d'octets dans les tests. */
interface ByteSource {
    val size: Long

    /** Jusqu'à [length] octets à partir de [position] ; moins en fin de source. */
    fun read(position: Long, length: Int): ByteArray
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()
    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || position >= bytes.size) return ByteArray(0)
        val end = (position + length).coerceAtMost(bytes.size.toLong()).toInt()
        return bytes.copyOfRange(position.toInt(), end)
    }
}

/**
 * Lecture des chapitres intégrés à un livre audio en un seul fichier.
 *
 * - MP4 / M4B : piste de chapitres QuickTime (`tref/chap`, le format d'iTunes et
 *   d'Audible), sinon atome Nero `moov/udta/chpl`.
 * - MP3 : trames ID3v2.3 / v2.4 `CHAP`, titre dans la sous-trame `TIT2`.
 *
 * Seuls les en-têtes sont lus, jamais l'audio. Un fichier illisible ou sans chapitres
 * donne une liste vide, comme un fichier d'un seul chapitre : il n'y a rien à naviguer.
 */
object EmbeddedChapters {

    /** Au-delà, un atome ou une balise n'est pas lu en entier : fichier suspect. */
    private const val MAX_BLOCK_BYTES = 8 * 1024 * 1024

    fun read(source: ByteSource, durationMs: Long): List<Chapter> {
        val raw = runCatching {
            val head = source.read(0, 12)
            when {
                head.size >= 10 && head.ascii(0, 3) == "ID3" -> readId3(source)
                head.size >= 8 && head.ascii(4, 4) == "ftyp" -> readMp4(source)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        return normalize(raw, durationMs)
    }

    /**
     * Trie, écarte les débuts hors du fichier et les doublons, recalcule les fins sur le
     * début suivant (ou la durée du fichier), nomme les chapitres sans titre.
     */
    internal fun normalize(raw: List<RawChapter>, durationMs: Long): List<Chapter> {
        val starts = raw
            .filter { it.startMs >= 0 && (durationMs <= 0 || it.startMs < durationMs) }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
        if (starts.size < 2) return emptyList()
        return starts.mapIndexed { i, c ->
            val next = starts.getOrNull(i + 1)?.startMs
            val end = next ?: when {
                durationMs > 0 -> durationMs
                c.endMs != null && c.endMs > c.startMs -> c.endMs
                else -> c.startMs
            }
            Chapter(title = c.title.trim().ifBlank { "Chapitre ${i + 1}" }, startMs = c.startMs, endMs = end)
        }
    }

    internal data class RawChapter(val title: String, val startMs: Long, val endMs: Long? = null)

    // --- MP4 -----------------------------------------------------------------------------

    private class Atom(val type: String, val start: Long, val headerSize: Int, val size: Long) {
        val contentStart: Long get() = start + headerSize
        val end: Long get() = start + size
    }

    private fun atomsIn(source: ByteSource, from: Long, to: Long): List<Atom> {
        val atoms = ArrayList<Atom>()
        var pos = from
        while (pos + 8 <= to) {
            val h = source.read(pos, 16)
            if (h.size < 8) break
            var size = h.u32(0)
            val type = h.ascii(4, 4)
            var header = 8
            if (size == 1L) {
                if (h.size < 16) break
                size = h.u64(8)
                header = 16
            } else if (size == 0L) {
                size = to - pos
            }
            if (size < header || pos + size > to) break
            atoms += Atom(type, pos, header, size)
            pos += size
        }
        return atoms
    }

    private fun child(source: ByteSource, parent: Atom, type: String, skip: Int = 0): Atom? =
        atomsIn(source, parent.contentStart + skip, parent.end).firstOrNull { it.type == type }

    private fun content(source: ByteSource, atom: Atom): ByteArray {
        val length = atom.size - atom.headerSize
        require(length in 0..MAX_BLOCK_BYTES)
        return source.read(atom.contentStart, length.toInt())
    }

    private fun readMp4(source: ByteSource): List<RawChapter> {
        val moov = atomsIn(source, 0, source.size).firstOrNull { it.type == "moov" } ?: return emptyList()
        val children = atomsIn(source, moov.contentStart, moov.end)
        val quickTime = readQuickTimeChapters(source, children.filter { it.type == "trak" })
        if (quickTime.size >= 2) return quickTime
        val udta = children.firstOrNull { it.type == "udta" } ?: return quickTime
        val chpl = child(source, udta, "chpl") ?: return quickTime
        return readChpl(content(source, chpl))
    }

    /** Atome Nero : départs en unités de 100 ns, titres préfixés de leur longueur. */
    private fun readChpl(data: ByteArray): List<RawChapter> {
        if (data.size < 5) return emptyList()
        val version = data[0].toInt() and 0xFF
        var pos = if (version == 0) 4 else 8
        if (pos >= data.size) return emptyList()
        val count = data[pos].toInt() and 0xFF
        pos += 1
        val chapters = ArrayList<RawChapter>(count)
        repeat(count) {
            if (pos + 9 > data.size) return chapters
            val start100ns = data.u64(pos)
            val length = data[pos + 8].toInt() and 0xFF
            pos += 9
            if (pos + length > data.size) return chapters
            chapters += RawChapter(String(data, pos, length, Charsets.UTF_8), start100ns / 10_000)
            pos += length
        }
        return chapters
    }

    private class TrackInfo(
        val id: Long,
        val chapterTrackIds: List<Long>,
        val handler: String,
        val timescale: Long,
        val stbl: Atom?,
    )

    private fun trackInfo(source: ByteSource, trak: Atom): TrackInfo? {
        val tkhd = child(source, trak, "tkhd")?.let { content(source, it) } ?: return null
        val id = if ((tkhd[0].toInt() and 0xFF) == 1) tkhd.u32(20) else tkhd.u32(12)
        val chapterIds = child(source, trak, "tref")
            ?.let { child(source, it, "chap") }
            ?.let { content(source, it) }
            ?.let { bytes -> (0 until bytes.size / 4).map { bytes.u32(it * 4) } }
            .orEmpty()
        val mdia = child(source, trak, "mdia")
        val mdhd = mdia?.let { child(source, it, "mdhd") }?.let { content(source, it) }
        val timescale = when {
            mdhd == null -> 0L
            (mdhd[0].toInt() and 0xFF) == 1 -> mdhd.u32(20)
            else -> mdhd.u32(12)
        }
        val handler = mdia?.let { child(source, it, "hdlr") }?.let { content(source, it) }
            ?.takeIf { it.size >= 12 }?.ascii(8, 4).orEmpty()
        val stbl = mdia?.let { child(source, it, "minf") }?.let { child(source, it, "stbl") }
        return TrackInfo(id, chapterIds, handler, timescale, stbl)
    }

    private fun readQuickTimeChapters(source: ByteSource, traks: List<Atom>): List<RawChapter> {
        val tracks = traks.mapNotNull { trackInfo(source, it) }
        val chapterIds = tracks.flatMap { it.chapterTrackIds }.toSet()
        val track = tracks.firstOrNull { it.id in chapterIds && it.handler in setOf("text", "sbtl") }
            ?: return emptyList()
        val stbl = track.stbl ?: return emptyList()
        if (track.timescale <= 0) return emptyList()

        val stts = child(source, stbl, "stts")?.let { content(source, it) } ?: return emptyList()
        val stsz = child(source, stbl, "stsz")?.let { content(source, it) } ?: return emptyList()
        val stsc = child(source, stbl, "stsc")?.let { content(source, it) } ?: return emptyList()
        val co64 = child(source, stbl, "co64")
        val chunkOffsets: List<Long> = if (co64 != null) {
            val b = content(source, co64)
            (0 until b.u32(4).toInt()).map { b.u64(8 + it * 8) }
        } else {
            val b = child(source, stbl, "stco")?.let { content(source, it) } ?: return emptyList()
            (0 until b.u32(4).toInt()).map { b.u32(8 + it * 4) }
        }

        // Instants de départ de chaque échantillon (un échantillon = un chapitre).
        val startTicks = ArrayList<Long>()
        var tick = 0L
        for (i in 0 until stts.u32(4).toInt()) {
            val count = stts.u32(8 + i * 8)
            val delta = stts.u32(12 + i * 8)
            for (n in 0 until count) {
                startTicks += tick
                tick += delta
            }
        }

        val uniformSize = stsz.u32(4)
        val sampleCount = stsz.u32(8).toInt()
        val sizes = (0 until sampleCount).map { if (uniformSize != 0L) uniformSize else stsz.u32(12 + it * 4) }

        // Échantillons par bloc : chaque entrée vaut jusqu'à la suivante.
        val stscEntries = (0 until stsc.u32(4).toInt()).map { stsc.u32(8 + it * 12) to stsc.u32(12 + it * 12) }
        val offsets = ArrayList<Long>(sampleCount)
        chunkOffsets.forEachIndexed { chunkIndex, chunkOffset ->
            val chunkNumber = chunkIndex + 1L
            val perChunk = stscEntries.lastOrNull { it.first <= chunkNumber }?.second ?: 1L
            var pos = chunkOffset
            for (n in 0 until perChunk) {
                if (offsets.size >= sampleCount) break
                offsets += pos
                pos += sizes[offsets.size - 1]
            }
        }

        return offsets.indices.mapNotNull { i ->
            val start = startTicks.getOrNull(i) ?: return@mapNotNull null
            val sample = source.read(offsets[i], sizes[i].toInt().coerceAtMost(4096))
            RawChapter(decodeTextSample(sample), start * 1000 / track.timescale)
        }
    }

    /** Échantillon de texte QuickTime : longueur sur 2 octets, puis UTF-8 ou UTF-16 (BOM). */
    private fun decodeTextSample(sample: ByteArray): String {
        if (sample.size < 2) return ""
        val length = ((sample[0].toInt() and 0xFF) shl 8) or (sample[1].toInt() and 0xFF)
        val end = (2 + length).coerceAtMost(sample.size)
        val text = sample.copyOfRange(2, end)
        return if (text.size >= 2 && text[0] == 0xFE.toByte() && text[1] == 0xFF.toByte()) {
            String(text, Charsets.UTF_16)
        } else {
            String(text, Charsets.UTF_8)
        }
    }

    // --- ID3 -----------------------------------------------------------------------------

    private fun readId3(source: ByteSource): List<RawChapter> {
        val header = source.read(0, 10)
        val major = header[3].toInt() and 0xFF
        if (major != 3 && major != 4) return emptyList()
        val flags = header[5].toInt() and 0xFF
        // Désynchronisation de toute la balise (v2.3) : les octets seraient à décoder un à un.
        if (major == 3 && flags and 0x80 != 0) return emptyList()
        val tagSize = header.syncSafe(6)
        require(tagSize in 0..MAX_BLOCK_BYTES)
        val tag = source.read(10, tagSize.toInt())

        var pos = 0
        if (flags and 0x40 != 0 && tag.size >= 4) {
            pos = if (major == 4) tag.syncSafe(0).toInt() else (tag.u32(0) + 4).toInt()
        }
        return frames(tag, pos, tag.size, major)
            .filter { it.first == "CHAP" }
            .mapNotNull { (_, body) -> parseChap(body, major) }
    }

    /** Trames (identifiant, contenu) entre [from] et [to]. */
    private fun frames(data: ByteArray, from: Int, to: Int, major: Int): List<Pair<String, ByteArray>> {
        val result = ArrayList<Pair<String, ByteArray>>()
        var pos = from
        while (pos + 10 <= to) {
            if (data[pos] == 0.toByte()) break // remplissage
            val id = data.ascii(pos, 4)
            val size = (if (major == 4) data.syncSafe(pos + 4) else data.u32(pos + 4)).toInt()
            val bodyStart = pos + 10
            if (size < 0 || bodyStart + size > to) break
            result += id to data.copyOfRange(bodyStart, bodyStart + size)
            pos = bodyStart + size
        }
        return result
    }

    private fun parseChap(body: ByteArray, major: Int): RawChapter? {
        val idEnd = body.indexOf(0.toByte())
        if (idEnd < 0 || idEnd + 17 > body.size) return null
        val start = body.u32(idEnd + 1)
        val end = body.u32(idEnd + 5)
        val title = frames(body, idEnd + 17, body.size, major)
            .firstOrNull { it.first == "TIT2" }
            ?.second
            ?.let(::decodeId3Text)
            .orEmpty()
        return RawChapter(title, start, end.takeIf { it != 0xFFFFFFFFL && it > start })
    }

    private fun decodeId3Text(body: ByteArray): String {
        if (body.isEmpty()) return ""
        val charset: Charset = when (body[0].toInt()) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(body, 1, body.size - 1, charset).trimEnd('\u0000')
    }

    // --- Octets --------------------------------------------------------------------------

    private fun ByteArray.ascii(at: Int, length: Int): String =
        if (at + length > size) "" else String(this, at, length, Charsets.ISO_8859_1)

    private fun ByteArray.u32(at: Int): Long {
        if (at + 4 > size) return 0L
        return ((this[at].toLong() and 0xFF) shl 24) or ((this[at + 1].toLong() and 0xFF) shl 16) or
            ((this[at + 2].toLong() and 0xFF) shl 8) or (this[at + 3].toLong() and 0xFF)
    }

    private fun ByteArray.u64(at: Int): Long = (u32(at) shl 32) or u32(at + 4)

    private fun ByteArray.syncSafe(at: Int): Long {
        if (at + 4 > size) return 0L
        return ((this[at].toLong() and 0x7F) shl 21) or ((this[at + 1].toLong() and 0x7F) shl 14) or
            ((this[at + 2].toLong() and 0x7F) shl 7) or (this[at + 3].toLong() and 0x7F)
    }
}
