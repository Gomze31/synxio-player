package fr.synxio.player.data.media

import fr.synxio.player.data.media.StorageTreePaths.DOWNLOADS_AUTHORITY
import fr.synxio.player.data.media.StorageTreePaths.EXTERNAL_STORAGE_AUTHORITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageTreePathsTest {

    private fun external(id: String?) = StorageTreePaths.pathOf(EXTERNAL_STORAGE_AUTHORITY, id)

    @Test
    fun `stockage interne`() {
        assertEquals("/storage/emulated/0/Livres audio", external("primary:Livres audio"))
        assertEquals("/storage/emulated/0/Livres/Tome 1", external("primary:Livres/Tome 1"))
    }

    @Test
    fun `seul le premier deux-points separe le volume`() {
        assertEquals("/storage/emulated/0/Saga: tome 1", external("primary:Saga: tome 1"))
        assertEquals("/storage/emulated/0/a:b:c", external("primary:a:b:c"))
    }

    @Test
    fun `carte SD`() {
        assertEquals("/storage/1A2B-3C4D/Livres", external("1A2B-3C4D:Livres"))
    }

    @Test
    fun `racine du volume et barre finale`() {
        assertEquals("/storage/emulated/0", external("primary:"))
        assertEquals("/storage/emulated/0/Livres", external("primary:Livres/"))
    }

    @Test
    fun `identifiants invalides refuses`() {
        assertNull(external(null))
        assertNull(external(""))
        assertNull(external("Livres"))
        assertNull(external(":Livres"))
    }

    @Test
    fun `telechargements - chemin brut seulement`() {
        assertEquals(
            "/storage/emulated/0/Download/Livres",
            StorageTreePaths.pathOf(DOWNLOADS_AUTHORITY, "raw:/storage/emulated/0/Download/Livres/"),
        )
        assertNull(StorageTreePaths.pathOf(DOWNLOADS_AUTHORITY, "msd:42"))
        assertNull(StorageTreePaths.pathOf(DOWNLOADS_AUTHORITY, "downloads"))
    }

    @Test
    fun `fournisseur inconnu refuse`() {
        assertNull(StorageTreePaths.pathOf("com.google.android.apps.docs.storage", "primary:Livres"))
    }
}
