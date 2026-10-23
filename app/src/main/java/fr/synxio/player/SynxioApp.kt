package fr.synxio.player

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import dagger.hilt.android.HiltAndroidApp

/**
 * YoutubeDL et FFmpeg ne sont pas initialisés ici : au premier lancement, leur `init`
 * extrait Python et FFmpeg sur le stockage, ce qui bloquait le thread principal au
 * démarrage. `DownloadRepository` les initialise à la demande, sur IO.
 */
@HiltAndroidApp
class SynxioApp : Application(), SingletonImageLoader.Factory {

    /**
     * Les pochettes viennent toutes de MediaStore : un cache disque généreux évite
     * de re-décoder les mêmes JPEG à chaque scroll de la bibliothèque.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork").toOkioPath())
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
