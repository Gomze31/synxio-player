package fr.synxio.player.data.meta

import fr.synxio.player.data.media.tidyTag
import java.util.Calendar

/** Champ que l'atelier peut écrire dans un fichier. */
enum class FixField(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    ALBUM("Album"),
    ALBUM_ARTIST("Artiste de l'album"),
    YEAR("Année"),
    GENRE("Genre"),
    TRACK("N° de piste"),
    ARTWORK("Pochette"),
}

/** D'où vient la valeur proposée, en mots pour l'utilisateur. */
enum class FixOrigin(val label: String) {
    ENCODING("Accents réparés"),
    CLEANUP("Nettoyage"),
    INDEX("Index Android"),
    FILE_NAME("Nom du fichier"),
    ONLINE("MusicBrainz"),
    FOLDER_IMAGE("Image du dossier"),
    COVER_ART("Cover Art Archive"),
}

/** Une valeur à écrire dans un fichier. [doubt] non nul : à vérifier, décochée d'office. */
data class FieldFix(
    val field: FixField,
    val current: String?,
    val proposed: String,
    val origin: FixOrigin,
    val doubt: String? = null,
) {
    val sure: Boolean get() = doubt == null
}

/** Les corrections proposées pour un fichier. */
data class TrackFix(
    val songId: Long,
    val path: String,
    val fileName: String,
    /** Titre et artiste tels que Synxio les affiche, pour reconnaître le morceau. */
    val displayTitle: String,
    val displayArtist: String?,
    val fixes: List<FieldFix>,
    /** Image à intégrer quand [FixField.ARTWORK] est proposé. */
    val artworkPath: String?,
    /** Pochette à montrer dans la revue : celle proposée, ou l'actuelle. */
    val previewUri: String?,
) {
    val sure: Boolean get() = fixes.all { it.sure }
    fun fix(field: FixField): FieldFix? = fixes.firstOrNull { it.field == field }
}

/** Ce qu'on sait de la recherche en ligne qui a fourni des valeurs. */
data class LookupInfo(
    val confidence: Int,
    /** Recherche faite sans artiste : un homonyme de même durée est possible. */
    val byTitleOnly: Boolean,
)

/** Tout ce qu'il faut pour décider quoi écrire dans un fichier. */
data class WorkshopInput(
    val songId: Long,
    val path: String,
    /** Tags tels qu'ils sont dans le fichier, sans aucune réparation ; `null` si illisible. */
    val fileTags: TrackMetadata?,
    val hasEmbeddedArt: Boolean,
    val resolved: TrackMetadata,
    val sources: Map<MetaField, MetaSource>,
    val artSource: ArtSource,
    val artUri: String?,
    /** Nom de fichier lisible dans les deux sens : « Titre - Artiste » ou l'inverse. */
    val ambiguousOrder: Boolean,
    val lookup: LookupInfo?,
)

/**
 * Décide quelles valeurs de la cascade méritent d'être écrites dans les fichiers.
 *
 * Une valeur est proposée quand le fichier ne l'a pas, ou l'a abîmée (accents, nom de
 * chaîne YouTube). Les règles de doute viennent de la relecture d'une vraie bibliothèque :
 * un ordre « Artiste - Titre » indécidable, une fiche trouvée par le titre seul (« Skyfall »
 * → une reprise), un album de compilation, une correspondance moyenne. Tout ce qui est
 * douteux reste proposé, mais décoché : l'utilisateur tranche.
 *
 * Fonction pure, testée sur la JVM.
 */
object TagWorkshop {

    /** En dessous, une fiche MusicBrainz est à vérifier. */
    const val SURE_CONFIDENCE = 85

