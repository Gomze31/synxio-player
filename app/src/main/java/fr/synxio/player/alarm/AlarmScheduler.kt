package fr.synxio.player.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.MainActivity
import fr.synxio.player.data.db.AlarmDao
import fr.synxio.player.data.db.AlarmEntity
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programme les réveils auprès d'Android.
 *
 * `setAlarmClock` plutôt qu'une alarme exacte ordinaire : le système la traite comme un
 * vrai réveil (icône dans la barre d'état, sortie du mode Doze, droit de lancer la lecture
 * depuis l'arrière-plan au moment où elle sonne).
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AlarmDao,
) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    /** Prochain déclenchement d'un réveil actif, en millisecondes epoch. */
    fun nextTriggerMillis(alarm: AlarmEntity, now: LocalDateTime = LocalDateTime.now()): Long =
        AlarmSchedule.nextTrigger(alarm.hour, alarm.minute, alarm.days, now)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) return cancel(alarm.id)
        setAt(alarm.id, nextTriggerMillis(alarm), snooze = false)
    }

    /** Fait resonner le réveil dans [minutes], sans toucher à sa programmation habituelle. */
    fun snooze(alarmId: Long, minutes: Int) {
        setAt(alarmId, System.currentTimeMillis() + minutes * 60_000L, snooze = true)
    }

    fun cancel(alarmId: Long) {
        manager.cancel(fireIntent(alarmId, snooze = false))
        manager.cancel(fireIntent(alarmId, snooze = true))
    }

    /** Reprogramme tout : au démarrage du téléphone, après un changement d'heure, une mise à jour. */
    suspend fun rescheduleAll() {
        dao.all().forEach(::schedule)
    }

    private fun setAt(alarmId: Long, triggerAt: Long, snooze: Boolean) {
        val show = PendingIntent.getActivity(
            context, REQUEST_SHOW,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fireIntent(alarmId, snooze))
        }.onFailure {
            // Permission d'alarme exacte retirée par l'utilisateur : on garde une alarme
            // approximative plutôt que de ne pas réveiller du tout.
            Log.w(TAG, "Alarme exacte refusée, repli sur une alarme approximative", it)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, fireIntent(alarmId, snooze))
        }
    }

    private fun fireIntent(alarmId: Long, snooze: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Un code par réveil (et par répétition) : sinon chaque programmation écraserait la précédente.
        (alarmId * 2 + if (snooze) 1 else 0).toInt(),
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "AlarmScheduler"
        const val REQUEST_SHOW = 0x5A1
    }
}
