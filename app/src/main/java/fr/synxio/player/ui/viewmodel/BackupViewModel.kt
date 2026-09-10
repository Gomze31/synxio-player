package fr.synxio.player.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.repo.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel pour gérer la sauvegarde et la restauration des données.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    /**
     * État de l'opération en cours.
     */
    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    /**
     * Informations sur le fichier de sauvegarde sélectionné.
     */
    private val _backupInfo = MutableStateFlow<BackupRepository.BackupInfo?>(null)
    val backupInfo: StateFlow<BackupRepository.BackupInfo?> = _backupInfo.asStateFlow()

    /**
     * Exporte les données vers un fichier.
     */
    fun exportData(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Exporting
        val result = backupRepository.exportToFile(uri)
        _backupState.value = if (result.isSuccess) {
            BackupState.ExportSuccess("Données exportées avec succès")
        } else {
            BackupState.ExportFailed(result.exceptionOrNull()?.message ?: "Erreur inconnue")
        }
    }

    /**
     * Importe les données depuis un fichier.
     */
    fun importData(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Importing
        val result = backupRepository.importFromFile(uri)
        _backupState.value = if (result.isSuccess) {
            BackupState.ImportSuccess("Données importées avec succès")
        } else {
            BackupState.ImportFailed(result.exceptionOrNull()?.message ?: "Erreur inconnue")
        }
    }

    /**
     * Vérifie si un fichier est un fichier de sauvegarde valide.
     */
    fun checkBackupFile(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Checking
        val result = backupRepository.isValidBackupFile(uri)
        _backupState.value = if (result) {
            // Récupérer les infos du fichier
            val infoResult = backupRepository.getBackupInfo(uri)
            if (infoResult.isSuccess) {
                _backupInfo.value = infoResult.getOrNull()
                BackupState.ValidFile
            } else {
                BackupState.InvalidFile("Fichier invalide")
            }
        } else {
            BackupState.InvalidFile("Ce fichier n'est pas une sauvegarde Synxio valide")
        }
    }

    /**
     * Efface toutes les données locales.
     */
    fun clearAllData() = viewModelScope.launch {
        _backupState.value = BackupState.Clearing
        val result = backupRepository.clearAllData()
        _backupState.value = if (result.isSuccess) {
            BackupState.ClearSuccess("Toutes les données ont été supprimées")
        } else {
            BackupState.ClearFailed(result.exceptionOrNull()?.message ?: "Erreur inconnue")
        }
    }

    /**
     * Réinitialise l'état.
     */
    fun resetState() {
        _backupState.value = BackupState.Idle
        _backupInfo.value = null
    }

    /**
     * Exporte une playlist vers un fichier M3U8.
     */
    fun exportPlaylistToM3u8(playlist: fr.synxio.player.data.model.Playlist, uri: Uri) =
        viewModelScope.launch {
            _backupState.value = BackupState.Exporting
            val result = backupRepository.exportPlaylistToM3u8(playlist, uri)
            _backupState.value = if (result.isSuccess) {
                BackupState.ExportSuccess("Playlist exportée avec succès")
            } else {
                BackupState.ExportFailed(result.exceptionOrNull()?.message ?: "Erreur inconnue")
            }
        }

    /**
     * Importe un fichier M3U8 et crée une playlist.
     */
    fun importM3u8File(uri: Uri, playlistName: String) = viewModelScope.launch {
        _backupState.value = BackupState.Importing
        val result = backupRepository.importM3u8File(uri, playlistName)
        _backupState.value = if (result.isSuccess) {
            val songCount = result.getOrNull() ?: 0
            BackupState.ImportSuccess("$songCount morceaux importés")
        } else {
            BackupState.ImportFailed(result.exceptionOrNull()?.message ?: "Erreur inconnue")
        }
    }

    /**
     * Récupère le nom de fichier de sauvegarde par défaut.
     */
    fun getDefaultBackupFilename(): String = backupRepository.getDefaultBackupFilename()

    /**
     * État des opérations de sauvegarde.
     */
    sealed class BackupState {
        object Idle : BackupState()
        object Checking : BackupState()
        object Exporting : BackupState()
        object Importing : BackupState()
        object Clearing : BackupState()
        object ValidFile : BackupState()
        data class InvalidFile(val message: String) : BackupState()
        data class ExportSuccess(val message: String) : BackupState()
        data class ExportFailed(val message: String) : BackupState()
        data class ImportSuccess(val message: String) : BackupState()
        data class ImportFailed(val message: String) : BackupState()
        data class ClearSuccess(val message: String) : BackupState()
        data class ClearFailed(val message: String) : BackupState()
    }
}
