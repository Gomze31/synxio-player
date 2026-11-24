package fr.synxio.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDiffTest {

    @Test
    fun `rien ne change - plan vide`() {
        val plan = LibraryDiff.plan(cachedIds = listOf(1, 2, 3), changedIds = emptyList(), currentIds = listOf(1, 2, 3))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `ajout et modification - tous deux en upsert`() {
        val plan = LibraryDiff.plan(cachedIds = listOf(1, 2), changedIds = listOf(2, 9), currentIds = listOf(1, 2, 9))
        assertEquals(listOf(2L, 9L), plan.upserts)
        assertTrue(plan.deletions.isEmpty())
    }

    @Test
    fun `suppression - id en cache absent de MediaStore`() {
        val plan = LibraryDiff.plan(cachedIds = listOf(1, 2, 3), changedIds = emptyList(), currentIds = listOf(1, 3))
        assertEquals(setOf(2L), plan.deletions)
    }

    @Test
    fun `modifie puis supprime entre les deux requetes - la suppression gagne`() {
        val plan = LibraryDiff.plan(cachedIds = listOf(1, 2), changedIds = listOf(2), currentIds = listOf(1))
        assertTrue(plan.upserts.isEmpty())
        assertEquals(setOf(2L), plan.deletions)
    }
}
