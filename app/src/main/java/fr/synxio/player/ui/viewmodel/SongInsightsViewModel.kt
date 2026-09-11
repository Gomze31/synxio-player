package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.LoudnessRepository
import fr.synxio.player.data.repo.MusicRepository
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ce que l'application a appris d'un morceau à force de l'écouter.
 *
 * Ces chiffres existaient déjà — compteurs d'écoute, coupures, niveau mesuré pour la
 * normalisation — mais n'étaient visibles nulle part à l'échelle du morceau.
 */
data class SongInsights(
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAt: Long,
    /** Part moyenne du morceau réellement écoutée, ou null si jamais joué. */
    val completion: Float?,
    /** Niveau mesuré en dBFS, ou null si le morceau n'a pas été analysé. */
    val loudnessDbfs: Float?,
) {
    val hasHistory: Boolean get() = playCount > 0 || skipCount > 0

    val lastPlayedLabel: String?
        get() = lastPlayedAt.takeIf { it > 0 }?.let {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
        }

    val completionLabel: String?
        get() = completion?.let { "${(it * 100).roundToInt()} %" }

    val loudnessLabel: String?
        get() = loudnessDbfs?.takeIf { it <= 0f }?.let { "%.1f dBFS".format(it) }
}

@HiltViewModel
class SongInsightsViewModel @Inject constructor(
    private val music: MusicRepository,
    private val loudness: LoudnessRepository,
) : ViewModel() {

    fun insightsFor(song: Song): SongInsights {
        val stat = music.allStats.value[song.id]
        val level = loudness.levels.value[song.path]

        // Le taux d'écoute est plafonné à 1 : réécouter un passage en boucle peut faire
        // dépasser la durée du fichier et afficherait « 140 % ».
        val completion = stat?.takeIf { it.playCount > 0 && song.durationMs > 0 }?.let {
            min(1f, it.totalListenedMs / (song.durationMs.toFloat() * it.playCount))
        }

        return SongInsights(
            playCount = stat?.playCount ?: 0,
            skipCount = stat?.skipCount ?: 0,
            lastPlayedAt = stat?.lastPlayedAt ?: 0L,
            completion = completion,
            loudnessDbfs = level,
        )
    }
}
