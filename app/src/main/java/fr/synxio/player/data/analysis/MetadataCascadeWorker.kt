package fr.synxio.player.data.analysis

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.synxio.player.data.meta.MetadataEnricher
import kotlinx.coroutines.CancellationException

/**
 * Passe automatique de la cascade de métadonnées (tags, pochettes, MusicBrainz), hors
 * processus de l'interface : reprend après une mort du processus, attend une batterie
 * suffisante. Les demandes manuelles restent en processus, l'utilisateur les attend.
 */
@HiltWorker
class MetadataCascadeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val enricher: MetadataEnricher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        enricher.runAutoPass()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Pas de nouvel essai automatique : la prochaine synchronisation relancera.
        Log.w(TAG, "Cascade de métadonnées interrompue", e)
        Result.failure()
    }

    companion object {
        const val NAME = "metadata-cascade"
        private const val TAG = "MetadataCascade"
    }
}
