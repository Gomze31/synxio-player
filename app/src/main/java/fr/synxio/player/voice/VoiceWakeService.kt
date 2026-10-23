package fr.synxio.player.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.MainActivity
import fr.synxio.player.R
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.di.ApplicationScope
import fr.synxio.player.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Écoute permanente du mot-clé « Synxio », écran éteint compris.
 *
 * Service de premier plan de type « micro » : Android ne laisse le micro qu'à un service
 * démarré pendant que l'app est visible, d'où un démarrage piloté par l'interface
 * ([VoiceWakeController]) et non au boot ni en « sticky » (un redémarrage en arrière-plan
 * n'aurait pas le droit d'écouter).
 *
 * La reconnaissance tourne hors ligne (Vosk) sur une grammaire fermée : le son ne quitte
 * jamais le téléphone et rien n'est enregistré.
 */
@AndroidEntryPoint
class VoiceWakeService : Service() {

    @Inject lateinit var player: PlayerConnection
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var models: VoskModelStore
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val main = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    @Volatile private var listening = false
    private var worker: Thread? = null

    /** Fin de la fenêtre d'écoute ouverte par le mot-clé seul (horloge `elapsedRealtime`). */
    @Volatile private var windowUntil = 0L
    private var duckRequest: AudioFocusRequest? = null
    private val tones by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Arrêt depuis la notification : on coupe aussi le réglage, sinon l'écoute
            // reprendrait à la prochaine ouverture de l'app.
            appScope.launch { settings.setVoiceWakeEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canListen(this, models)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }
        if (started.isFailure) {
            // Démarré depuis l'arrière-plan : Android refuse le micro.
            Log.w(TAG, "Écoute impossible à démarrer", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) startListening()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listening = false
        worker?.interrupt()
        closeWindow()
        tones?.release()
        super.onDestroy()
    }

    private fun startListening() {
        listening = true
        worker = Thread({ listenLoop() }, "voice-wake").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    private fun listenLoop() {
        val model = runCatching { Model(models.modelDir.absolutePath) }.getOrElse {
            Log.w(TAG, "Modèle vocal illisible", it)
            main.post { stopSelf() }
            return
        }
        val grammar = JSONArray(VoiceGrammar.phrases()).toString()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            @Suppress("MissingPermission") // vérifiée par canListen avant le démarrage
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME * 2 * 4),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Micro indisponible", e)
            model.close()
            main.post { stopSelf() }
            return
        }
        // Le haut-parleur joue la musique juste à côté du micro : l'annulation d'écho
        // (quand l'appareil la propose) retire une partie du son lu.
        val effects = listOfNotNull(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId) else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null,
        ).onEach { it.enabled = true }

        val recognizer = Recognizer(model, SAMPLE_RATE.toFloat(), grammar)
        val buffer = ShortArray(FRAME)
        try {
            record.startRecording()
            while (listening && !Thread.currentThread().isInterrupted) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                if (recognizer.acceptWaveForm(buffer, read)) {
                    onHeard(JSONObject(recognizer.result).optString("text"))
                }
                if (windowUntil != 0L && SystemClock.elapsedRealtime() > windowUntil) closeWindow()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Écoute interrompue", e)
        } finally {
            runCatching { record.stop() }
            record.release()
            effects.forEach { it.release() }
            recognizer.close()
            model.close()
        }
    }

    private fun onHeard(text: String) {
        when (val heard = VoiceGrammar.parse(text)) {
            VoiceHeard.Wake -> openWindow()
            is VoiceHeard.Command -> {
                val windowOpen = SystemClock.elapsedRealtime() <= windowUntil
                // Sans le mot-clé, une phrase n'est prise que juste après lui : une conversation
                // ou les paroles d'une chanson ne pilotent pas le lecteur.
                if (heard.afterWake || windowOpen) {
                    closeWindow()
                    main.post { execute(heard.command) }
                    tones?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                }
            }
            VoiceHeard.Nothing -> Unit
        }
    }

    /** Mot-clé seul : on baisse la musique et on écoute la commande quelques secondes. */
    @Synchronized
    private fun openWindow() {
        windowUntil = SystemClock.elapsedRealtime() + COMMAND_WINDOW_MS
        if (duckRequest == null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audio.requestAudioFocus(request)
            duckRequest = request
        }
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    @Synchronized
    private fun closeWindow() {
        windowUntil = 0L
        duckRequest?.let { audio.abandonAudioFocusRequest(it) }
        duckRequest = null
    }

    private fun execute(command: VoiceCommand) {
        when (command) {
            VoiceCommand.Play -> player.resume()
            VoiceCommand.Pause -> player.pause()
            VoiceCommand.Next -> player.next()
            VoiceCommand.Previous -> player.previous()
            VoiceCommand.Restart -> player.seekTo(0L)
            is VoiceCommand.SeekBy -> player.seekBy(command.deltaMs)
            is VoiceCommand.Shuffle -> command.enabled?.let(player::setShuffle) ?: player.toggleShuffle()
            VoiceCommand.CycleRepeat -> player.cycleRepeat()
            // Deux crans : un seul, sur une échelle de 15, s'entend à peine.
            VoiceCommand.VolumeUp -> repeat(2) { adjustVolume(AudioManager.ADJUST_RAISE) }
            VoiceCommand.VolumeDown -> repeat(2) { adjustVolume(AudioManager.ADJUST_LOWER) }
            VoiceCommand.Mute -> adjustVolume(AudioManager.ADJUST_MUTE)
            VoiceCommand.Unmute -> adjustVolume(AudioManager.ADJUST_UNMUTE)
            is VoiceCommand.SetVolume -> {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (max * command.percent / 100f).roundToInt().coerceIn(0, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            }
            is VoiceCommand.SleepTimer -> player.startSleepTimer(command.minutes * 60_000L, finishCurrentTrack = false)
            VoiceCommand.CancelSleepTimer -> player.cancelSleepTimer()
        }
    }

    private fun adjustVolume(direction: Int) =
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Commande vocale", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Affichée tant que Synxio écoute le mot-clé"
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceWakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_mic)
            .setContentTitle("Commande vocale active")
            .setContentText("Dis « Synxio » puis ta commande")
            .setContentIntent(open)
            .addAction(0, "Arrêter l'écoute", stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "VoiceWake"
        private const val SAMPLE_RATE = 16_000
        /** 100 ms de son par lecture. */
        private const val FRAME = 1_600
        private const val COMMAND_WINDOW_MS = 6_000L
        private const val NOTIFICATION_ID = 0x5701
        private const val CHANNEL_ID = "voice_wake"
        const val ACTION_STOP = "fr.synxio.player.voice.STOP"

        fun canListen(context: Context, models: VoskModelStore): Boolean =
            models.isReady && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
}
