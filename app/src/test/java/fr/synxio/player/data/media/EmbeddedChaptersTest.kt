package fr.synxio.player.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class EmbeddedChaptersTest {

    // --- Fabrication de fichiers minimaux ----------------------------------------------

    private fun ByteArrayOutputStream.u8(v: Int) = apply { write(v and 0xFF) }
    private fun ByteArrayOutputStream.u16(v: Int) = apply { u8(v shr 8); u8(v) }
    private fun ByteArrayOutputStream.u32(v: Long) = apply { u16((v shr 16).toInt()); u16(v.toInt()) }
    private fun ByteArrayOutputStream.u64(v: Long) = apply { u32(v ushr 32); u32(v and 0xFFFFFFFFL) }
    private fun ByteArrayOutputStream.bytes(b: ByteArray) = apply { write(b) }
    private fun ByteArrayOutputStream.ascii(s: String) = apply { write(s.toByteArray(Charsets.ISO_8859_1)) }
    private fun build(block: ByteArrayOutputStream.() -> Unit) = ByteArrayOutputStream().apply(block).toByteArray()

    private fun atom(type: String, vararg children: ByteArray): ByteArray {
        val body = children.fold(ByteArray(0)) { acc, b -> acc + b }
        return build { u32(8L + body.size); ascii(type); bytes(body) }
    }

    private val ftyp = atom("ftyp", build { ascii("M4B "); u32(0) })

    private fun chpl(vararg chapters: Pair<Long, String>) = atom("chpl", build {
        u8(1); u8(0); u16(0) // version 1, drapeaux
        u32(0) // réservé
        u8(chapters.size)
        chapters.forEach { (startMs, title) ->
            val t = title.toByteArray(Charsets.UTF_8)
            u64(startMs * 10_000); u8(t.size); bytes(t)
        }
    })

    private fun tkhd(id: Long) = atom("tkhd", build { u32(0); u32(0); u32(0); u32(id); u32(0); u32(0) })

    /** Fichier MP4 avec une piste audio qui renvoie à une piste de chapitres QuickTime. */
    private fun quickTimeFile(titles: List<String>, durationsMs: List<Long>): ByteArray {
        val samples = titles.map { t ->
            val b = t.toByteArray(Charsets.UTF_8)
            build { u16(b.size); bytes(b) }
        }
        val mdatHeader = 8
        val mdat = atom("mdat", *samples.toTypedArray())
        val firstSample = ftyp.size + mdatHeader

        val audio = atom("trak", tkhd(1), atom("tref", atom("chap", build { u32(2) })))
        val stbl = atom(
            "stbl",
            atom("stts", build {
                u32(0); u32(durationsMs.size.toLong())
                durationsMs.forEach { u32(1); u32(it) }
            }),
            atom("stsz", build {
                u32(0); u32(0); u32(samples.size.toLong())
                samples.forEach { u32(it.size.toLong()) }
            }),
            atom("stsc", build { u32(0); u32(1); u32(1); u32(samples.size.toLong()); u32(1) }),
            atom("stco", build { u32(0); u32(1); u32(firstSample.toLong()) }),
        )
        val text = atom(
            "trak",
            tkhd(2),
            atom(
                "mdia",
                atom("mdhd", build { u32(0); u32(0); u32(0); u32(1000); u32(0); u32(0) }),
                atom("hdlr", build { u32(0); u32(0); ascii("text"); u32(0); u32(0); u32(0) }),
                atom("minf", stbl),
            ),
        )
        return ftyp + mdat + atom("moov", audio, text)
    }

    private fun syncSafe(v: Int) = build { u8(v shr 21 and 0x7F); u8(v shr 14 and 0x7F); u8(v shr 7 and 0x7F); u8(v and 0x7F) }

    private fun id3Frame(id: String, body: ByteArray, major: Int) = build {
        ascii(id)
        if (major == 4) bytes(syncSafe(body.size)) else u32(body.size.toLong())
        u16(0)
        bytes(body)
    }

    private fun chap(id: String, startMs: Long, endMs: Long, title: String, major: Int) = id3Frame(
        "CHAP",
        build {
            ascii(id); u8(0)
            u32(startMs); u32(endMs); u32(0xFFFFFFFFL); u32(0xFFFFFFFFL)
            bytes(id3Frame("TIT2", build { u8(3); bytes(title.toByteArray(Charsets.UTF_8)) }, major))
        },
        major,
    )

    private fun id3File(major: Int, vararg frames: ByteArray): ByteArray {
        val body = frames.fold(ByteArray(0)) { acc, b -> acc + b } + ByteArray(16) // remplissage
        return build { ascii("ID3"); u8(major); u8(0); u8(0); bytes(syncSafe(body.size)); bytes(body) } +
            ByteArray(64) { 0x55 } // « audio »
    }

    private fun read(bytes: ByteArray, durationMs: Long) = EmbeddedChapters.read(ByteArraySource(bytes), durationMs)

    // --- Tests ---------------------------------------------------------------------------

    @Test
    fun `m4b - piste de chapitres QuickTime`() {
        val file = quickTimeFile(listOf("Prologue", "Chapitre un", "Épilogue"), listOf(60_000, 120_000, 30_000))
        val chapters = read(file, durationMs = 210_000)
        assertEquals(listOf("Prologue", "Chapitre un", "Épilogue"), chapters.map { it.title })
        assertEquals(listOf(0L, 60_000L, 180_000L), chapters.map { it.startMs })
        assertEquals(listOf(60_000L, 180_000L, 210_000L), chapters.map { it.endMs })
    }

    @Test
    fun `m4b - atome Nero chpl`() {
        val file = ftyp + atom("moov", atom("udta", chpl(0L to "Un", 90_000L to "Deux", 200_000L to "")))
        val chapters = read(file, durationMs = 300_000)
        assertEquals(listOf("Un", "Deux", "Chapitre 3"), chapters.map { it.title })
        assertEquals(listOf(0L, 90_000L, 200_000L), chapters.map { it.startMs })
        assertEquals(100_000L, chapters[2].durationMs)
    }

    @Test
    fun `mp3 - trames CHAP en ID3 v2_3 et v2_4`() {
        for (major in listOf(3, 4)) {
            val file = id3File(
                major,
                // Dans le désordre : la lecture les remet dans l'ordre du temps.
                chap("ch2", 50_000, 100_000, "Deuxième", major),
                chap("ch1", 0, 50_000, "Premier", major),
            )
            val chapters = read(file, durationMs = 100_000)
            assertEquals("v2.$major", listOf("Premier", "Deuxième"), chapters.map { it.title })
            assertEquals(listOf(0L, 50_000L), chapters.map { it.startMs })
        }
    }

    @Test
    fun `un seul chapitre ou aucun - rien a naviguer`() {
        assertTrue(read(ftyp + atom("moov", atom("udta", chpl(0L to "Seul"))), 60_000).isEmpty())
        assertTrue(read(ftyp + atom("moov"), 60_000).isEmpty())
        assertTrue(read(id3File(3), 60_000).isEmpty())
    }

    @Test
    fun `fichier inconnu, tronque ou corrompu - liste vide sans exception`() {
        assertTrue(read(ByteArray(0), 60_000).isEmpty())
        assertTrue(read("RIFF....WAVEfmt ".toByteArray(), 60_000).isEmpty())
        val file = quickTimeFile(listOf("A", "B"), listOf(1_000, 1_000))
        assertTrue(read(file.copyOf(file.size - 20), 2_000).isEmpty())
        assertTrue(read(ftyp + build { u32(0xFFFFFFF0L); ascii("moov") }, 60_000).isEmpty())
    }

    @Test
    fun `debuts hors du fichier et doublons ecartes`() {
        val file = ftyp + atom("moov", atom("udta", chpl(0L to "A", 0L to "A bis", 30_000L to "B", 90_000L to "Hors")))
        val chapters = read(file, durationMs = 60_000)
        assertEquals(listOf("A", "B"), chapters.map { it.title })
        assertEquals(60_000L, chapters.last().endMs)
    }
}