    fun propose(input: WorkshopInput): TrackFix? {
        if (input.fileTags == null) return null
        val fileName = input.path.substringAfterLast('/')
        val baseName = fileName.substringBeforeLast('.')
        val fixes = buildList {
            textFix(input, FixField.TITLE, MetaField.TITLE, input.fileTags.title, input.resolved.title)
                // Sans tag titre, Android affiche déjà le nom du fichier : le recopier n'apporte
                // rien. Il remplace en revanche utilement un « Piste 12 ».
                ?.takeUnless {
                    it.origin == FixOrigin.FILE_NAME && it.current == null && it.proposed.equals(baseName, ignoreCase = true)
                }
                ?.let(::add)
            textFix(input, FixField.ARTIST, MetaField.ARTIST, input.fileTags.artist, input.resolved.artist)?.let(::add)
            textFix(input, FixField.ALBUM, MetaField.ALBUM, input.fileTags.album, input.resolved.album)?.let(::add)
            textFix(input, FixField.ALBUM_ARTIST, MetaField.ALBUM_ARTIST, input.fileTags.albumArtist, input.resolved.albumArtist)?.let(::add)
            textFix(input, FixField.GENRE, MetaField.GENRE, input.fileTags.genre, input.resolved.genre)?.let(::add)
            numberFix(input, FixField.YEAR, MetaField.YEAR, input.fileTags.year, input.resolved.year)?.let(::add)
            numberFix(input, FixField.TRACK, MetaField.TRACK, input.fileTags.track, input.resolved.track)?.let(::add)
            artworkFix(input)?.let(::add)
        }.linkFileNameDoubts()
        if (fixes.isEmpty()) return null
        val artworkPath = input.artUri?.takeIf { fixes.any { it.field == FixField.ARTWORK } }?.removePrefix("file://")
        return TrackFix(
            songId = input.songId,
            path = input.path,
            fileName = fileName,
            displayTitle = input.resolved.title ?: baseName,
            displayArtist = input.resolved.artist,
            fixes = fixes,
            artworkPath = artworkPath,
            previewUri = input.artUri,
        )
    }

    private fun textFix(
        input: WorkshopInput,
        field: FixField,
        metaField: MetaField,
        raw: String?,
        resolved: String?,
    ): FieldFix? {
        val proposed = resolved?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val current = raw?.trim()?.takeIf { it.isNotEmpty() }
        if (current == proposed) return null
        val source = input.sources[metaField] ?: return null
        var origin = when (source) {
            MetaSource.FILE_TAGS -> when {
                current == null -> return null
                current.tidyTag() == proposed -> FixOrigin.ENCODING
                else -> FixOrigin.CLEANUP
            }
            // Une valeur que seul MediaStore connaît (le tag est illisible pour lui) : on
            // la remet dans le fichier pour que les autres lecteurs la voient aussi.
            MetaSource.MEDIASTORE -> if (current == null) FixOrigin.INDEX else return null
            MetaSource.INFERRED -> FixOrigin.FILE_NAME
            MetaSource.ONLINE -> FixOrigin.ONLINE
        }
        // « Liane Foly "On a tous le droit" » → « On a tous le droit » : le titre déduit est
        // extrait du tag lui-même, c'est un nettoyage.
        if (current != null && field == FixField.TITLE && origin == FixOrigin.FILE_NAME &&
            current.contains(proposed, ignoreCase = true)
        ) origin = FixOrigin.CLEANUP
        // Un tag présent et sain n'est remplacé que par une réparation de ce même tag.
        if (current != null && origin != FixOrigin.ENCODING && origin != FixOrigin.CLEANUP &&
            TagSanity.isValidFor(field, current)
        ) return null
        return FieldFix(field, current, proposed, origin, doubtFor(input, field, origin, proposed))
    }

    private fun numberFix(input: WorkshopInput, field: FixField, metaField: MetaField, raw: Int?, resolved: Int?): FieldFix? {
        val proposed = resolved?.takeIf { it > 0 } ?: return null
        val current = raw?.takeIf { it > 0 }
        if (current != null) return null
        val origin = when (input.sources[metaField]) {
            MetaSource.INFERRED -> FixOrigin.FILE_NAME
            MetaSource.ONLINE -> FixOrigin.ONLINE
            MetaSource.MEDIASTORE -> FixOrigin.INDEX
            else -> return null
        }
        return FieldFix(field, null, proposed.toString(), origin, doubtFor(input, field, origin, proposed.toString()))
    }

