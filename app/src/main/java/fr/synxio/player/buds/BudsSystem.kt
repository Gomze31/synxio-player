package fr.synxio.player.buds

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build

/** État du son spatial d'Android (Spatializer, Android 12L+) pour la sortie en cours. */
data class SpatialStatus(
    /** Le téléphone sait spatialiser. */
    val supported: Boolean,
    /** La sortie actuelle le permet (casque compatible branché). */
    val available: Boolean,
    /** Activé dans les réglages Bluetooth du système. */
    val enabled: Boolean,
    /** Les écouteurs suivent les mouvements de la tête. */
    val headTracking: Boolean,
) {
    companion object {
        val Unsupported = SpatialStatus(supported = false, available = false, enabled = false, headTracking = false)
    }
}

object SpatialAudio {

    fun status(context: Context): SpatialStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return SpatialStatus.Unsupported
        val spatializer = context.getSystemService(AudioManager::class.java)?.spatializer
            ?: return SpatialStatus.Unsupported
        return runCatching {
            SpatialStatus(
                supported = spatializer.immersiveAudioLevel != android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE,
                available = spatializer.isAvailable,
                enabled = spatializer.isEnabled,
                headTracking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && spatializer.isHeadTrackerAvailable,
            )
        }.getOrDefault(SpatialStatus.Unsupported)
    }
}

/**
 * Galaxy Wearable règle ce qu'aucune API publique n'expose : réduction de bruit, son
 * ambiant, détection du port, localisation des écouteurs.
 */
object GalaxyWearable {

    const val PACKAGE = "com.samsung.android.app.watchmanager"

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    /** Ouvre Galaxy Wearable, ou sa fiche du Play Store s'il manque. */
    fun open(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        val intents = listOfNotNull(
            launch,
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")),
        )
        return intents.any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }
}
