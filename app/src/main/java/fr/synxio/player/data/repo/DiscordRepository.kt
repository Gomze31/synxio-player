package fr.synxio.player.data.repo

import android.util.Log
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publication du morceau en cours dans un salon Discord, par webhook.
 *
 * ## Pourquoi un webhook et pas le « Rich Presence »
 *
 * Le statut « Écoute… » du profil Discord passe par une IPC locale avec le client de
 * bureau, qui n'existe pas sur Android. Les applications Android qui l'affichent quand
 * même se connectent à la passerelle Discord avec le **jeton du compte utilisateur** :
 * c'est un self-bot, formellement interdit, passible de suppression définitive du compte.
 *
 * Un webhook est le seul canal officiel qu'une application tierce peut utiliser sans
 * jeton de compte. Il publie dans un salon, ce qui n'est pas un statut de profil — mais
 * c'est légitime, et personne ne risque son compte.
 *
 * L'URL de webhook est un secret : quiconque la détient peut écrire dans le salon. Elle
 * est stockée dans les préférences privées de l'application et n'est jamais journalisée.
 */
@Singleton
class DiscordRepository @Inject constructor(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {

    /** Dernier morceau publié, pour ne pas republier au retour de pause. */
    private var lastAnnouncedSongId: Long = -1L
    private var lastAnnouncedAt: Long = 0L

    /**
     * Publie le morceau si la configuration le permet.
     *
     * Deux garde-fous contre l'inondation du salon : on ne republie jamais le même
     * morceau d'affilée, et jamais plus d'un message par [MIN_INTERVAL_MS]. Sans eux,
     * parcourir sa bibliothèque en sautant des titres enverrait une rafale de messages.
     */
    suspend fun announce(song: Song) = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        if (!current.discordEnabled || current.discordWebhookUrl.isBlank()) return@withContext
        // Une URL qui n'est pas un webhook Discord en HTTPS n'est jamais contactée :
        // le titre en cours ne doit pas partir vers un serveur arbitraire.
        if (!looksLikeWebhook(current.discordWebhookUrl)) return@withContext

        val now = System.currentTimeMillis()
        if (song.id == lastAnnouncedSongId) return@withContext
        if (now - lastAnnouncedAt < MIN_INTERVAL_MS) return@withContext

        val sent = post(current.discordWebhookUrl, nowPlayingPayload(song))
        if (sent) {
            lastAnnouncedSongId = song.id
            lastAnnouncedAt = now
        }
    }

    /** Message de vérification, envoyé depuis les réglages. */
    suspend fun sendTest(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!looksLikeWebhook(url)) {
            return@withContext Result.failure(IllegalArgumentException("URL de webhook Discord invalide"))
        }
        val payload = JSONObject().apply {
            put("content", "Synxio est bien connecté à ce salon.")
            put("username", USERNAME)
        }
        if (post(url, payload)) Result.success(Unit)
        else Result.failure(IllegalStateException("Discord a refusé le message"))
    }

    /**
     * Valide la forme de l'URL avant d'essayer quoi que ce soit.
     * Utilisable par l'écran de réglages pour refuser une saisie invalide.
     */
    fun looksLikeWebhook(url: String): Boolean = DiscordWebhookUrl.isValid(url)

    private fun nowPlayingPayload(song: Song): JSONObject {
        val fields = JSONArray().apply {
            put(field("Artiste", song.displayArtist))
            put(field("Album", song.displayAlbum))
        }

        val embed = JSONObject().apply {
            put("title", song.title)
            put("color", EMBED_COLOR)
            put("fields", fields)
            put(
                "footer",
                JSONObject().put(
                    "text",
                    buildString {
                        append("Synxio")
                        if (song.year > 0) append(" · ${song.year}")
                        append(" · ${song.extension}")
                    },
                )
            )
        }

        return JSONObject().apply {
            put("username", USERNAME)
            put("embeds", JSONArray().put(embed))
        }
    }

    private fun field(name: String, value: String) = JSONObject().apply {
        put("name", name)
        put("value", value.ifBlank { "—" })
        put("inline", true)
    }

    private fun post(url: String, payload: JSONObject): Boolean = runCatching {
        // Dernier verrou : aucune requête ne part vers autre chose qu'un webhook Discord HTTPS.
        if (!looksLikeWebhook(url)) return false
        val request = Request.Builder()
            .url(url.trim())
            .post(payload.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { it.isSuccessful }
    }.getOrElse {
        // L'URL n'est jamais journalisée : elle suffit à écrire dans le salon.
        Log.w(TAG, "Publication Discord impossible", it)
        false
    }

    private companion object {
        const val TAG = "DiscordRepository"
        const val USERNAME = "Synxio"
        /** Violet Synxio, pour la barre latérale de l'embed. */
        const val EMBED_COLOR = 0x6C5CE7
        const val MIN_INTERVAL_MS = 20_000L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Validation pure (JVM) d'une URL de webhook Discord.
 *
 * Exige HTTPS, un hôte Discord officiel, aucun identifiant ni port explicite, et un
 * chemin `/api/webhooks/<id>/<jeton>` (éventuellement versionné : `/api/v10/webhooks/…`).
 */
object DiscordWebhookUrl {
    private val HOSTS = setOf(
        "discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com",
    )
    private val PATH = Regex("""^/api(/v\d+)?/webhooks/\d+/[A-Za-z0-9_-]+/?$""")

    fun isValid(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (host !in HOSTS) return false
        if (uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443)) return false
        if (uri.rawFragment != null) return false
        return PATH.matches(uri.rawPath ?: return false)
    }
}