    private fun artworkFix(input: WorkshopInput): FieldFix? {
        if (input.hasEmbeddedArt || input.artUri == null || !input.artUri.startsWith("file://")) return null
        val origin = when (input.artSource) {
            ArtSource.ONLINE -> FixOrigin.COVER_ART
            ArtSource.FOLDER -> FixOrigin.FOLDER_IMAGE
            // Extraite du fichier par Synxio : elle y est déjà.
            else -> return null
        }
        return FieldFix(FixField.ARTWORK, null, "Intégrée au fichier", origin, doubtFor(input, FixField.ARTWORK, origin, ""))
    }

    private fun doubtFor(input: WorkshopInput, field: FixField, origin: FixOrigin, proposed: String): String? {
        when (origin) {
            FixOrigin.FILE_NAME -> {
                if ((field == FixField.TITLE || field == FixField.ARTIST) && input.ambiguousOrder) {
                    return "Le nom ne dit pas lequel est l'artiste"
                }
                if (field == FixField.ARTIST && proposed.split(' ').count { it.isNotBlank() } > 5) {
                    return "Ressemble plus à une phrase qu'à un artiste"
                }
            }
            FixOrigin.ONLINE, FixOrigin.COVER_ART -> {
                val lookup = input.lookup ?: return "Fiche en ligne sans détail"
                if (lookup.byTitleOnly) return "Trouvé par le titre seul : un homonyme est possible"
                if (lookup.confidence < SURE_CONFIDENCE) return "Correspondance moyenne (${lookup.confidence} %)"
                if ((field == FixField.ALBUM || field == FixField.YEAR || field == FixField.ALBUM_ARTIST) &&
                    looksLikeCompilation(input.resolved)
                ) return "Album de compilation, pas l'album d'origine"
                if (field == FixField.YEAR && proposed.toIntOrNull()?.let { it >= currentYear() } == true) {
                    return "Année très récente : peut-être une réédition"
                }
            }
            else -> Unit
        }
        return null
    }

    /**
     * Artiste et titre tirés du même découpage du nom de fichier : si l'un est douteux,
     * l'autre l'est aussi. Un artiste faux veut dire que le titre a été mal coupé.
     */
    private fun List<FieldFix>.linkFileNameDoubts(): List<FieldFix> {
        val pair = setOf(FixField.ARTIST, FixField.TITLE)
        if (none { it.field in pair && it.origin == FixOrigin.FILE_NAME && it.doubt != null }) return this
        return map { fix ->
            if (fix.field in pair && fix.origin == FixOrigin.FILE_NAME && fix.doubt == null) {
                fix.copy(doubt = "Tiré du même découpage que l'artiste, douteux lui aussi")
            } else fix
        }
    }

    internal fun looksLikeCompilation(meta: TrackMetadata): Boolean {
        val album = meta.album.orEmpty()
        val albumArtist = meta.albumArtist.orEmpty()
        return COMPILATION_ARTISTS.any { albumArtist.equals(it, ignoreCase = true) } ||
            COMPILATION_HINT.containsMatchIn(album)
    }

    private fun currentYear() = Calendar.getInstance().get(Calendar.YEAR)

    private val COMPILATION_ARTISTS = setOf("Various Artists", "Artistes variés", "Artistes divers", "Divers", "VA")
    private val COMPILATION_HINT = Regex(
        "(?i)\\b(?:hits?|compilation|best of|top \\d+|\\d+ (?:stars|tubes|succès)|vol\\.? ?\\d+|volume \\d+|" +
            "foute|ultimate|party|anthology|les plus belles|n[°o] ?1)\\b",
    )
}

/** Validité d'une valeur actuelle de tag, champ par champ. */
private fun TagSanity.isValidFor(field: FixField, value: String): Boolean = when (field) {
    FixField.TITLE -> isValidTitle(value)
    FixField.ARTIST, FixField.ALBUM_ARTIST -> isValidArtist(value)
    FixField.ALBUM -> isValidAlbum(value)
    FixField.GENRE -> isValidGenre(value)
    else -> true
}
