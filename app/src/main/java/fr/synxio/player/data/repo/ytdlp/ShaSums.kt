package fr.synxio.player.data.repo.ytdlp

/**
 * Lit le fichier `SHA2-256SUMS` publié avec chaque release de yt-dlp : une ligne par
 * fichier, `<sha256 hex>  <nom>` (le nom peut être précédé de `*`).
 */
object ShaSums {

    private val hex64 = Regex("^[0-9a-fA-F]{64}$")

    fun digestFor(sums: String, fileName: String): String? =
        sums.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val name = parts[1].removePrefix("*")
                if (name != fileName) return@mapNotNull null
                parts[0].takeIf { hex64.matches(it) }?.lowercase()
            }
            .firstOrNull()
}
