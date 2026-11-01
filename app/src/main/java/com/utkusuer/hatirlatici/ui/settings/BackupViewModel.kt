package com.utkusuer.hatirlatici.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.utkusuer.hatirlatici.HatirlaticiApp
import com.utkusuer.hatirlatici.data.backup.BackupInfo
import com.utkusuer.hatirlatici.data.backup.DriveAuth
import com.utkusuer.hatirlatici.data.backup.DriveClient
import com.utkusuer.hatirlatici.data.backup.DriveFile
import com.utkusuer.hatirlatici.data.backup.WrongPasswordException
import com.utkusuer.hatirlatici.data.settings.DriveState
import com.utkusuer.hatirlatici.work.DriveBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Geri yüklenmeyi bekleyen yedek (dosyadan veya Drive'dan okunmuş). */
data class PendingImport(val json: String, val info: BackupInfo, val source: String)

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val pendingImport: PendingImport? = null,
    val driveFiles: List<DriveFile>? = null,
)

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as HatirlaticiApp).container
    private val repo = container.backupRepository
    private val settings = container.settingsRepository

    val drive: StateFlow<DriveState> = settings.driveState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveState())

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    fun suggestedFileName() = "hatirlatici-yedek-${LocalDate.now()}.json"

    fun exportToFile(uri: Uri, password: String?) = run("Dışa aktarıldı") {
        val json = repo.export(password)
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
        }
    }

    fun loadFromFile(uri: Uri) = run(null) {
        val json = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        _state.update { it.copy(pendingImport = PendingImport(json, repo.info(json), "Dosya")) }
    }

    fun confirmImport(password: String?) {
        val pending = _state.value.pendingImport ?: return
        run("Geri yükleme tamamlandı") {
            repo.import(pending.json, password?.takeIf { it.isNotEmpty() })
            _state.update { it.copy(pendingImport = null) }
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    fun driveBackup(token: String, password: String?) = run("Drive'a yedeklendi") {
        val json = repo.export(password)
        val client = DriveClient(token)
        client.upload("hatirlatici-yedek-${LocalDate.now()}.json", json)
        client.prune()
        settings.setDriveLinked(true)
        settings.setDriveLastBackup(System.currentTimeMillis())
    }

    fun driveListForRestore(token: String) = run(null) {
        val files = DriveClient(token).listBackups()
        settings.setDriveLinked(true)
        if (files.isEmpty()) error("Drive'da yedek bulunamadı")
        _state.update { it.copy(driveFiles = files) }
    }

    fun driveDownload(token: String, file: DriveFile) = run(null) {
        val json = DriveClient(token).download(file.id)
        _state.update { it.copy(driveFiles = null, pendingImport = PendingImport(json, repo.info(json), "Google Drive")) }
    }

    fun closeDriveFiles() = _state.update { it.copy(driveFiles = null) }

    fun onDriveLinked() = viewModelScope.launch { settings.setDriveLinked(true) }

    fun unlinkDrive() = viewModelScope.launch {
        settings.setDriveLinked(false)
        settings.setDriveAutoBackup(false)
        DriveBackupWorker.schedule(getApplication(), false)
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settings.setDriveAutoBackup(enabled)
        DriveBackupWorker.schedule(getApplication(), enabled)
    }

    fun showError(t: Throwable) = _state.update { it.copy(busy = false, message = DriveAuth.describe(t), isError = true) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun run(success: String?, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false, message = success, isError = false) }
            } catch (e: WrongPasswordException) {
                _state.update { it.copy(busy = false, message = "Yedek parolası hatalı.", isError = true) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = DriveAuth.describe(e), isError = true) }
            }
        }
    }
}
