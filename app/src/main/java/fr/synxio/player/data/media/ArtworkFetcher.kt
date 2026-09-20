package fr.synxio.player.data.media

import android.util.Log
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ArtworkFetcher {
    private const val TAG = "ArtworkFetcher"

    suspend fun fetchArtworkUrl(song: Song): String? = withContext(Dispatchers.IO) {
        try {
            val query = "${song.title} ${song.displayArtist}".trim()
            if (query.isBlank()) return@withContext null

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=1"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val results = jsonObject.optJSONArray("results")
                
                if (results != null && results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    // On récupère l'image en 100x100 et on la demande en 600x600 (haute qualité)
                    val artworkUrl100 = firstResult.optString("artworkUrl100")
                    if (artworkUrl100.isNotEmpty()) {
                        return@withContext artworkUrl100.replace("100x100bb", "600x600bb")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la récupération de la pochette pour ${song.title}", e)
        }
        return@withContext null
    }
}
