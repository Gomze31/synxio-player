package fr.synxio.player.playback

/**
 * Traduction des pochettes privées en adresses lisibles par les autres apps.
 *
 * La cascade de métadonnées produit des `file://` : pochettes extraites ou téléchargées
 * dans `files/meta-artwork`, images de dossier. Android Auto, l'écran de verrouillage et
 * les casques ne peuvent pas ouvrir ces fichiers ; [ArtworkProvider] les sert en
 * `content://`, sur ces seuls chemins.
 */
object ArtworkUris {
    const val SEGMENT_ART = "art"
    const val SEGMENT_FOLDER = "folder"
    const val SEGMENT_RADIO = "radio"

    private const val PRIVATE_DIR_MARKER = "/files/meta-artwork/"
    private val PRIVATE_NAME = Regex("[0-9a-f]{40}\\.jpg")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** Racines du stockage partagé où peuvent vivre les images de dossier. */
    private val SHARED_ROOTS = listOf("/storage/", "/sdcard/", "/mnt/media_rw/")

    /**
     * Segments de chemin `content://` pour une pochette, ou null si l'URI est déjà
     * lisible par tous (MediaStore, ressource) ou n'est pas une pochette que l'on sert.
     */
    fun providerSegments(uri: String): List<String>? {
        if (!uri.startsWith("file://")) return null
        // URLDecoder lirait un « + » littéral comme une espace : il reste un « + ».
        val path = runCatching {
            java.net.URLDecoder.decode(uri.removePrefix("file://").replace("+", "%2B"), "UTF-8")
        }.getOrNull() ?: return null
        val marker = path.indexOf(PRIVATE_DIR_MARKER)
        if (marker >= 0) {
            val name = path.substring(marker + PRIVATE_DIR_MARKER.length)
            return if (PRIVATE_NAME.matches(name)) listOf(SEGMENT_ART, name) else null
        }
        return if (isServableFolderImage(path)) listOf(SEGMENT_FOLDER, path) else null
    }

    /** Nom de fichier servi sous « art » : uniquement une empreinte produite par `ArtworkStore`. */
    fun isPrivateArtName(name: String): Boolean = PRIVATE_NAME.matches(name)

    /**
     * Une image de dossier que le fournisseur accepte de servir.
     *
     * Le fournisseur est exporté : il refuse tout ce qui n'est pas une image du stockage
     * partagé, et tout chemin qui remonterait l'arborescence.
     */
    fun isServableFolderImage(path: String): Boolean {
        if (!path.startsWith("/") || path.contains('\u0000')) return false
        if (SHARED_ROOTS.none { path.startsWith(it) }) return false
        if (path.split('/').any { it == ".." || it == "." }) return false
        val extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    fun mimeTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}
