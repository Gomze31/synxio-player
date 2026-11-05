package fr.synxio.player.playback

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.synxio.player.BuildConfig
import fr.synxio.player.data.repo.RadioRepository
import fr.synxio.player.playback.art.ArtBitmaps
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Sert les pochettes aux autres apps : Android Auto, écran de verrouillage, Bluetooth.
 *
 * Ces hôtes ne lisent que des `content://` ou des ressources. Trois familles d'adresses :
 * - `art/<empreinte>.jpg` : pochette extraite ou téléchargée par la cascade ;
 * - `folder/<chemin>` : image posée dans le dossier de l'album ;
 * - `radio/<uuid>` : logo d'une radio favorite, téléchargé puis gardé en cache.
 *
 * Exporté, en lecture seule, et limité à ces chemins : il ne sert rien d'autre.
 */
class ArtworkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun radioRepository(): RadioRepository
        fun okHttpClient(): OkHttpClient
    }

    private val entryPoint: ProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication(requireNotNull(context).applicationContext, ProviderEntryPoint::class.java)
    }

    /** Rien à préparer : le fournisseur démarre avec le processus, avant l'application. */
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val segments = uri.pathSegments
        return when (segments.firstOrNull()) {
            ArtworkUris.SEGMENT_ART, ArtworkUris.SEGMENT_FOLDER -> ArtworkUris.mimeTypeFor(segments.lastOrNull().orEmpty())
            ArtworkUris.SEGMENT_RADIO -> "image/*"
            ArtworkUris.SEGMENT_ALBUM -> "image/jpeg"
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Lecture seule")
        val size = ArtworkUris.sizeOf(uri.getQueryParameter(ArtworkUris.PARAM_SIZE))
        val segments = uri.pathSegments
        // Pochette MediaStore sans taille : on la relaie telle quelle.
        if (size == null && segments.size == 2 && segments[0] == ArtworkUris.SEGMENT_ALBUM) {
            val albumId = segments[1].toLongOrNull() ?: throw FileNotFoundException(uri.toString())
            return requireNotNull(context).contentResolver.openFileDescriptor(albumArtUri(albumId), "r")
                ?: throw FileNotFoundException(uri.toString())
        }
        val file = resolve(uri, size) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolve(uri: Uri, size: Int?): File? {
        val context = context ?: return null
        val segments = uri.pathSegments
        if (segments.size != 2) return null
        val (kind, value) = segments
        val original: File? = when (kind) {
            ArtworkUris.SEGMENT_ART -> value.takeIf(ArtworkUris::isPrivateArtName)
                ?.let { File(File(context.filesDir, "meta-artwork"), it) }
            ArtworkUris.SEGMENT_FOLDER -> value.takeIf(ArtworkUris::isServableFolderImage)
                ?.let(::File)
                // Le chemin réel doit rester une image du stockage partagé, même via un lien.
                ?.takeIf { ArtworkUris.isServableFolderImage(it.canonicalPath) }
            ArtworkUris.SEGMENT_RADIO -> radioLogo(context.cacheDir, value)
            ArtworkUris.SEGMENT_ALBUM -> null
            else -> return null
        }?.takeIf { it.isFile && it.canRead() }
        if (size == null) return original
        return when (kind) {
            ArtworkUris.SEGMENT_ALBUM -> value.toLongOrNull()
                ?.let { id -> sized(context, "album/$id", size) { ArtBitmaps.decode(context, albumArtUri(id), size) } }
            else -> original?.let { file -> sized(context, "$kind/$value", size) { ArtBitmaps.decode(context, Uri.fromFile(file), size) } }
        }
    }

    /**
     * Version carrée de [size] pixels, rendue une fois puis gardée en cache par clé et
     * taille : Android Auto demande la même pochette à chaque ouverture de liste.
     */
    private fun sized(context: Context, key: String, size: Int, decode: () -> Bitmap?): File? {
        val dir = File(context.cacheDir, SIZED_CACHE_DIR).apply { mkdirs() }
        val target = File(dir, sha1("$key@$size") + ".jpg")
        if (target.isFile) return target
        val source = decode() ?: return null
        return runCatching {
            val square = ArtBitmaps.centerCrop(source, size)
            val tmp = File(dir, target.name + ".tmp")
            tmp.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, SIZED_QUALITY, it) }
            if (square !== source) square.recycle()
            source.recycle()
            if (!tmp.renameTo(target)) tmp.delete()
            target.takeIf { it.isFile }
        }.onFailure { Log.d(TAG, "Pochette redimensionnée non rendue", it) }.getOrNull()
    }

    /**
     * Logo d'une radio favorite, mis en cache une semaine.
     *
     * Seules les radios mises en favori sont servies : l'adresse vient de leur fiche, jamais
     * de l'appelant, pour que le fournisseur ne devienne pas un relais vers n'importe quel site.
     */
    private fun radioLogo(cacheDir: File, uuid: String): File? {
        val station = entryPoint.radioRepository().favoriteStationsNow.firstOrNull { it.stationUuid == uuid }
            ?: return null
        val url = station.favicon.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val dir = File(cacheDir, RADIO_CACHE_DIR).apply { mkdirs() }
        val target = File(dir, sha1(url))
        if (target.isFile && System.currentTimeMillis() - target.lastModified() < RADIO_CACHE_MS) return target

        return runCatching {
            val client = entryPoint.okHttpClient().newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@use null
                if (body.contentLength() > MAX_LOGO_BYTES) return@use null
                val bytes = body.bytes().takeIf { it.isNotEmpty() && it.size <= MAX_LOGO_BYTES } ?: return@use null
                val tmp = File(dir, target.name + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) tmp.delete()
                target.takeIf { it.isFile }
            }
        }.onFailure { Log.d(TAG, "Logo de radio indisponible", it) }
            .getOrNull()
            // Hors ligne : un ancien logo vaut mieux que rien.
            ?: target.takeIf { it.isFile }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "ArtworkProvider"
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".artwork"
        private const val RADIO_CACHE_DIR = "auto-radio-art"
        private const val RADIO_CACHE_MS = 7L * 24 * 3600 * 1000
        private const val MAX_LOGO_BYTES = 1_000_000L
        private const val SIZED_CACHE_DIR = "auto-art"
        private const val SIZED_QUALITY = 88
        private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

        fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

        /** Adresse publique d'une pochette ; l'URI d'origine si elle est déjà lisible. */
        fun publicUri(uri: Uri?): Uri? {
            if (uri == null) return null
            val segments = ArtworkUris.providerSegments(uri.toString()) ?: return uri
            return build(segments)
        }

        /**
         * Adresse publique d'une pochette à [size] pixels de côté ; sans taille, [publicUri].
         * Une pochette MediaStore passe alors par le fournisseur, segment `album`, pour
         * être réduite ; une ressource ou une adresse http reste telle quelle.
         */
        fun sizedUri(uri: Uri?, size: Int?): Uri? {
            if (uri == null) return null
            if (size == null) return publicUri(uri)
            val segments = ArtworkUris.providerSegments(uri.toString())
                ?: ArtworkUris.albumArtSegments(uri.toString())
                ?: return uri
            return build(segments).buildUpon().appendQueryParameter(ArtworkUris.PARAM_SIZE, size.toString()).build()
        }

        fun radioUri(stationUuid: String): Uri = build(listOf(ArtworkUris.SEGMENT_RADIO, stationUuid))

        private fun build(segments: List<String>): Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(AUTHORITY)
            .apply { segments.forEach(::appendPath) }
            .build()

        private fun sha1(value: String): String =
            MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
