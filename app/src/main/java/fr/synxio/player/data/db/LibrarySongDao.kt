package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibrarySongDao {

    @Query("SELECT * FROM songs")
    abstract suspend fun all(): List<LibrarySongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun byId(id: Long): LibrarySongEntity?

    @Query("SELECT id FROM songs")
    abstract suspend fun ids(): List<Long>

    @Query("SELECT COUNT(*) FROM songs")
    abstract suspend fun count(): Int

    @Upsert
    abstract suspend fun upsertAll(songs: List<LibrarySongEntity>)

    /** SQLite limite les variables liées : on supprime par paquets de 500. */
    @Query("DELETE FROM songs WHERE id IN (:ids)")
    protected abstract suspend fun deleteChunk(ids: List<Long>)

    suspend fun deleteByIds(ids: Collection<Long>) {
        ids.chunked(500).forEach { deleteChunk(it) }
    }

    @Query("DELETE FROM songs")
    abstract suspend fun clear()

    @Query("SELECT * FROM library_scan_state WHERE id = 0")
    abstract suspend fun scanState(): LibraryScanStateEntity?

    @Query("SELECT * FROM library_scan_state WHERE id = 0")
    abstract fun observeScanState(): Flow<LibraryScanStateEntity?>

    @Upsert
    abstract suspend fun saveScanState(state: LibraryScanStateEntity)

    @Query("DELETE FROM library_scan_state")
    abstract suspend fun clearScanState()

    /** Remplacement atomique : utilisé par le scan complet. */
    @Transaction
    open suspend fun replaceAll(songs: List<LibrarySongEntity>, state: LibraryScanStateEntity) {
        clear()
        upsertAll(songs)
        saveScanState(state)
    }

    /** Application atomique d'un diff : utilisé par le scan incrémental. */
    @Transaction
    open suspend fun applyDiff(upserts: List<LibrarySongEntity>, deletions: Collection<Long>, state: LibraryScanStateEntity) {
        if (upserts.isNotEmpty()) upsertAll(upserts)
        if (deletions.isNotEmpty()) deleteByIds(deletions)
        saveScanState(state)
    }
}
