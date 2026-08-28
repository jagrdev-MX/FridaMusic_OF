

package com.jagr.fridamusic.viewmodels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.LyricsEntity
import com.jagr.fridamusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.lyrics.LyricsHelper
import com.jagr.fridamusic.lyrics.LyricsResult
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.utils.NetworkConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel
@Inject
constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _currentSong = mutableStateOf<Song?>(null)
    val currentSong: State<Song?> = _currentSong

    init {
        viewModelScope.launch {
            networkConnectivity.networkStatus.collect { isConnected ->
                _isNetworkAvailable.value = isConnected
            }
        }

        _isNetworkAvailable.value = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true 
        }
    }

    fun setCurrentSong(song: Song) {
        _currentSong.value = song
    }

    fun search(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                lyricsHelper.getAllLyrics(mediaId, title, artist, duration, album) { result ->
                    results.update {
                        (it + result).sortedByDescending { candidate -> candidate.score }
                    }
                }
                isLoading.value = false
            }
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
    }

    fun refetchLyrics(
        mediaMetadata: MediaMetadata,
        lyricsEntity: LyricsEntity?,
    ) {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            try {
                lyricsHelper.invalidate(mediaMetadata.id)
                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
                if (lyricsWithProvider.lyrics == LYRICS_NOT_FOUND && lyricsEntity != null) return@launch
                val song = database.song(mediaMetadata.id).first()?.song
                database.query {
                    upsert(LyricsEntity(mediaMetadata.id, lyricsWithProvider.lyrics, lyricsWithProvider.provider))
                    if (lyricsWithProvider.autoOffsetMs != 0 && song?.lyricsOffset == 0) {
                        update(song.copy(lyricsOffset = lyricsWithProvider.autoOffsetMs))
                    }
                }
            } finally {
                isLoading.value = false
            }
        }
    }

    fun selectLyrics(mediaId: String, result: LyricsResult) {
        viewModelScope.launch(Dispatchers.IO) {
            database.query {
                upsert(LyricsEntity(mediaId, result.lyrics, result.providerName))
            }
        }
    }
}
