package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPlannerTest {

    private val dims = 27

    @Test
    fun `tout est a jour - plan vide`() {
        val s = song(id = 1, dateAddedSec = 100)
        val plan = AnalysisPlanner.plan(
            songs = listOf(s),
            loudnessModified = mapOf(s.path to 100L),
            featureModified = mapOf(s.path to 100L),
            featureDimensions = mapOf(s.path to dims),
            expectedDimensions = dims,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `fichier modifie - niveau et empreinte a refaire`() {
        val s = song(id = 1, dateAddedSec = 200)
        val plan = AnalysisPlanner.plan(
            songs = listOf(s),
            loudnessModified = mapOf(s.path to 100L),
            featureModified = mapOf(s.path to 100L),
            featureDimensions = mapOf(s.path to dims),
            expectedDimensions = dims,
        )
        val item = plan.single()
        assertTrue(item.needsLoudness)
        assertTrue(item.needsFeatures)
    }

    @Test
    fun `dimension obsolete - empreinte seule a refaire`() {
        val s = song(id = 1, dateAddedSec = 100)
        val plan = AnalysisPlanner.plan(
            songs = listOf(s),
            loudnessModified = mapOf(s.path to 100L),
            featureModified = mapOf(s.path to 100L),
            featureDimensions = mapOf(s.path to 20),
            expectedDimensions = dims,
        )
        val item = plan.single()
        assertFalse(item.needsLoudness)
        assertTrue(item.needsFeatures)
    }

    @Test
    fun `priorite - les chemins demandes passent en tete, ordre conserve ensuite`() {
        val songs = listOf(song(id = 1), song(id = 2), song(id = 3))
        val plan = AnalysisPlanner.plan(
            songs = songs,
            loudnessModified = emptyMap(),
            featureModified = emptyMap(),
            featureDimensions = emptyMap(),
            expectedDimensions = dims,
            priorityPaths = setOf(songs[2].path),
        )
        assertEquals(listOf(3L, 1L, 2L), plan.map { it.song.id })
    }

    @Test
    fun `podcast - niveau oui, empreinte non`() {
        val p = song(id = 1).copy(isPodcast = true)
        val plan = AnalysisPlanner.plan(
            songs = listOf(p),
            loudnessModified = emptyMap(),
            featureModified = emptyMap(),
            featureDimensions = emptyMap(),
            expectedDimensions = dims,
        )
        val item = plan.single()
        assertTrue(item.needsLoudness)
        assertFalse(item.needsFeatures)
    }
}
