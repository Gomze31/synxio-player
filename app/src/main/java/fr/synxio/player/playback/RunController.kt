package fr.synxio.player.playback

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.media3.common.Player
import fr.synxio.player.data.model.CadenceEstimator
import fr.synxio.player.data.model.FlowTrack
import fr.synxio.player.data.model.RunEngine
import fr.synxio.player.data.model.toFlowTrack
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.TrackTraitsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Côté service de la course au tempo. Tant que [RunSession] est active : écoute le
 * podomètre (`TYPE_STEP_DETECTOR`, un événement par pas), met à jour la cadence et la
 * cible, et garde **un** titre d'avance juste après le titre en cours, choisi par
 * [RunEngine.pick] parmi les titres analysés (jamais un podcast ni un livre audio). Quand
 * ce titre démarre, sa vitesse est exposée par [speedOverride] et appliquée par
 * `PlaybackService.applySpeed()` (tonalité 1,0). Rien n'est écrit dans les réglages.
 *
 * La course s'arrête d'elle-même après [FOLLOW_TIMEOUT_MS] sans pas en mode « Suivre ».
 * Tout tourne sur le fil principal (capteur, lecteur, scope du service).
 */
class RunController(
    private val sensorManager: SensorManager?,
    private val player: () -> Player,
    private val session: RunSession,
    private val musicRepository: MusicRepository,
    private val trackTraits: TrackTraitsRepository,
    private val scope: CoroutineScope,
    private val onSpeedChanged: () -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val random: Random = Random.Default,
) : SensorEventListener {

    private val estimator = CadenceEstimator()
    private var listening = false
    private var tickJob: Job? = null
    private var startedAt = 0L
    private var lastStepAt: Long? = null

    /** Titre d'avance inséré dans la file, et la vitesse qui l'attend. */
    private var pendingId: Long? = null
    private var pendingSpeed = 1f
    /** Titres joués ou choisis pendant la course : on les évite pour ne pas tourner en boucle. */
    private val recent = ArrayDeque<Long>()

    /** Vitesse du titre choisi en cours de lecture ; null quand la vitesse habituelle s'applique. */
    var speedOverride: Float? = null
        private set

    fun start() {
        session.state
            .map { it.active to it.sensorAllowed }
            .distinctUntilChanged()
            .onEach { (active, allowed) -> if (active) begin(allowed) else end() }
            .launchIn(scope)
        // La cible change par la cadence (ici) ou par l'écran (verrouillage) : dans les deux
        // cas le titre d'avance est rechoisi.
        session.state
            .map { it.active to it.target }
            .distinctUntilChanged()
            .onEach { (active, _) -> if (active && startedAt > 0L) ensureNext() }
            .launchIn(scope)
    }

    fun release() {
        end()
    }

    // --- Cycle de vie ---------------------------------------------------------------------

    private fun begin(sensorAllowed: Boolean) {
        if (startedAt == 0L) {
            startedAt = clock()
            lastStepAt = null
            estimator.reset()
            recent.clear()
            pendingId = null
            player().currentMediaItem?.songId?.takeIf { it > 0 }?.let(::remember)
            refreshCurrent()
            tickJob?.cancel()
            tickJob = scope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    tick()
                }
            }
        }
        if (sensorAllowed) listen() else unlisten()
    }

    private fun end() {
        if (startedAt == 0L) return
        startedAt = 0L
        unlisten()
        tickJob?.cancel()
        tickJob = null
        estimator.reset()
        // Le titre d'avance reste dans la file : il est au bon tempo, le retirer surprendrait.
        pendingId = null
        if (speedOverride != null) {
            speedOverride = null
            onSpeedChanged()
        }
    }

    private fun listen() {
        if (listening) return
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) {
            session.update { it.copy(sensorMissing = true) }
            return
        }
        listening = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    private fun unlisten() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
    }

    // --- Capteur et horloge ---------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR || startedAt == 0L) return
        val now = clock()
        lastStepAt = now
        estimator.onStep(now)
        publishCadence()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun tick() {
        val now = clock()
        estimator.tick(now)
        publishCadence()
        val state = session.state.value
        if (state.mode == RunMode.FOLLOW && now - (lastStepAt ?: startedAt) > FOLLOW_TIMEOUT_MS) {
            session.stop()
        }
    }

    private fun publishCadence() {
        val cadence = estimator.cadence
        val stable = estimator.stable
        session.update { s ->
            val target = RunEngine.retarget(s.target, cadence, stable)
            if (s.cadence == cadence && s.stable == stable && s.target == target) s
            else s.copy(cadence = cadence, stable = stable, target = target)
        }
    }

    // --- File et vitesse ------------------------------------------------------------------

    /** Appelé par le service à chaque changement de titre. */
    fun onTransition() {
        if (startedAt == 0L) return
        val p = player()
        val currentId = p.currentMediaItem?.songId
        val override = if (currentId != null && currentId == pendingId) {
            pendingId = null
            remember(currentId)
            pendingSpeed
        } else null
        if (override != speedOverride) {
            speedOverride = override
            onSpeedChanged()
        }
        refreshCurrent()
        if (pendingIndex() == null) ensureNext()
    }

    /** Garde un titre d'avance pour la cible courante ; remplace l'ancien s'il n'a pas encore joué. */
    private fun ensureNext() {
        val p = player()
        val target = session.state.value.target
        removePending()
        if (!target.hasTarget) {
            session.update { it.copy(nextId = null, nextTitle = null, nextArtist = null, nextBpm = null, nextSpeed = 1f, noMatch = false) }
            return
        }
        val recentIds = buildSet {
            addAll(recent)
            p.currentMediaItem?.songId?.let(::add)
        }
        val pick = RunEngine.pick(target.bpm, candidates(), recentIds, random)
        val song = pick?.let { musicRepository.songById(it.id) }
        if (pick == null || song == null) {
            session.update { it.copy(nextId = null, nextTitle = null, nextArtist = null, nextBpm = null, nextSpeed = 1f, noMatch = true) }
            return
        }
        if (p.mediaItemCount == 0) {
            p.setMediaItem(song.toMediaItem())
            p.prepare()
            p.play()
        } else {
            p.addMediaItem(p.currentMediaItemIndex + 1, song.toMediaItem())
        }
        pendingId = pick.id
        pendingSpeed = pick.speed
        val bpm = trackTraits.traitsFor(song)?.takeIf { it.hasBpm }?.bpm
        session.update {
            it.copy(nextId = pick.id, nextTitle = song.title, nextArtist = song.displayArtist, nextBpm = bpm, nextSpeed = pick.speed, noMatch = false)
        }
    }

    private fun pendingIndex(): Int? {
        val id = pendingId ?: return null
        val p = player()
        for (i in p.currentMediaItemIndex + 1 until p.mediaItemCount) {
            if (p.getMediaItemAt(i).songId == id) return i
        }
        return null
    }

    private fun removePending() {
        pendingIndex()?.let { player().removeMediaItem(it) }
        pendingId = null
    }

    private fun refreshCurrent() {
        val song = player().currentMediaItem?.songId?.let(musicRepository::songById)
        val bpm = song?.let(trackTraits::traitsFor)?.takeIf { it.hasBpm }?.bpm
        val speed = speedOverride ?: 1f
        session.update { if (it.currentBpm == bpm && it.currentSpeed == speed) it else it.copy(currentBpm = bpm, currentSpeed = speed) }
    }

    /** Titres analysés de la bibliothèque, hors podcasts et livres audio. */
    private fun candidates(): List<FlowTrack> =
        musicRepository.library.value.songs.mapNotNull { song ->
            if (song.isPodcast || song.isAudiobook) return@mapNotNull null
            val traits = trackTraits.traitsFor(song)?.takeIf { it.hasBpm } ?: return@mapNotNull null
            song.toFlowTrack(song.id, traits)
        }

    private fun remember(id: Long) {
        recent.remove(id)
        recent.addLast(id)
        while (recent.size > RECENT_LIMIT) recent.removeFirst()
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val FOLLOW_TIMEOUT_MS = 10 * 60_000L
        const val RECENT_LIMIT = 30
    }
}
