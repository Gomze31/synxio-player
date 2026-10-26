package fr.synxio.player.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class AlarmScheduleTest {

    /** Mercredi 21 octobre 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0)

    @Test
    fun `reveil unique plus tard dans la journee`() {
        assertEquals(LocalDateTime.of(2026, 10, 21, 11, 30), AlarmSchedule.nextTrigger(11, 30, 0, now))
    }

    @Test
    fun `reveil unique deja passe aujourd'hui sonne demain`() {
        assertEquals(LocalDateTime.of(2026, 10, 22, 7, 0), AlarmSchedule.nextTrigger(7, 0, 0, now))
    }

    @Test
    fun `l'heure exacte de maintenant est consideree passee`() {
        assertEquals(LocalDateTime.of(2026, 10, 22, 10, 0), AlarmSchedule.nextTrigger(10, 0, 0, now))
    }

    @Test
    fun `reveil de semaine le mercredi matin passe sonne jeudi`() {
        val weekdays = AlarmSchedule.mask(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        assertEquals(LocalDateTime.of(2026, 10, 22, 7, 0), AlarmSchedule.nextTrigger(7, 0, weekdays, now))
    }

    @Test
    fun `reveil du week-end saute jusqu'au samedi`() {
        val weekend = AlarmSchedule.mask(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), AlarmSchedule.nextTrigger(9, 0, weekend, now))
    }

    @Test
    fun `reveil du seul mercredi deja passe sonne la semaine suivante`() {
        val wednesday = AlarmSchedule.mask(DayOfWeek.WEDNESDAY)
        assertEquals(LocalDateTime.of(2026, 10, 28, 7, 0), AlarmSchedule.nextTrigger(7, 0, wednesday, now))
    }

    @Test
    fun `libelle des jours`() {
        assertEquals("Une fois", AlarmSchedule.daysLabel(0))
        assertEquals("Tous les jours", AlarmSchedule.daysLabel(AlarmSchedule.EVERY_DAY))
        assertEquals("En semaine", AlarmSchedule.daysLabel(AlarmSchedule.WEEKDAYS))
        assertEquals("Le week-end", AlarmSchedule.daysLabel(AlarmSchedule.mask(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        assertEquals("lun., mer.", AlarmSchedule.daysLabel(AlarmSchedule.mask(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
    }
}
