

package com.jagr.fridamusic.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterVideoSongs
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.read
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val database: MusicDatabase
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val relatedItems = MutableStateFlow<List<YTItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set

    private var loadedEntryCount = 0

    private val continuationMutex = Mutex()
    private val seenContinuations = mutableSetOf<String>()
    private var continuationRequestCount = 0

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            continuation = null
            seenContinuations.clear()
            continuationRequestCount = 0

            val normalizedId = runCatching {
                YouTube.normalizePlaylistBrowseId(playlistId)
            }.getOrDefault(playlistId)
            Timber.d("Loading playlist detail: id=%s endpoint=browse", normalizedId)

            YouTube.playlist(playlistId)
                .onSuccess { playlistPage ->
                    loadedEntryCount = playlistPage.songs.size
                    val knownCount = playlistPage.playlist.songCount
                        ?: loadedEntryCount.takeIf { playlistPage.songsContinuation == null }
                    knownCount?.let { database.updateRemotePlaylistSongCount(playlistId.removePrefix("VL"), it) }
                    playlist.value = playlistPage.playlist.copy(
                        songCountText = knownCount?.toString() ?: playlistPage.playlist.songCountText,
                    )
                    playlistSongs.value = applySongFilters(playlistPage.songs)
                    relatedItems.value = playlistPage.related ?: emptyList()
                    continuation = playlistPage.songsContinuation
                    _isLoading.value = false
                    Timber.d(
                        "Playlist detail parsed: id=%s source=%s songs=%d continuation=%s",
                        normalizedId,
                        playlistPage.songSource,
                        playlistPage.songs.size,
                        playlistPage.songsContinuation != null,
                    )
                }.onFailure { throwable ->
                    _error.value = throwable.safeErrorMessage()
                    _isLoading.value = false
                    Timber.w(
                        throwable,
                        "Playlist detail failed: id=%s endpoint=browse status=%s stage=initial",
                        normalizedId,
                        throwable.safeHttpStatus(),
                    )
                    reportException(throwable)
                }
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return 
        
        val tokenForManualLoad = continuation ?: return 

        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val continuationResult = requestContinuation(tokenForManualLoad)
            if (continuationResult == null) {
                _isLoadingMore.value = false
                return@launch
            }

            continuationResult
                .onSuccess { playlistContinuationPage ->
                    loadedEntryCount += playlistContinuationPage.songs.size
                    if (playlistContinuationPage.continuation == null) {
                        val count = playlist.value?.songCount ?: loadedEntryCount
                        database.updateRemotePlaylistSongCount(playlistId.removePrefix("VL"), count)
                        playlist.value = playlist.value?.copy(songCountText = count.toString())
                    }
                    val currentSongs = playlistSongs.value.toMutableList()
                    currentSongs.addAll(playlistContinuationPage.songs)
                    playlistSongs.value = applySongFilters(currentSongs)
                    continuation = playlistContinuationPage.continuation
                }.onFailure { throwable ->
                    if (playlistSongs.value.isEmpty()) {
                        _error.value = throwable.safeErrorMessage()
                    }
                    Timber.w(
                        throwable,
                        "Playlist continuation failed: id=%s endpoint=browse status=%s stage=continuation",
                        playlistId,
                        throwable.safeHttpStatus(),
                    )
                    reportException(throwable)
                }.also {
                    _isLoadingMore.value = false
                }
        }
    }

    fun retry() {
        fetchInitialPlaylistData() 
    }

    private suspend fun requestContinuation(token: String) = continuationMutex.withLock {
        if (continuationRequestCount >= MAX_CONTINUATION_REQUESTS) {
            Timber.w("Playlist continuation stopped at the safe page limit")
            continuation = null
            return@withLock null
        }
        if (!seenContinuations.add(token)) {
            Timber.w("Playlist continuation stopped after a repeated token")
            continuation = null
            return@withLock null
        }
        continuationRequestCount++
        YouTube.playlistContinuation(token)
    }

    private fun Throwable.safeErrorMessage(): String =
        message?.takeIf { it.isNotBlank() }
            ?: javaClass.simpleName.takeIf { it.isNotBlank() }
            ?: "Failed to load playlist"

    private fun Throwable.safeHttpStatus(): String =
        (this as? ResponseException)?.response?.status?.value?.toString() ?: "n/a"

    private suspend fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
        val uniqueSongs = songs.distinctBy { it.id }
        if (!hideVideoSongs) return uniqueSongs

        val filtered = uniqueSongs.filterVideoSongs(true)
        // If filtering hides everything, keep original list to avoid false "empty playlist" UX.
        return if (filtered.isEmpty() && uniqueSongs.isNotEmpty()) uniqueSongs else filtered
    }

    private companion object {
        const val MAX_CONTINUATION_REQUESTS = 50
    }
}
