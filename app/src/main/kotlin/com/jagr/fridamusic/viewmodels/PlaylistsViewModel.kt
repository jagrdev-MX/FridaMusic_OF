@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jagr.fridamusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.jagr.fridamusic.constants.AddToPlaylistSortDescendingKey
import com.jagr.fridamusic.constants.AddToPlaylistSortTypeKey
import com.jagr.fridamusic.constants.PlaylistSortType
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.db.entities.PlaylistEntity
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.toEnum
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.models.toMediaMetadata
import com.jagr.fridamusic.utils.SyncUtils
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.reportException
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private var saveToggleJob: Job? = null

    val allPlaylists =
        context.dataStore.data
            .map {
                it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[AddToPlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addSongToPlaylist(playlist: Playlist, songItem: SongItem) {
        viewModelScope.launch {
            database.query {
                insert(songItem.toMediaMetadata())
                addSongToPlaylist(playlist, listOf(songItem.id))
            }
        }
    }

    fun addSongToPlaylist(playlist: Playlist, song: Song) {
        viewModelScope.launch {
            database.query {
                addSongToPlaylist(playlist, listOf(song.song.id))
            }
        }
    }

    fun addSongToPlaylist(playlist: Playlist, mediaItem: MediaItem) {
        val mediaMetadata = mediaItem.metadata ?: return
        viewModelScope.launch {
            database.query {
                insert(mediaMetadata)
                addSongToPlaylist(playlist, listOf(mediaMetadata.id))
            }
        }
    }

    fun toggleSavedPlaylist(
        playlist: Playlist,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (saveToggleJob?.isActive == true) return
        saveToggleJob = viewModelScope.launch {
            val result = runCatching {
                database.query {
                    val current = getPlaylistByIdBlocking(playlist.id)?.playlist ?: playlist.playlist
                    val updated = if (current.browseId != null && !current.isLocal) {
                        current.toggleLike()
                    } else {
                        current.localToggleLike()
                    }
                    update(updated)
                }
            }.onFailure(::reportException)
            onComplete(result.isSuccess)
        }
    }

    fun toggleSavedOnlinePlaylist(
        playlist: PlaylistItem,
        cachedPlaylist: Playlist?,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (saveToggleJob?.isActive == true) return
        val browseId = playlist.id.trim()
        if (browseId.isEmpty()) {
            onComplete(false)
            return
        }

        saveToggleJob = viewModelScope.launch {
            val result = runCatching {
                database.query {
                    val current = cachedPlaylist
                        ?.let { getPlaylistByIdBlocking(it.id)?.playlist }
                        ?: cachedPlaylist?.playlist
                    val base = current ?: PlaylistEntity(
                        name = playlist.title,
                        browseId = browseId,
                        isEditable = playlist.isEditable,
                        remoteSongCount = playlist.songCountText
                            ?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                        playEndpointParams = playlist.playEndpoint?.params,
                        thumbnailUrl = playlist.thumbnail,
                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                        radioEndpointParams = playlist.radioEndpoint?.params,
                    )
                    val updated = base.toggleLike()
                    if (current == null) {
                        insert(updated)
                    } else {
                        update(updated)
                    }
                }
            }.onFailure(::reportException)
            onComplete(result.isSuccess)
        }
    }

    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }
}
