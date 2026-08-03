

package com.jagr.fridamusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.di.DownloadCache
import com.jagr.fridamusic.di.PlayerCache
import com.jagr.fridamusic.extensions.filterExplicit
import com.jagr.fridamusic.extensions.filterVideoSongs
import com.jagr.fridamusic.playback.DownloadUtil
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache,
    private val downloadUtil: DownloadUtil,
) : ViewModel() {

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    refreshAvailableSongs()
                    _hasError.value = false
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _hasError.value = true
                } finally {
                    _isLoading.value = false
                }
                delay(1000)
            }
        }
    }

    private suspend fun refreshAvailableSongs() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        val verifiedDownloadIds = downloadUtil.downloads.value.values
            .asSequence()
            .filter { it.state == Download.STATE_COMPLETED }
            .mapNotNull { download ->
                val contentLength = cacheContentLength(
                    cache = downloadCache,
                    songId = download.request.id,
                    fallback = download.contentLength.takeIf { it > 0 }
                        ?: download.bytesDownloaded.takeIf { it > 0 },
                )
                download.request.id.takeIf {
                    contentLength != null && downloadCache.isCached(it, 0, contentLength)
                }
            }.toSet()

        val downloaded = songsByIds(verifiedDownloadIds)
            .filterNot { it.song.isLocal }
            .sortedByDescending { it.song.dateDownload }
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)

        val cacheCandidateIds = playerCache.keys
            .asSequence()
            .filterNot { it in verifiedDownloadIds }
            .toSortedSet()
        val cached = songsByIds(cacheCandidateIds)
            .filterNot { it.song.isLocal }
            .filter { song ->
                val contentLength = cacheContentLength(
                    cache = playerCache,
                    songId = song.song.id,
                    fallback = song.format?.contentLength,
                )
                contentLength != null && playerCache.isCached(song.song.id, 0, contentLength)
            }.sortedBy { it.song.title.lowercase() }
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)

        _downloadedSongs.value = downloaded
        _cachedSongs.value = cached
    }

    private suspend fun songsByIds(ids: Set<String>): List<Song> =
        if (ids.isEmpty()) emptyList() else database.getSongsByIds(ids.toList())

    private fun cacheContentLength(
        cache: SimpleCache,
        songId: String,
        fallback: Long?,
    ): Long? = ContentMetadata.getContentLength(cache.getContentMetadata(songId))
        .takeIf { it != C.LENGTH_UNSET.toLong() && it > 0 }
        ?: fallback?.takeIf { it > 0 }

    fun removeSongFromCache(songId: String) {
        playerCache.removeResource(songId)
    }
}
