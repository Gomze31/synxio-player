package fr.synxio.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcast_progress")
data class PodcastProgressEntity(
    @PrimaryKey val songId: Long,
    val positionMs: Long,
    val lastPlayedSec: Long,
)
