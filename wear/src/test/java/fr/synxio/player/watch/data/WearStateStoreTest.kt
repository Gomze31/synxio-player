package fr.synxio.player.watch.data

import fr.synxio.player.wear.protocol.WearState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WearStateStoreTest {

    @get:Rule val folder = TemporaryFolder()

    private val s42 = WearState(mediaId = "42", title = "Titre", capturedAtMs = 1_000)
    private val s43 = WearState(mediaId = "43", title = "Autre", capturedAtMs = 2_000)

    @Test
    fun `vide au depart`() {
        val store = WearStateStore(folder.newFolder())
        store.load()
        assertNull(store.current().state)
        assertNull(store.current().art)
    }

    @Test
    fun `save puis current rend l etat et les images`() {
        val store = WearStateStore(folder.newFolder())
        store.save(s42, byteArrayOf(1), byteArrayOf(2))
        assertEquals(s42, store.current().state)
        assertArrayEquals(byteArrayOf(1), store.current().art)
        assertArrayEquals(byteArrayOf(2), store.current().artBlurred)
        assertEquals(s42, store.stored.value.state)
    }

    @Test
    fun `sans image et meme titre, les images precedentes restent`() {
        val store = WearStateStore(folder.newFolder())
        store.save(s42, byteArrayOf(1), byteArrayOf(2))
        store.save(s42.copy(isPlaying = true), null, null)
        assertArrayEquals(byteArrayOf(1), store.current().art)
    }

    @Test
    fun `sans image et titre different, les images sont effacees`() {
        val store = WearStateStore(folder.newFolder())
        store.save(s42, byteArrayOf(1), byteArrayOf(2))
        store.save(s43, null, null)
        assertNull(store.current().art)
        assertNull(store.current().artBlurred)
    }

    @Test
    fun `un etat vide efface les images`() {
        val store = WearStateStore(folder.newFolder())
        store.save(s42, byteArrayOf(1), byteArrayOf(2))
        store.save(WearState(), null, null)
        assertNull(store.current().art)
    }

    @Test
    fun `load relit ce qu un autre store a ecrit`() {
        val dir = folder.newFolder()
        WearStateStore(dir).save(s42, byteArrayOf(1), byteArrayOf(2))
        val other = WearStateStore(dir)
        other.load()
        assertEquals(s42, other.current().state)
        assertArrayEquals(byteArrayOf(1), other.current().art)
        assertArrayEquals(byteArrayOf(2), other.current().artBlurred)
    }

    @Test
    fun `un fichier d etat illisible donne un store vide`() {
        val dir = folder.newFolder()
        java.io.File(dir, "state.json").writeText("{cassé")
        val store = WearStateStore(dir)
        store.load()
        assertNull(store.current().state)
    }
}
