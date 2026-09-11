package fr.synxio.player.data.repo

import fr.synxio.player.data.model.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor() {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    // API endpoint for RadioBrowser (using a well-known reliable node)
    private val baseUrl = "https://de1.api.radio-browser.info/json"

    suspend fun getTopRadios(limit: Int = 100): List<RadioStation> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/stations/topvote/$limit")
            .header("User-Agent", "SynxioMediaPlayer/2.6.0")
            .build()
            
        return@withContext runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val bodyString = response.body?.string() ?: return@runCatching emptyList()
                json.decodeFromString<List<RadioStation>>(bodyString)
            }
        }.getOrDefault(emptyList())
    }
    
    suspend fun searchRadios(query: String, limit: Int = 50): List<RadioStation> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/stations/search?name=$query&limit=$limit&order=clickcount&reverse=true"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SynxioMediaPlayer/2.6.0")
            .build()

        return@withContext runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val bodyString = response.body?.string() ?: return@runCatching emptyList()
                json.decodeFromString<List<RadioStation>>(bodyString)
            }
        }.getOrDefault(emptyList())
    }
}
