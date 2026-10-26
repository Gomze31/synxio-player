package fr.synxio.player.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.MainActivity
import fr.synxio.player.R
import fr.synxio.player.data.db.AlarmDao
import fr.synxio.player.data.db.AlarmEntity
import fr.synxio.player.data.db.AlarmSource
import fr.synxio.player.data.model.DailyMixKind
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.DailyMixRepository
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.PlaylistRepository
import fr.synxio.player.di.ApplicationScope
import fr.synxio.player.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Fait sonner un réveil : lance la musique sur le flux alarme, avec montée du volume
 * (voir `PlaybackService.enterAlarmMode`), affiche la notification
 * « Arrêter / Répéter », puis programme la prochaine occurrence.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: AlarmDao
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var music: MusicRepository
    @Inject lateinit var mixes: DailyMixRepository
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var player: PlayerConnection
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, alarmId, snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false))
                    ACTION_STOP -> stop(context)
                    ACTION_SNOOZE -> {
                        stop(context)
                        scheduler.snooze(alarmId, SNOOZE_MINUTES)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Réveil $alarmId : action ${intent.action} impossible", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, alarmId: Long, snooze: Boolean) {
        val alarm = dao.get(alarmId) ?: return
        // Une répétition reste due même si le réveil a été coupé entre-temps : l'utilisateur
        // l'a demandée explicitement depuis la notification.
        if (!alarm.enabled && !snooze) return
        if (!snooze) {
            if (alarm.days == 0) dao.setEnabled(alarm.id, false) else scheduler.schedule(alarm)
        }

        notify(context, alarm)
        val songs = songsFor(alarm)
        if (songs.isEmpty()) {
            Log.w(TAG, "Réveil $alarmId : aucun titre à jouer")
            return
        }
        withContext(Dispatchers.Main) { player.playAlarm(songs, alarm.rampSeconds) }
    }

    /** La pause suffit : le service quitte alors le mode alarme et rend le flux musique. */
    private suspend fun stop(context: Context) {
        withContext(Dispatchers.Main) { player.pause() }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /**
     * Les titres du réveil. Appli fermée, la bibliothèque se charge à peine : on l'attend
     * un peu, et une source vide (playlist supprimée, aucun favori) retombe sur l'aléatoire
     * plutôt que de laisser le réveil muet.
     */
    private suspend fun songsFor(alarm: AlarmEntity): List<Song> {
        val library = withTimeoutOrNull(LIBRARY_WAIT_MS) {
            music.library.first { it.songs.isNotEmpty() }
        }?.songs.orEmpty()
        if (library.isEmpty()) return emptyList()

        val chosen = when (alarm.sourceKind) {
            // Flux de Room encore à froid si l'appli était fermée : on laisse aux favoris
            // le temps d'arriver plutôt que de lire une valeur initiale vide.
            AlarmSource.FAVORITES -> {
                val ids = withTimeoutOrNull(FAVORITES_WAIT_MS) {
                    music.favoriteIds.first { it.isNotEmpty() }
                }.orEmpty()
                library.filter { it.id in ids }.shuffled()
            }
            AlarmSource.DAILY_MIX -> withTimeoutOrNull(MIX_WAIT_MS) {
                mixes.mixes.first { it.isNotEmpty() }
            }?.firstOrNull { it.kind == DailyMixKind.DAILY }?.songs.orEmpty()
            AlarmSource.PLAYLIST -> alarm.playlistId
                ?.let { playlists.observePlaylist(it).first() }?.songs.orEmpty()
            AlarmSource.SHUFFLE -> emptyList()
        }
        return chosen.ifEmpty { music.withoutDisliked(library).shuffled().take(SHUFFLE_SIZE) }
    }

    private fun notify(context: Context, alarm: AlarmEntity) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Réveils", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Affichée quand un réveil en musique sonne"
                    setSound(null, null)
                }
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle("Réveil · %02d:%02d".format(alarm.hour, alarm.minute))
            .setContentText(alarm.sourceKind.label)
            .setContentIntent(open)
            .addAction(0, "Arrêter", action(context, ACTION_STOP, alarm.id))
            .addAction(0, "Répéter dans $SNOOZE_MINUTES min", action(context, ACTION_SNOOZE, alarm.id))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Notification de réveil refusée", it) }
    }

    private fun action(context: Context, action: String, alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ACTION_FIRE = "fr.synxio.player.alarm.FIRE"
        const val ACTION_STOP = "fr.synxio.player.alarm.STOP"
        const val ACTION_SNOOZE = "fr.synxio.player.alarm.SNOOZE"
        const val EXTRA_ALARM_ID = "alarmId"
        const val EXTRA_SNOOZE = "snooze"

        private const val TAG = "AlarmReceiver"
        private const val CHANNEL_ID = "alarms"
        /** Retirée aussi par le service de lecture quand le réveil est mis en pause ailleurs. */
        const val NOTIFICATION_ID = 0x5A2
        private const val SNOOZE_MINUTES = 10
        private const val SHUFFLE_SIZE = 100
        private const val LIBRARY_WAIT_MS = 20_000L
        private const val MIX_WAIT_MS = 8_000L
        private const val FAVORITES_WAIT_MS = 3_000L
        private const val NOTIFICATION_TIMEOUT_MS = 30 * 60_000L
    }
}

/** Reprogramme les réveils quand Android les a oubliés : redémarrage, changement d'heure, mise à jour. */
@AndroidEntryPoint
class AlarmBootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: AlarmScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
