package fr.synxio.player.data.repo.ytdlp

/**
 * Signatures d'un yt-dlp périmé dans sa sortie d'erreur. YouTube change souvent ; une
 * version de quelques semaines produit l'un de ces messages.
 */
object YtDlpOutdated {

    private val patterns = listOf(
        "Unable to extract",
        "Sign in to confirm",
        "HTTP Error 403",
        "Requested format is not available",
    )

    fun looksOutdated(stderr: String): Boolean =
        patterns.any { stderr.contains(it, ignoreCase = true) }
}
