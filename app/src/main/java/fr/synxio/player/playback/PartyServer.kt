package fr.synxio.player.playback

import fi.iki.elonen.NanoHTTPD
import fr.synxio.player.data.model.Song
import org.json.JSONObject

class PartyServer(port: Int = 8080) : NanoHTTPD(port) {
    
    private var currentSong: Song? = null
    private var isPlaying: Boolean = false
    private var currentPosition: Long = 0
    private var lastUpdate: Long = System.currentTimeMillis()

    fun updateState(song: Song?, playing: Boolean, position: Long) {
        currentSong = song
        isPlaying = playing
        currentPosition = position
        lastUpdate = System.currentTimeMillis()
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
        if (uri == "/api/status") {
            val json = JSONObject()
            json.put("title", currentSong?.title ?: "Aucune lecture")
            json.put("artist", currentSong?.displayArtist ?: "")
            json.put("album", currentSong?.displayAlbum ?: "")
            val estimatedPosition = if (isPlaying) currentPosition + (System.currentTimeMillis() - lastUpdate) else currentPosition
            json.put("position", estimatedPosition)
            json.put("duration", currentSong?.durationMs ?: 0L)
            json.put("playing", isPlaying)
            
            val response = newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        }

        // Page HTML simple pour les amis
        val html = """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Synxio Party</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; 
                           background-color: #121212; color: white; display: flex; flex-direction: column; 
                           align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }
                    .card { background: #1E1E1E; padding: 2rem; border-radius: 24px; box-shadow: 0 8px 32px rgba(0,0,0,0.5); width: 80%; max-width: 400px; }
                    h1 { margin: 0 0 10px 0; font-size: 1.5rem; }
                    h2 { margin: 0 0 20px 0; font-size: 1.1rem; color: #BB86FC; font-weight: normal; }
                    .progress-bar { width: 100%; background: #333; height: 6px; border-radius: 3px; margin-top: 20px; overflow: hidden; }
                    .progress-fill { height: 100%; background: #BB86FC; width: 0%; transition: width 1s linear; }
                    .time { display: flex; justify-content: space-between; font-size: 0.8rem; color: #aaa; margin-top: 8px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <p style="color:#aaa; font-size:0.9rem; margin-bottom: 24px;">En direct de Synxio Party 🎉</p>
                    <h1 id="title">Chargement...</h1>
                    <h2 id="artist"></h2>
                    
                    <div class="progress-bar">
                        <div class="progress-fill" id="progress"></div>
                    </div>
                    <div class="time">
                        <span id="current-time">0:00</span>
                        <span id="total-time">0:00</span>
                    </div>
                </div>

                <script>
                    function formatTime(ms) {
                        if (ms <= 0) return "0:00";
                        const totalSeconds = Math.floor(ms / 1000);
                        const minutes = Math.floor(totalSeconds / 60);
                        const seconds = totalSeconds % 60;
                        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
                    }

                    async function fetchStatus() {
                        try {
                            const res = await fetch('/api/status');
                            const data = await res.json();
                            
                            document.getElementById('title').innerText = data.title;
                            document.getElementById('artist').innerText = data.artist;
                            
                            if (data.duration > 0) {
                                document.getElementById('current-time').innerText = formatTime(data.position);
                                document.getElementById('total-time').innerText = formatTime(data.duration);
                                document.getElementById('progress').style.width = (data.position / data.duration * 100) + '%';
                            } else {
                                document.getElementById('progress').style.width = '0%';
                            }
                        } catch (e) {
                            console.error(e);
                        }
                    }

                    setInterval(fetchStatus, 1000);
                    fetchStatus();
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
