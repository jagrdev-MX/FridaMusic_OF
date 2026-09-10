

package com.jagr.fridamusic.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ArtistPage
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.constants.HideYoutubeShortsKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.ArtistEntity
import com.jagr.fridamusic.extensions.filterExplicit
import com.jagr.fridamusic.extensions.filterExplicitAlbums
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.read
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jagr.fridamusic.extensions.filterVideoSongs as filterVideoSongsLocal
import com.jagr.fridamusic.artistvideo.ArtistVideoCanvasProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    var isLoadingRemote by mutableStateOf(true)
        private set
    
    private val _artistVideoUrl = MutableStateFlow<String?>(null)
    val artistVideoUrl: StateFlow<String?> = _artistVideoUrl

    private val _artistVideoSong = MutableStateFlow<com.music.innertube.models.SongItem?>(null)
    val artistVideoSong: StateFlow<com.music.innertube.models.SongItem?> = _artistVideoSong
    private val _isBookmarkUpdating = MutableStateFlow(false)
    val isBookmarkUpdating = _isBookmarkUpdating.asStateFlow()
    
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, hideVideoSongs) ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        
        viewModelScope.launch {
            context.dataStore.data
                .map {
                    Triple(
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false,
                        it[HideYoutubeShortsKey] ?: false
                    )
                }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            isLoadingRemote = true
            try {
                val currentArtist = database.artist(artistId).first()
                if (currentArtist?.artist?.isLocal == true) return@launch

                val hideExplicit = context.dataStore.read(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
                val fallbackArtistId = currentArtist?.artist?.channelId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it != artistId }
                val artistResult = YouTube.artist(artistId).let { primaryResult ->
                    if (primaryResult.isSuccess || fallbackArtistId == null) {
                        primaryResult
                    } else {
                        YouTube.artist(fallbackArtistId)
                    }
                }

                artistResult
                    .onSuccess { page ->
                        val filteredSections = page.sections
                            .map { section ->
                                section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                            }
                            .filter { section -> section.items.isNotEmpty() }

                        artistPage = page.copy(sections = filteredSections)

                        database.withTransaction {
                            val current = getArtistById(artistId)
                            if (current == null) {
                                insert(
                                    ArtistEntity(
                                        id = artistId,
                                        name = page.artist.title,
                                        thumbnailUrl = page.artist.thumbnail,
                                        channelId = page.artist.channelId,
                                    )
                                )
                            } else {
                                update(
                                    current.copy(
                                        name = page.artist.title,
                                        thumbnailUrl = page.artist.thumbnail ?: current.thumbnailUrl,
                                        channelId = page.artist.channelId ?: current.channelId,
                                    )
                                )
                            }
                        }


                        val topSongsSection = page.sections.find { it.items.firstOrNull() is com.music.innertube.models.SongItem }
                        topSongsSection?.items?.forEach { item ->
                            if (item is com.music.innertube.models.SongItem) {
                                val canvas = ArtistVideoCanvasProvider.getBySongArtist(
                                    song = item.title,
                                    artist = page.artist?.title ?: ""
                                )
                                if (canvas?.preferredAnimationUrl != null) {
                                    _artistVideoUrl.value = canvas.preferredAnimationUrl
                                    _artistVideoSong.value = item
                                    return@forEach
                                }
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }
            } finally {
                isLoadingRemote = false
            }
        }
    }

    fun toggleFollow() {
        if (_isBookmarkUpdating.value) return
        _isBookmarkUpdating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val current = getArtistById(artistId)
                    if (current != null) {
                        update(current.localToggleLike())
                    } else {
                        val remoteArtist = artistPage?.artist ?: return@withTransaction
                        insert(
                            ArtistEntity(
                                id = artistId,
                                name = remoteArtist.title,
                                thumbnailUrl = remoteArtist.thumbnail,
                                channelId = remoteArtist.channelId,
                            ).localToggleLike()
                        )
                        getArtistById(artistId)
                            ?.takeIf { it.bookmarkedAt == null }
                            ?.let { update(it.localToggleLike()) }
                    }
                }
            } catch (error: Exception) {
                reportException(error)
            } finally {
                _isBookmarkUpdating.value = false
            }
        }
    }
}
