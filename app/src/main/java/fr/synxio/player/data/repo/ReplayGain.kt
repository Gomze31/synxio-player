package fr.synxio.player.data.repo

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File

/**
 * Lecture du tag ReplayGain d'un fichier.
 *
 * ReplayGain stocke une *correction* relative à sa référence (-18 dBFS chez la plupart
 * des encodeurs, selon la spécification RG2 / EBU R128). Le niveau du fichier est donc
 * la référence moins la correction annoncée.
 */
internal object ReplayGain {

    const val REFERENCE_DBFS = -18f

    /**
     * Valeur sentinelle pour « fichier illisible ».
     *
     * Positive, donc impossible pour un vrai signal numérique, qui ne peut pas dépasser
     * 0 dBFS. Aucune mesure légitime ne peut entrer en collision.
     */
    const val UNMEASURABLE_DBFS = 1f

    private val KEYS = listOf("REPLAYGAIN_TRACK_GAIN", "replaygain_track_gain")

    /** Niveau dBFS équivalent au tag, ou null si le fichier n'en porte pas de lisible. */
    suspend fun dbfs(path: String): Float? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.canRead()) return@runCatching null
            val tag = AudioFileIO.read(file).tag ?: return@runCatching null

            val raw = KEYS.firstNotNullOfOrNull { key ->
                tag.getFirst(key)?.takeIf { it.isNotBlank() }
            } ?: return@runCatching null

            // Format typique : « -7.06 dB ».
            val gain = raw.replace("dB", "", ignoreCase = true).trim().toFloatOrNull()
                ?: return@runCatching null

            (REFERENCE_DBFS - gain).coerceIn(-70f, 0f)
        }.getOrElse {
            Log.d(TAG, "Pas de tag ReplayGain lisible pour ${path.substringAfterLast('/')}")
            null
        }
    }

    private const val TAG = "ReplayGain"
}
