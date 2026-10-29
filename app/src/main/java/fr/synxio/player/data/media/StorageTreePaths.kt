package fr.synxio.player.data.media

/**
 * Chemin de fichier d'un dossier choisi avec le sélecteur système (`OpenDocumentTree`).
 *
 * Le scanner compare des chemins MediaStore (`/storage/emulated/0/…`) : il faut donc
 * retraduire l'identifiant de document du dossier choisi.
 */
object StorageTreePaths {

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"

    private const val PRIMARY_VOLUME = "primary"
    private const val RAW_PREFIX = "raw:"

    /**
     * Chemin absolu pour l'identifiant [documentId] (`primary:Livres/Tome 1`) fourni par
     * [authority], ou `null` si le dossier n'est pas un dossier du stockage local.
     *
     * Seul le premier « : » sépare le volume du chemin : un nom de dossier peut en
     * contenir d'autres (`Livres:Saga`), et l'ancien découpage les refusait sans un mot.
     */
    fun pathOf(authority: String?, documentId: String?): String? {
        if (documentId.isNullOrBlank()) return null
        return when (authority) {
            EXTERNAL_STORAGE_AUTHORITY -> {
                val separator = documentId.indexOf(':')
                if (separator <= 0) return null
                val volume = documentId.substring(0, separator)
                val relative = documentId.substring(separator + 1).trim('/')
                val root = if (volume.equals(PRIMARY_VOLUME, ignoreCase = true)) {
                    "/storage/emulated/0"
                } else {
                    "/storage/$volume"
                }
                if (relative.isEmpty()) root else "$root/$relative"
            }
            // « Téléchargements » donne parfois le vrai chemin (`raw:/storage/…`) ;
            // ses autres identifiants (`msd:42`) ne désignent pas un dossier du disque.
            DOWNLOADS_AUTHORITY -> documentId
                .takeIf { it.startsWith(RAW_PREFIX) }
                ?.removePrefix(RAW_PREFIX)
                ?.trimEnd('/')
                ?.takeIf { it.startsWith("/") }
            else -> null
        }
    }
}
