package fr.synxio.player.data.repo

import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.AudiobookFolderDao
import fr.synxio.player.data.db.ExcludedFolderDao
import fr.synxio.player.data.model.LibraryFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filtres utilisateur du moment (durée minimale, dossiers exclus, dossiers livres audio).
 *
 * Partagé par la publication de la bibliothèque et par les Workers d'analyse : un titre
 * exclu de la bibliothèque n'est jamais analysé.
 */
@Singleton
class LibraryFiltersSource @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val excludedFolderDao: ExcludedFolderDao,
    private val audiobookFolderDao: AudiobookFolderDao,
) {
    suspend fun current(): LibraryFilters = withContext(Dispatchers.IO) {
        LibraryFilters(
            minDurationMs = settingsRepository.settings.first().minDurationSec * 1000L,
            excludedFolders = excludedFolderDao.paths().toSet(),
            audiobookFolders = audiobookFolderDao.paths().toSet(),
        )
    }
}
