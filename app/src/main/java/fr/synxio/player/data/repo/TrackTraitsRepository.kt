package fr.synxio.player.data.repo

import fr.synxio.player.data.db.AudioFeatureDao
import fr.synxio.player.data.db.AudioFeatureEntity
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.TrackTraits
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Traits (tempo, tonalité, énergie, intro/outro) par chemin de fichier, lus dans `audio_features`. */
@Singleton
class TrackTraitsRepository @Inject constructor(
    dao: AudioFeatureDao,
    @ApplicationScope scope: CoroutineScope,
) {
    val traits: StateFlow<Map<String, TrackTraits>> = dao.observeAll()
        .map { list -> list.filter { it.analysisVersion >= TrackTraits.VERSION }.associate { it.songPath to it.toTraits() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun traitsFor(song: Song): TrackTraits? = traits.value[song.path]

    fun pendingCount(songs: List<Song>): Int {
        val known = traits.value
        return songs.count { !it.isPodcast && !it.isAudiobook && it.path !in known }
    }
}

internal fun AudioFeatureEntity.toTraits() = TrackTraits(
    bpm = bpm,
    bpmConfidence = bpmConfidence,
    keyIndex = keyIndex,
    keyConfidence = keyConfidence,
    energy = energy,
    introMs = introMs,
    outroMs = outroMs,
)
