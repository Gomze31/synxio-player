package fr.synxio.player.data.meta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pochettes que Synxio a dû produire lui-même : téléchargées en ligne, ou extraites d'un
 * fichier dont MediaStore ne sait pas tirer de miniature.
 *
 * Dans `files/` et non dans le cache : Android vide le cache quand la place manque, et
 * il faudrait alors tout retélécharger. Le nom est l'empreinte de l'image : une nouvelle
 * pochette a une nouvelle adresse, ce qui invalide d'office le cache de Coil.
 */
@Singleton
class ArtworkStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, DIR)

    /** Enregistre [bytes] réduite à [MAX_SIDE] px, et renvoie le fichier, ou `null` si l'image est illisible. */
    fun save(bytes: ByteArray): File? = runCatching {
        dir.mkdirs()
        val target = File(dir, sha1(bytes) + ".jpg")
        if (target.exists()) return@runCatching target

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val scaled = scaleDown(bitmap)
        val tmp = File(dir, target.name + ".tmp")
        tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        if (!tmp.renameTo(target)) tmp.delete()
        target.takeIf { it.exists() }
    }.onFailure { Log.w(TAG, "Pochette non enregistrée", it) }.getOrNull()

    /** Supprime les pochettes qu'aucun morceau ne référence plus. */
    fun prune(keep: Set<String>) {
        dir.listFiles()?.forEach { file -> if (file.path !in keep) file.delete() }
    }

    fun owns(path: String): Boolean = path.startsWith(dir.path)

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= MAX_SIDE) return bitmap
        val ratio = MAX_SIDE.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "ArtworkStore"
        const val DIR = "meta-artwork"
        /** Assez pour le lecteur plein écran, sans stocker des scans de 3000 px. */
        const val MAX_SIDE = 800
        const val JPEG_QUALITY = 88
    }
}
