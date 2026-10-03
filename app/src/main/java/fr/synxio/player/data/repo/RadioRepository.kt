package fr.synxio.player.data.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val prefs = context.getSharedPreferences("synxio_radio_prefs", Context.MODE_PRIVATE)

    // Serveurs miroirs de l'API RadioBrowser
    private val mirrorHosts = listOf(
        "https://de1.api.radio-browser.info/json",
        "https://nl1.api.radio-browser.info/json",
        "https://at1.api.radio-browser.info/json"
    )

    private val _favoriteStations = MutableStateFlow<List<RadioStation>>(loadFavoritesFromPrefs())
    val favoriteStations: Flow<List<RadioStation>> = _favoriteStations.asStateFlow()

    private fun loadFavoritesFromPrefs(): List<RadioStation> {
        val raw = prefs.getString("favorite_stations_json", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RadioStation>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveFavoritesToPrefs(list: List<RadioStation>) {
        val raw = json.encodeToString(list)
        prefs.edit().putString("favorite_stations_json", raw).apply()
        _favoriteStations.value = list
    }

    fun isFavorite(stationUuid: String): Boolean {
        return _favoriteStations.value.any { it.stationUuid == stationUuid }
    }

    fun toggleFavorite(station: RadioStation) {
        val current = _favoriteStations.value.toMutableList()
        val index = current.indexOfFirst { it.stationUuid == station.stationUuid }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, station)
        }
        saveFavoritesToPrefs(current)
    }

    private suspend fun executeQuery(pathAndQuery: String): List<RadioStation> = withContext(Dispatchers.IO) {
        for (base in mirrorHosts) {
            val url = "$base/$pathAndQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SynxioMediaPlayer/4.1.0")
                .build()

            val stations = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bodyString = response.body?.string() ?: return@use null
                    json.decodeFromString<List<RadioStation>>(bodyString)
                }
            }.getOrNull()

            if (stations != null) {
                return@withContext stations.filter { it.urlResolved.isNotBlank() }
            }
        }
        emptyList()
    }

    suspend fun getTopRadios(limit: Int = 100): List<RadioStation> {
        return executeQuery("stations/search?country=France&limit=$limit&order=clickcount&reverse=true")
    }

    suspend fun getTopWorldRadios(limit: Int = 100): List<RadioStation> {
        return executeQuery("stations/search?limit=$limit&order=clickcount&reverse=true")
    }

    suspend fun getRadiosByTag(tag: String, limit: Int = 80): List<RadioStation> {
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        return executeQuery("stations/bytag/$encodedTag?limit=$limit&order=clickcount&reverse=true")
    }

    suspend fun searchRadios(query: String, limit: Int = 60): List<RadioStation> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getTopRadios(limit)
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        // Recherche sans filtre de pays pour permettre de trouver des radios internationales
        return executeQuery("stations/search?name=$encoded&limit=$limit&order=clickcount&reverse=true")
    }
}
