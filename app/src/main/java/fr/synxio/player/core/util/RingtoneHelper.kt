package fr.synxio.player.core.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RingtoneHelper {

    /** Vérifie si l'application a l'autorisation de modifier les paramètres système. */
    fun hasWriteSettingsPermission(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    /** Renvoie l'Intent pour ouvrir l'écran de demande d'autorisation système. */
    fun getWriteSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Définit le morceau donné comme sonnerie par défaut du téléphone.
     * Met à jour le MediaStore pour que le fichier soit reconnu comme sonnerie,
     * puis utilise RingtoneManager pour l'appliquer.
     */
    suspend fun setAsRingtone(context: Context, song: Song): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteSettingsPermission(context)) return@withContext false

        try {
            val file = File(song.path)
            if (!file.exists()) return@withContext false

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
                put(MediaStore.Audio.Media.IS_ALARM, false)
                put(MediaStore.Audio.Media.IS_MUSIC, false)
            }

            // Met à jour la base de données MediaStore
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
            context.contentResolver.update(uri, values, null, null)

            // Définit la sonnerie
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                uri
            )
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Sonnerie modifiée avec succès !", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur lors de la modification de la sonnerie", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}
