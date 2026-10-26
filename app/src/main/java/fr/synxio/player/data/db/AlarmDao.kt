package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.PrimaryKey
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Ce que le réveil fait jouer. */
enum class AlarmSource(val label: String) {
    FAVORITES("Mes favoris"),
    DAILY_MIX("Ton mix du jour"),
    SHUFFLE("Toute la bibliothèque, au hasard"),
    PLAYLIST("Une playlist"),
}

/**
 * Un réveil en musique.
 *
 * [days] : masque de jours de `AlarmSchedule` (0 = une seule fois). [source] est stocké
 * par son nom : ajouter une source ne casse pas les réveils existants.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val source: String,
    val playlistId: Long?,
    /** Durée de la montée du volume, en secondes ; 0 = volume plein d'emblée. */
    val rampSeconds: Int,
    val enabled: Boolean,
) {
    val sourceKind: AlarmSource
        get() = AlarmSource.entries.firstOrNull { it.name == source } ?: AlarmSource.FAVORITES
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)
}
