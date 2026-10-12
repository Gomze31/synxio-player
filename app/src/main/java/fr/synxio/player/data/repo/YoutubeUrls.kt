package fr.synxio.player.data.repo

import java.net.URI

/**
 * Validation des liens YouTube avant de les confier à yt-dlp ou à la WebView.
 *
 * yt-dlp reçoit l'URL comme argument de ligne de commande : un texte commençant par `-`
 * (par exemple `--config-locations=/sdcard/x#youtube.com/watch`) serait interprété comme
 * une option. On n'accepte donc que des URL http(s) dont l'hôte est un domaine YouTube
 * connu, et on renvoie toujours une URL canonique `https://…` reconstruite par nos soins.
 *
 * Code JVM pur (java.net.URI) pour rester testable sans Android.
 */
object YoutubeUrls {

    /** Hôtes acceptés pour un lien à télécharger ou à ouvrir directement. */
    private val ALLOWED_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
    )

    /**
     * Hôtes vers lesquels la WebView peut naviguer en plus des domaines YouTube :
     * page de consentement (UE) et connexion Google.
     */
    private val WEBVIEW_EXTRA_HOSTS = setOf(
        "consent.youtube.com",
        "consent.google.com",
        "accounts.google.com",
        "accounts.youtube.com",
    )

    /**
     * Normalise [input] en URL `https://` YouTube, ou renvoie null si le texte n'est pas
     * un lien YouTube sûr. Un texte sans schéma (`youtu.be/abc`) est complété en https.
     */
    fun normalize(input: String?): String? {
        val trimmed = input?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        if (trimmed.startsWith("-")) return null
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return null

        val withScheme = if (SCHEME.containsMatchIn(trimmed)) {
            trimmed
        } else {
            // Pas de schéma : on n'accepte que « hôte YouTube/… ».
            val host = trimmed.substringBefore('/').substringBefore('?').lowercase()
            if (host !in ALLOWED_HOSTS) return null
            "https://$trimmed"
        }

        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        if (uri.isOpaque) return null
        if (uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != 80 && uri.port != 443) return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in ALLOWED_HOSTS) return null

        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        // Le fragment est volontairement abandonné.
        val canonical = "https://$host$path$query"
        // Re-vérification : l'URL reconstruite doit rester valide et commencer par https://.
        return canonical.takeIf { runCatching { URI(it) }.isSuccess }
    }

    /**
     * Comme [normalize], mais n'accepte qu'un lien pointant vers une vidéo précise
     * (watch?v=…, shorts/…, live/…, youtu.be/…). C'est ce qui est envoyé à yt-dlp.
     */
    fun normalizeVideo(input: String?): String? {
        val url = normalize(input) ?: return null
        val uri = URI(url)
        val host = uri.host
        val path = uri.rawPath.orEmpty()
        val isVideo = when {
            host == "youtu.be" -> VIDEO_ID.matches(path.removePrefix("/").removeSuffix("/"))
            path == "/watch" || path == "/watch/" ->
                uri.rawQuery.orEmpty().split('&').any { param ->
                    param.startsWith("v=") && VIDEO_ID.matches(param.removePrefix("v="))
                }
            path.startsWith("/shorts/") || path.startsWith("/live/") ->
                VIDEO_ID.matches(path.substringAfter('/', "").substringAfter('/').removeSuffix("/"))
            else -> false
        }
        return url.takeIf { isVideo }
    }

    /**
     * Vrai si la WebView peut charger [url] en interne : HTTPS uniquement, sur un domaine
     * YouTube (sous-domaines compris) ou une page de consentement/connexion Google.
     */
    fun isAllowedInWebView(url: String?): Boolean {
        val uri = runCatching { URI(url?.trim() ?: return false) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host in WEBVIEW_EXTRA_HOSTS
    }

    private const val MAX_LENGTH = 2048
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")
    /** Identifiant de vidéo YouTube : 11 caractères base64url en pratique, on tolère 6 à 64. */
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{6,64}$")
}
