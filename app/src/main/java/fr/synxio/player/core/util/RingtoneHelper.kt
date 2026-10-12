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
import android.util.Log
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
     * Marque le fichier comme sonnerie dans le MediaStore (si l'app en a le droit),
     * puis utilise RingtoneManager pour l'appliquer.
     *
     * IS_MUSIC n'est jamais modifié : le passer à 0 ferait disparaître le titre de la
     * bibliothèque (le scanner filtre `IS_MUSIC != 0`).
     *
     * @return false (avec un message) en cas d'échec, sans jamais faire planter l'app.
     */
    suspend fun setAsRingtone(context: Context, song: Song): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteSettingsPermission(context)) {
            showToast(context, "Autorisation « Modifier les paramètres système » requise")
            return@withContext false
        }

        try {
            val file = File(song.path)
            if (!file.exists()) {
                showToast(context, "Fichier introuvable")
                return@withContext false
            }

            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)

            // Marquage « sonnerie » : facultatif. Depuis Android 10, modifier une entrée
            // MediaStore créée par une autre app lève une SecurityException — on continue.
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                }
                context.contentResolver.update(uri, values, null, null)
            } catch (e: SecurityException) {
                Log.w(TAG, "Marquage IS_RINGTONE refusé, sonnerie appliquée sans", e)
            }

            // Définit la sonnerie
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                uri
            )

            showToast(context, "Sonnerie modifiée avec succès !")
            true
        } catch (e: SecurityException) {
            // Autorisation retirée entre-temps, ou refus du système : échec propre.
            Log.w(TAG, "Modification de la sonnerie refusée", e)
            showToast(context, "Autorisation refusée : impossible de modifier la sonnerie")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Modification de la sonnerie impossible", e)
            showToast(context, "Erreur lors de la modification de la sonnerie")
            false
        }
    }

    private suspend fun showToast(context: Context, message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private const val TAG = "RingtoneHelper"
}
