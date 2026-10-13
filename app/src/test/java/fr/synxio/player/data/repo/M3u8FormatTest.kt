package fr.synxio.player.data.repo

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3u8FormatTest {

    private val song1 = song(id = 1, title = "Titre 1", artist = "Artiste 1", path = "/storage/emulated/0/Music/A/song1.mp3")
    private val song2 = song(id = 2, title = "Titre 2", artist = "Artiste 2", path = "/storage/emulated/0/Music/B/song2.flac")

    @Test
    fun `export puis lecture redonne les memes titres dans le meme ordre`() {
        val text = M3u8Format.write("Ma playlist", listOf(song2, song1))
        assertTrue(text.startsWith("#EXTM3U\n"))

        val parsed = M3u8Format.parse(text)
        assertEquals("Ma playlist", parsed.name)
        assertEquals(listOf(song2.path, song1.path), parsed.entries)
        assertEquals(listOf(song2, song1), M3u8Format.match(parsed.entries, listOf(song1, song2)))
    }

    @Test
    fun `un retour a la ligne dans un titre ne casse pas le fichier`() {
        val tricky = song(id = 3, title = "Ligne 1\nLigne 2", path = "/music/x.mp3")
        val parsed = M3u8Format.parse(M3u8Format.write("Nom\r\ncasse", listOf(tricky)))
        assertEquals("Nom  casse", parsed.name)
        assertEquals(listOf("/music/x.mp3"), parsed.entries)
    }

    @Test
    fun `BOM, fins de ligne Windows et separateurs inverses sont toleres`() {
        val text = "﻿#EXTM3U\r\n#EXTINF:12,X - Y\r\nMusic\\A\\song1.mp3\r\n\r\n"
        val parsed = M3u8Format.parse(text)
        assertNull(parsed.name)
        assertEquals(listOf("Music/A/song1.mp3"), parsed.entries)
        assertEquals(listOf(song1), M3u8Format.match(parsed.entries, listOf(song1, song2)))
    }

    @Test
    fun `les URI file sont decodees`() {
        val parsed = M3u8Format.parse("file:///storage/Mon%20Dossier/a+b.mp3\nfile:/music/c.mp3")
        assertEquals(listOf("/storage/Mon Dossier/a+b.mp3", "/music/c.mp3"), parsed.entries)
    }

    @Test
    fun `une racine differente retrouve le fichier par ses dossiers finaux`() {
        // Même nom de fichier dans deux dossiers : le dossier parent départage.
        val twinA = song(id = 10, path = "/storage/emulated/0/Music/A/intro.mp3")
        val twinB = song(id = 11, path = "/storage/emulated/0/Music/B/intro.mp3")
        val matched = M3u8Format.match(listOf("/sdcard/Music/B/INTRO.mp3"), listOf(twinA, twinB))
        assertEquals(listOf(twinB), matched)
    }

    @Test
    fun `les entrees absentes de la bibliotheque sont ignorees`() {
        val matched = M3u8Format.match(listOf("/ailleurs/inconnu.mp3", song1.path), listOf(song1, song2))
        assertEquals(listOf(song1), matched)
    }
}
