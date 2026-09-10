package fr.synxio.player.data.lastfm

import android.util.Log
import fr.synxio.player.BuildConfig
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrobbling Last.fm (API 2.0).
 *
 * Règle officielle : un morceau compte quand il a été écouté au-delà de la moitié
 * de sa durée ou plus de 4 minutes, et qu'il dure au moins 30 secondes.
 */
@Singleton
class LastFmScrobbler @Inject constructor(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {

    val isConfigured: Boolean
        get() = BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.LASTFM_SECRET.isNotBlank()

    fun qualifies(song: Song, listenedMs: Long): Boolean =
        song.durationMs >= 30_000 &&
            (listenedMs >= song.durationMs / 2 || listenedMs >= 4 * 60_000)

    /** Authentification « mobile » : identifiants échangés une fois contre une clé de session. */
    suspend fun login(username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val params = mapOf(
                    "method" to "auth.getMobileSession",
                    "username" to username,
                    "password" to password,
                    "api_key" to BuildConfig.LASTFM_API_KEY,
                )
                val json = post(params)
                val sessionKey = json.getJSONObject("session").getString("key")
                val name = json.getJSONObject("session").optString("name", username)
                settings.setLastFmSession(sessionKey, name)
                sessionKey
            }
        }

    suspend fun logout() = settings.setLastFmSession("", "")

    suspend fun updateNowPlaying(song: Song) = send(song, timestampSec = null)

    suspend fun scrobble(song: Song, startedAtSec: Long) = send(song, timestampSec = startedAtSec)

    private suspend fun send(song: Song, timestampSec: Long?): Result<Unit> =
        withContext(Dispatchers.IO) {
            val current = settings.settings.first()
            if (!isConfigured || !current.scrobbleEnabled || current.lastFmSessionKey.isBlank()) {
                return@withContext Result.success(Unit)
            }

            runCatching {
                val params = buildMap {
                    put("method", if (timestampSec == null) "track.updateNowPlaying" else "track.scrobble")
                    put("artist", song.displayArtist)
                    put("track", song.title)
                    put("album", song.displayAlbum)
                    put("duration", (song.durationMs / 1000).toString())
                    put("api_key", BuildConfig.LASTFM_API_KEY)
                    put("sk", current.lastFmSessionKey)
                    timestampSec?.let { put("timestamp", it.toString()) }
                }
                post(params)
                Unit
            }.onFailure { Log.w(TAG, "Scrobble échoué pour ${song.title}", it) }
        }

    private fun post(params: Map<String, String>): JSONObject {
        val signed = params + ("api_sig" to signature(params)) + ("format" to "json")
        val body = FormBody.Builder().apply { signed.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(ENDPOINT).post(body).build()

        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            val json = JSONObject(payload)
            if (json.has("error")) error("Last.fm ${json.optInt("error")}: ${json.optString("message")}")
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return json
        }
    }

    /** Signature Last.fm : concaténation clé+valeur triée par clé, suffixée du secret, en MD5. */
    private fun signature(params: Map<String, String>): String {
        val raw = params.toSortedMap().entries.joinToString("") { "${it.key}${it.value}" } +
            BuildConfig.LASTFM_SECRET
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "LastFmScrobbler"
        const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    }
}
