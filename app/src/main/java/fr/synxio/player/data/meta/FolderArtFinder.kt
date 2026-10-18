package fr.synxio.player.data.meta

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cherche une image de pochette posée à côté des morceaux : `cover.jpg`, `folder.png`,
 * `Front.jpg`, `Covers/Covers.jpg`…
 *
 * Android 13+ n'expose les images aux applications qu'avec l'autorisation « Photos » : sans
 * elle, le dossier paraît ne contenir que les fichiers audio et cette étape ne trouve rien.
 * MediaStore prend alors le relais pour les dossiers qu'il sait lire lui-même.
 */
@Singleton
class FolderArtFinder @Inject constructor() {

    /** Résultat par dossier : tous les titres d'un album le demandent. */
    private val cache = ConcurrentHashMap<String, String>()

    fun find(folderPath: String): File? {
        if (!FileNameInference.isAlbumFolder(folderPath)) return null
        cache[folderPath]?.let { return it.takeIf { p -> p.isNotEmpty() }?.let(::File) }
        val found = runCatching { search(File(folderPath)) }.getOrNull()
        cache[folderPath] = found?.path.orEmpty()
        return found
    }

    /** À appeler avant une nouvelle passe : une image a pu être ajoutée depuis. */
    fun clearCache() = cache.clear()

    private fun search(dir: File): File? {
        val entries = dir.listFiles() ?: return null
        val images = entries.filter { it.isFile && isImage(it.name) }
        pickCover(images.map { it.name }, allowSingle = !FileNameInference.isGeneric(dir.name))
            ?.let { name -> return images.first { it.name == name } }

        // Scans rangés dans un sous-dossier « Covers », « Scans », « Artwork ».
        val sub = entries.firstOrNull { it.isDirectory && it.name.lowercase() in COVER_DIRS } ?: return null
        val subImages = sub.listFiles()?.filter { it.isFile && isImage(it.name) }.orEmpty()
        return pickCover(subImages.map { it.name }, allowSingle = true)
            ?.let { name -> subImages.first { it.name == name } }
    }

    companion object {
        private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val COVER_DIRS = setOf("covers", "cover", "scans", "artwork", "artworks", "art")

        /** Noms exacts, du plus au moins fiable. */
        private val EXACT = listOf("cover", "folder", "front", "albumart", "album", "covers", "albumartlarge", "albumartsmall")
        private val PREFIXES = listOf("cover", "front", "folder", "albumart")

        internal fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

        /**
         * Choisit l'image de pochette parmi [names]. Une image seule et sans nom parlant
         * n'est retenue que si [allowSingle] : dans « Download », ce serait n'importe quoi.
         */
        internal fun pickCover(names: List<String>, allowSingle: Boolean): String? {
            val images = names.filter(::isImage)
            if (images.isEmpty()) return null
            fun base(name: String) = name.substringBeforeLast('.').lowercase().trim()

            for (exact in EXACT) images.firstOrNull { base(it) == exact }?.let { return it }
            for (prefix in PREFIXES) {
                images.filter { base(it).startsWith(prefix) }
                    // « AlbumArt_{GUID}_Large » avant « …_Small » : on garde la plus grande.
                    .sortedBy { if (base(it).contains("small")) 1 else 0 }
                    .firstOrNull()?.let { return it }
            }
            // Jamais le dos ni le livret.
            val candidates = images.filterNot { base(it).let { b -> b.contains("back") || b.contains("cd") || b.contains("inlay") || b.contains("inside") } }
            return candidates.singleOrNull()?.takeIf { allowSingle }
        }
    }
}
