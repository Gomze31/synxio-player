package fr.synxio.player.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeUrlsTest {

    @Test
    fun `les tentatives d injection d option sont refusees`() {
        val attaques = listOf(
            "--config-locations=/sdcard/x#youtube.com/watch",
            "--exec=rm -rf /sdcard #https://youtube.com/watch?v=dQw4w9WgXcQ",
            "-o/sdcard/evil https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "  --batch-file=/sdcard/list youtube.com",
            "-https://youtube.com/watch?v=dQw4w9WgXcQ",
        )
        attaques.forEach {
            assertNull(it, YoutubeUrls.normalize(it))
            assertNull(it, YoutubeUrls.normalizeVideo(it))
        }
    }

    @Test
    fun `les schemas dangereux sont refuses`() {
        val attaques = listOf(
            "javascript:alert(1)//youtube.com/watch?v=dQw4w9WgXcQ",
            "javascript://youtube.com/%0aalert(1)",
            "file:///sdcard/youtube.com/watch",
            "intent://youtube.com/watch?v=dQw4w9WgXcQ#Intent;scheme=https;end",
            "content://youtube.com/watch",
            "data:text/html,youtube.com",
            "ftp://youtube.com/watch?v=dQw4w9WgXcQ",
        )
        attaques.forEach { assertNull(it, YoutubeUrls.normalize(it)) }
    }

    @Test
    fun `les hotes etrangers ou deguises sont refuses`() {
        val attaques = listOf(
            "https://evil.com/youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://evilyoutube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com@evil.com/watch?v=dQw4w9WgXcQ",
            "https://user:pass@youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com:8443/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ --exec x",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ\n--exec",
            "evil.com/watch?v=dQw4w9WgXcQ",
            "",
            "   ",
        )
        attaques.forEach { assertNull(it, YoutubeUrls.normalize(it)) }
        assertNull(YoutubeUrls.normalize(null))
    }

    @Test
    fun `les liens video valides sont acceptes et canonises en https`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            YoutubeUrls.normalizeVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42",
            YoutubeUrls.normalizeVideo("http://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42#comments"),
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            YoutubeUrls.normalizeVideo("  youtu.be/dQw4w9WgXcQ?si=abc  "),
        )
        assertEquals(
            "https://www.youtube.com/shorts/aBcDeFgHiJk",
            YoutubeUrls.normalizeVideo("HTTPS://WWW.YOUTUBE.COM/shorts/aBcDeFgHiJk"),
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM",
            YoutubeUrls.normalizeVideo("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM"),
        )
        assertEquals(
            "https://youtube.com/live/dQw4w9WgXcQ",
            YoutubeUrls.normalizeVideo("youtube.com/live/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `une page YouTube qui n est pas une video n est pas telechargeable`() {
        assertEquals("https://m.youtube.com/", YoutubeUrls.normalize("https://m.youtube.com/"))
        assertNull(YoutubeUrls.normalizeVideo("https://m.youtube.com/"))
        assertNull(YoutubeUrls.normalizeVideo("https://m.youtube.com/results?search_query=watch"))
        assertNull(YoutubeUrls.normalizeVideo("https://www.youtube.com/watch?list=PL123456"))
        assertNull(YoutubeUrls.normalizeVideo("https://youtu.be/"))
    }

    @Test
    fun `la WebView reste sur YouTube en https`() {
        assertTrue(YoutubeUrls.isAllowedInWebView("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YoutubeUrls.isAllowedInWebView("https://consent.youtube.com/m?continue=x"))
        assertTrue(YoutubeUrls.isAllowedInWebView("https://accounts.google.com/ServiceLogin"))
        assertFalse(YoutubeUrls.isAllowedInWebView("http://m.youtube.com/"))
        assertFalse(YoutubeUrls.isAllowedInWebView("https://evil.com/"))
        assertFalse(YoutubeUrls.isAllowedInWebView("https://youtube.com.evil.com/"))
        assertFalse(YoutubeUrls.isAllowedInWebView("intent://watch#Intent;end"))
        assertFalse(YoutubeUrls.isAllowedInWebView("javascript:alert(1)"))
        assertFalse(YoutubeUrls.isAllowedInWebView(null))
    }
}
