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
            json.put("artist", currentSong?.displayArtist ?: "En attente")
            json.put("album", currentSong?.displayAlbum ?: "")
            val estimatedPosition = if (isPlaying) currentPosition + (System.currentTimeMillis() - lastUpdate) else currentPosition
            json.put("position", estimatedPosition.coerceAtLeast(0L))
            json.put("duration", currentSong?.durationMs ?: 0L)
            json.put("playing", isPlaying)
            
            val response = newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        }

        // Interface web moderne et immersive pour les invités
        val html = """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Synxio Party 🎉</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: 'Outfit', -apple-system, BlinkMacSystemFont, sans-serif;
                        background: radial-gradient(circle at 50% 20%, #201736, #0e0d13 80%);
                        color: #ffffff;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 20px;
                        overflow-x: hidden;
                    }
                    .party-container {
                        background: rgba(30, 26, 44, 0.7);
                        backdrop-filter: blur(24px);
                        -webkit-backdrop-filter: blur(24px);
                        border: 1px solid rgba(255, 255, 255, 0.12);
                        padding: 2.2rem;
                        border-radius: 32px;
                        box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6), 0 0 40px rgba(120, 80, 240, 0.15);
                        width: 100%;
                        max-width: 420px;
                        text-align: center;
                        position: relative;
                    }
                    .live-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        background: rgba(255, 255, 255, 0.08);
                        padding: 6px 14px;
                        border-radius: 20px;
                        font-size: 0.85rem;
                        font-weight: 600;
                        letter-spacing: 0.5px;
                        margin-bottom: 24px;
                        border: 1px solid rgba(255, 255, 255, 0.08);
                    }
                    .dot {
                        width: 8px;
                        height: 8px;
                        border-radius: 50%;
                        background: #4ade80;
                        box-shadow: 0 0 10px #4ade80;
                        animation: pulse 2s infinite;
                    }
                    .dot.paused {
                        background: #f59e0b;
                        box-shadow: 0 0 10px #f59e0b;
                        animation: none;
                    }
                    @keyframes pulse {
                        0% { transform: scale(0.95); opacity: 0.8; }
                        50% { transform: scale(1.3); opacity: 1; }
                        100% { transform: scale(0.95); opacity: 0.8; }
                    }
                    .artwork-disc {
                        width: 170px;
                        height: 170px;
                        margin: 0 auto 24px;
                        border-radius: 50%;
                        background: linear-gradient(135deg, #7c3aed, #ec4899);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        box-shadow: 0 10px 30px rgba(124, 58, 237, 0.4);
                        border: 4px solid rgba(255, 255, 255, 0.2);
                        transition: transform 0.5s ease;
                    }
                    .spinning {
                        animation: rotate 15s linear infinite;
                    }
                    @keyframes rotate {
                        from { transform: rotate(0deg); }
                        to { transform: rotate(360deg); }
                    }
                    .disc-center {
                        width: 50px;
                        height: 50px;
                        background: #12101a;
                        border-radius: 50%;
                        border: 3px solid rgba(255, 255, 255, 0.3);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .disc-hole {
                        width: 14px;
                        height: 14px;
                        background: #2a2040;
                        border-radius: 50%;
                    }
                    h1 {
                        font-size: 1.55rem;
                        font-weight: 700;
                        margin-bottom: 6px;
                        line-height: 1.25;
                        color: #ffffff;
                        text-shadow: 0 2px 8px rgba(0,0,0,0.4);
                    }
                    h2 {
                        font-size: 1.05rem;
                        font-weight: 600;
                        color: #c4b5fd;
                        margin-bottom: 4px;
                    }
                    .album {
                        font-size: 0.88rem;
                        color: #94a3b8;
                        margin-bottom: 24px;
                    }
                    .progress-container {
                        width: 100%;
                        background: rgba(255, 255, 255, 0.1);
                        height: 8px;
                        border-radius: 4px;
                        margin-top: 10px;
                        overflow: hidden;
                    }
                    .progress-fill {
                        height: 100%;
                        background: linear-gradient(90deg, #8b5cf6, #d946ef);
                        width: 0%;
                        border-radius: 4px;
                        transition: width 0.4s linear;
                    }
                    .time-labels {
                        display: flex;
                        justify-content: space-between;
                        font-size: 0.82rem;
                        font-weight: 600;
                        color: #94a3b8;
                        margin-top: 10px;
                    }
                    .footer {
                        margin-top: 24px;
                        font-size: 0.8rem;
                        color: #64748b;
                    }
                </style>
            </head>
            <body>
                <div class="party-container">
                    <div class="live-badge">
                        <span class="dot" id="live-dot"></span>
                        <span id="live-text">SYNXIOPARTY • EN DIRECT</span>
                    </div>

                    <div class="artwork-disc spinning" id="disc">
                        <div class="disc-center">
                            <div class="disc-hole"></div>
                        </div>
                    </div>

                    <h1 id="title">En attente de lecture…</h1>
                    <h2 id="artist">Synxio Media Player</h2>
                    <p class="album" id="album"></p>

                    <div class="progress-container">
                        <div class="progress-fill" id="progress"></div>
                    </div>
                    <div class="time-labels">
                        <span id="current-time">0:00</span>
                        <span id="total-time">0:00</span>
                    </div>

                    <p class="footer">Partagé depuis Synxio Player</p>
                </div>

                <script>
                    function formatTime(ms) {
                        if (ms <= 0 || isNaN(ms)) return "0:00";
                        const totalSeconds = Math.floor(ms / 1000);
                        const minutes = Math.floor(totalSeconds / 60);
                        const seconds = totalSeconds % 60;
                        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
                    }

                    async function fetchStatus() {
                        try {
                            const res = await fetch('/api/status');
                            const data = await res.json();

                            document.getElementById('title').innerText = data.title || "Aucune lecture";
                            document.getElementById('artist').innerText = data.artist || "";
                            document.getElementById('album').innerText = data.album || "";

                            const disc = document.getElementById('disc');
                            const dot = document.getElementById('live-dot');
                            const liveText = document.getElementById('live-text');

                            if (data.playing) {
                                disc.classList.add('spinning');
                                dot.classList.remove('paused');
                                liveText.innerText = "SYNXIOPARTY • EN DIRECT";
                            } else {
                                disc.classList.remove('spinning');
                                dot.classList.add('paused');
                                liveText.innerText = "EN PAUSE";
                            }

                            if (data.duration > 0) {
                                document.getElementById('current-time').innerText = formatTime(data.position);
                                document.getElementById('total-time').innerText = formatTime(data.duration);
                                const percent = Math.min(100, Math.max(0, (data.position / data.duration) * 100));
                                document.getElementById('progress').style.width = percent + '%';
                            } else {
                                document.getElementById('progress').style.width = '0%';
                                document.getElementById('current-time').innerText = "0:00";
                                document.getElementById('total-time').innerText = "0:00";
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
