package fr.synxio.player.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordWebhookUrlTest {

    @Test
    fun `les webhooks Discord https sont acceptes`() {
        assertTrue(DiscordWebhookUrl.isValid("https://discord.com/api/webhooks/123456789012345678/AbC-dEf_123"))
        assertTrue(DiscordWebhookUrl.isValid("https://discordapp.com/api/webhooks/1/token"))
        assertTrue(DiscordWebhookUrl.isValid("https://ptb.discord.com/api/v10/webhooks/1/token?wait=true"))
        assertTrue(DiscordWebhookUrl.isValid("  https://discord.com/api/webhooks/1/token  "))
    }

    @Test
    fun `tout le reste est refuse`() {
        listOf(
            "http://discord.com/api/webhooks/1/token",
            "https://discord.com.evil.com/api/webhooks/1/token",
            "https://evil.com/api/webhooks/1/token",
            "https://discord.com@evil.com/api/webhooks/1/token",
            "https://user@discord.com/api/webhooks/1/token",
            "https://discord.com:8443/api/webhooks/1/token",
            "https://discord.com/api/webhooks/abc/token",
            "https://discord.com/api/users/@me",
            "https://discord.com/api/webhooks/1/token/../../users",
            "https://discord.com/api/webhooks/1/tok en",
            "javascript:alert(1)",
            "",
        ).forEach { assertFalse(it, DiscordWebhookUrl.isValid(it)) }
    }
}
