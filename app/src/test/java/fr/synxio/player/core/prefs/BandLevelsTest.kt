package fr.synxio.player.core.prefs

import kotlin.test.Test
import kotlin.test.assertEquals

class BandLevelsTest {

    @Test
    fun `decode la chaine stockee`() {
        assertEquals(listOf<Short>(-300, 0, 150), "-300,0,150".toBandLevels())
        assertEquals(listOf<Short>(1, 2), " 1 , 2 ".toBandLevels())
    }

    @Test
    fun `ignore les valeurs invalides et le vide`() {
        assertEquals(emptyList(), "".toBandLevels())
        assertEquals(emptyList(), "   ".toBandLevels())
        assertEquals(listOf<Short>(5), "abc,5,99999".toBandLevels())
    }
}
