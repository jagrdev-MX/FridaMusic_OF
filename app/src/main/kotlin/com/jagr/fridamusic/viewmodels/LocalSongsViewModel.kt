package com.jagr.fridamusic.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.localmedia.LocalAudioFolder
import com.jagr.fridamusic.localmedia.LocalAudioPreferencesRepository
import com.jagr.fridamusic.localmedia.LocalSongScanConfig
import com.jagr.fridamusic.localmedia.LocalSongScanSummary
import com.jagr.fridamusic.localmedia.LocalSongScanner
import com.jagr.fridamusic.localmedia.LocalSongSortMetadata
import com.jagr.fridamusic.utils.reportException
import javax.inject.Inject

@HiltViewModel
class LocalSongsViewModel
@Inject
constructor(
    database: MusicDatabase,
    private val localSongScanner: LocalSongScanner,
    private val preferencesRepository: LocalAudioPreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _scanState = MutableStateFlow(LocalSongsScanState())
    val scanState = _scanState.asStateFlow()

    private val _foldersState = MutableStateFlow(LocalAudioFoldersState())
    val foldersState = _foldersState.asStateFlow()

    private val _sortMetadata = MutableStateFlow<Map<String, LocalSongSortMetadata>>(emptyMap())
    val sortMetadata = _sortMetadata.asStateFlow()

    val scanConfig = preferencesRepository.scanConfig.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LocalSongScanConfig(),
    )

    val songs = database.localSongs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val scanRequests = Channel<ScanRequest>(Channel.CONFLATED)
    private var preferenceRefreshJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (request in scanRequests) {
                performScan(request)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            songs
                .map { localSongs -> localSongs.mapTo(linkedSetOf()) { it.song.id } }
                .distinctUntilChanged()
                .collect { localSongIds -> loadSortMetadata(localSongIds) }
        }
    }

    fun refreshLibrary() {
        enqueueCurrentConfig(force = false)
    }

    fun scanDevice() {
        enqueueCurrentConfig(force = true)
    }

    fun refreshSortMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            loadSortMetadata(songs.value.mapTo(linkedSetOf()) { it.song.id })
        }
    }

    fun loadAudioFolders() {
        if (!hasAudioPermission() || _foldersState.value.isLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _foldersState.value = _foldersState.value.copy(isLoading = true, errorMessage = null)
            runCatching { localSongScanner.queryAudioFolders() }
                .onSuccess { folders ->
                    _foldersState.value = LocalAudioFoldersState(folders = folders)
                }
                .onFailure { error ->
                    reportException(error)
                    _foldersState.value = _foldersState.value.copy(
                        isLoading = false,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun setHideShortAudio(enabled: Boolean) = updatePreference {
        preferencesRepository.setHideShortAudio(enabled)
    }

    fun setMinimumDurationSeconds(seconds: Int) = updatePreference {
        preferencesRepository.setMinimumDurationSeconds(seconds)
    }

    fun setHideSmallFiles(enabled: Boolean) = updatePreference {
        preferencesRepository.setHideSmallFiles(enabled)
    }

    fun setMinimumSizeKb(sizeKb: Int) = updatePreference {
        preferencesRepository.setMinimumSizeKb(sizeKb)
    }

    fun setHideRecordings(enabled: Boolean) = updatePreference {
        preferencesRepository.setHideRecordings(enabled)
    }

    fun setHideSystemSounds(enabled: Boolean) = updatePreference {
        preferencesRepository.setHideSystemSounds(enabled)
    }

    fun setShowFilesWithoutMetadata(enabled: Boolean) = updatePreference {
        preferencesRepository.setShowFilesWithoutMetadata(enabled)
    }

    fun setExcludedFolders(folders: Set<String>) = updatePreference {
        preferencesRepository.setExcludedFolders(folders)
    }

    private fun updatePreference(update: suspend () -> Unit) {
        viewModelScope.launch {
            update()
            preferenceRefreshJob?.cancel()
            preferenceRefreshJob = viewModelScope.launch {
                delay(PREFERENCE_REFRESH_DEBOUNCE_MS)
                enqueueCurrentConfig(force = false)
            }
        }
    }

    private fun enqueueCurrentConfig(force: Boolean) {
        if (!hasAudioPermission()) return
        viewModelScope.launch {
            scanRequests.send(
                ScanRequest(
                    config = preferencesRepository.currentScanConfig(),
                    force = force,
                ),
            )
        }
    }

    private suspend fun performScan(request: ScanRequest) {
        _scanState.value = _scanState.value.copy(isScanning = true, errorMessage = null)
        runCatching {
            localSongScanner.scanDevice(
                scanConfig = request.config,
                force = request.force,
            )
        }
            .onSuccess { summary ->
                _scanState.value = LocalSongsScanState(
                    isScanning = false,
                    lastSummary = summary,
                    errorMessage = null,
                )
            }
            .onFailure { error ->
                reportException(error)
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    errorMessage = error.message,
                )
            }
    }

    private suspend fun loadSortMetadata(localSongIds: Set<String>) {
        if (!hasAudioPermission() || localSongIds.isEmpty()) {
            _sortMetadata.value = emptyMap()
            return
        }
        _sortMetadata.value = localSongScanner.querySortMetadata()
            .filterKeys { it in localSongIds }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        scanRequests.close()
        super.onCleared()
    }

    private data class ScanRequest(
        val config: LocalSongScanConfig,
        val force: Boolean,
    )

    private companion object {
        const val PREFERENCE_REFRESH_DEBOUNCE_MS = 350L
    }
}

data class LocalSongsScanState(
    val isScanning: Boolean = false,
    val lastSummary: LocalSongScanSummary? = null,
    val errorMessage: String? = null,
)

data class LocalAudioFoldersState(
    val isLoading: Boolean = false,
    val folders: List<LocalAudioFolder> = emptyList(),
    val errorMessage: String? = null,
)
