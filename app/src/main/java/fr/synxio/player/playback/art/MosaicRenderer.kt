package fr.synxio.player.playback.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.repo.MusicRepository
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mosaïque 2×2 des pochettes de quatre titres, pour les mixs, souvenirs et playlists
 * sur l'écran de la voiture. Rendue une fois par liste de titres et par taille, gardée
 * dans le cache ; les mixs changeant chaque jour, les fichiers de plus d'une semaine
 * sont balayés à chaque nouveau rendu.
 */
@Singleton
class MosaicRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val music: MusicRepository,
) {

    fun render(songIds: List<Long>, side: Int): File? {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val target = File(dir, sha1(songIds.joinToString(",") + "@" + side) + ".jpg")
        if (target.isFile) return target

        val half = side / 2
        val tiles = songIds.mapNotNull { music.songById(it)?.artworkUri }
            .mapNotNull { ArtBitmaps.decode(context, it, half) }
        if (tiles.isEmpty()) return null

        return runCatching {
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            for (i in 0 until 4) {
                // Moins de quatre pochettes : on répète dans l'ordre.
                val square = ArtBitmaps.centerCrop(tiles[i % tiles.size], half)
                canvas.drawBitmap(square, ((i % 2) * half).toFloat(), ((i / 2) * half).toFloat(), null)
            }
            tiles.forEach { it.recycle() }
            val tmp = File(dir, target.name + ".tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            out.recycle()
            if (!tmp.renameTo(target)) tmp.delete()
            sweep(dir, keep = target.name)
            target.takeIf { it.isFile }
        }.getOrNull()
    }

    private fun sweep(dir: File, keep: String) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.name != keep && it.lastModified() < cutoff) it.delete() }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR = "auto-mosaic"
        const val QUALITY = 88
        const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    }
}
