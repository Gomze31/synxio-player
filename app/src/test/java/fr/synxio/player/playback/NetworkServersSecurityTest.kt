package fr.synxio.player.playback

import fr.synxio.player.playback.CastMediaServer.RangeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkServersSecurityTest {

    // --- Synxio Party : jeton ---

    @Test
    fun `le jeton Party est aleatoire et URL-safe`() {
        val a = PartyServer.newToken()
        val b = PartyServer.newToken()
        assertNotEquals(a, b)
        assertEquals(22, a.length)
        assertTrue(a, a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun `seules les URL portant le jeton sont servies`() {
        val token = "AbCdEfGhIjKlMnOpQrStUv"
        assertEquals(PartyServer.Route.PAGE, PartyServer.route("/$token/", token))
        assertEquals(PartyServer.Route.PAGE, PartyServer.route("/$token", token))
        assertEquals(PartyServer.Route.STATUS, PartyServer.route("/$token/api/status", token))

        listOf(
            "/", "", "/api/status", "/index.html",
            "/wrong/api/status", "/${token}x/", "/${token.dropLast(1)}/",
            "/$token/api/status/extra", "/$token/../api/status", "/$token/other",
        ).forEach { assertEquals(it, PartyServer.Route.NOT_FOUND, PartyServer.route(it, token)) }
        assertEquals(PartyServer.Route.NOT_FOUND, PartyServer.route(null, token))
        assertEquals(PartyServer.Route.NOT_FOUND, PartyServer.route("/", ""))
    }

    // --- Cast : en-tête Range ---

    @Test
    fun `plages Range valides`() {
        assertEquals(RangeResult.Full, CastMediaServer.parseRange(null, 1000))
        assertEquals(RangeResult.Partial(0, 999), CastMediaServer.parseRange("bytes=0-", 1000))
        assertEquals(RangeResult.Partial(100, 199), CastMediaServer.parseRange("bytes=100-199", 1000))
        assertEquals(RangeResult.Partial(100, 999), CastMediaServer.parseRange("bytes=100-5000", 1000))
        assertEquals(RangeResult.Partial(900, 999), CastMediaServer.parseRange("bytes=-100", 1000))
        assertEquals(RangeResult.Partial(0, 999), CastMediaServer.parseRange("bytes=-5000", 1000))
    }

    @Test
    fun `plages Range incoherentes donnent 416`() {
        assertEquals(RangeResult.Unsatisfiable, CastMediaServer.parseRange("bytes=500-100", 1000))
        assertEquals(RangeResult.Unsatisfiable, CastMediaServer.parseRange("bytes=1000-", 1000))
        assertEquals(RangeResult.Unsatisfiable, CastMediaServer.parseRange("bytes=abc-def", 1000))
        assertEquals(RangeResult.Unsatisfiable, CastMediaServer.parseRange("bytes=-0", 1000))
        assertEquals(RangeResult.Unsatisfiable, CastMediaServer.parseRange("bytes=100", 1000))
    }
}
