package fr.synxio.player.data.repo

import android.util.Log
import fr.synxio.player.data.db.LibraryScanStateEntity
import fr.synxio.player.data.db.LibrarySongDao
import fr.synxio.player.data.media.MediaStoreScanner
import fr.synxio.player.data.model.LibraryDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncMode { INCREMENTAL, FULL }

data class SyncResult(val mode: SyncMode, val changed: Boolean, val durationMs: Long)

/**
 * Synchronise le cache Room avec MediaStore.
 *
 * Incrémental (Android 11+) : si la génération et le nombre d'ids n'ont pas bougé, rien
 * n'est lu ; sinon `GENERATION_MODIFIED` donne les ajouts et modifications, la liste des
 * ids donne les suppressions. Complet : tout est relu et remplacé dans une transaction.
 * L'appelant tient le verrou (`MusicRepository.refreshMutex`) : une seule synchronisation
 * à la fois.
 */
@Singleton
class LibrarySync @Inject constructor(
    private val scanner: MediaStoreScanner,
    private val dao: LibrarySongDao,
) {

    suspend fun sync(requested: SyncMode): SyncResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val state = dao.scanState()
        val mode = when {
            requested == SyncMode.FULL -> SyncMode.FULL
            !scanner.supportsIncremental -> SyncMode.FULL
            state == null -> SyncMode.FULL
            state.scannerVersion != MediaStoreScanner.SCANNER_VERSION -> SyncMode.FULL
            state.volumeName != scanner.volumeName() -> SyncMode.FULL
            else -> SyncMode.INCREMENTAL
        }
        val changed = runCatching {
            if (mode == SyncMode.FULL) full(started) else incremental(state!!, started)
        }.getOrElse { e ->
            Log.e(TAG, "Synchronisation $mode échouée", e)
            if (mode == SyncMode.INCREMENTAL) runCatching { full(started) }.getOrDefault(false) else false
        }
        val duration = System.currentTimeMillis() - started
        Log.i(TAG, "Synchronisation $mode : ${if (changed) "changements" else "rien à faire"} en $duration ms")
        SyncResult(mode, changed, duration)
    }

    private suspend fun full(now: Long): Boolean {
        val page = scanner.scanAll()
        dao.replaceAll(
            page.songs,
            LibraryScanStateEntity(
                lastGeneration = page.generation,
                volumeName = scanner.volumeName(),
                lastFullScanAt = now,
                lastScanAt = now,
                scannerVersion = MediaStoreScanner.SCANNER_VERSION,
            ),
        )
        return true
    }

    private suspend fun incremental(state: LibraryScanStateEntity, now: Long): Boolean {
        val generation = scanner.currentGeneration()
        val currentIds = scanner.currentIds()
        if (generation == state.lastGeneration && currentIds.size == dao.count()) {
            return false
        }
        val page = scanner.scanSince(state.lastGeneration)
        val cachedIds = dao.ids()
        val plan = LibraryDiff.plan(cachedIds, page.songs.map { it.id }, currentIds.asList())
        val keep = plan.upserts.toHashSet()
        val upserts = page.songs.filter { it.id in keep }
        dao.applyDiff(
            upserts,
            plan.deletions,
            state.copy(lastGeneration = page.generation, lastScanAt = now),
        )
        return !plan.isEmpty
    }

    /** Permission perdue : plus rien à montrer ni à croire. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
        dao.clearScanState()
    }

    private companion object {
        const val TAG = "LibrarySync"
    }
}
