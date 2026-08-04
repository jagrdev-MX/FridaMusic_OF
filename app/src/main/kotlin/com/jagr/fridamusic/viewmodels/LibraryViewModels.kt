

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jagr.fridamusic.viewmodels

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.jagr.fridamusic.constants.AlbumFilter
import com.jagr.fridamusic.constants.AlbumFilterKey
import com.jagr.fridamusic.constants.AlbumSortDescendingKey
import com.jagr.fridamusic.constants.AlbumSortType
import com.jagr.fridamusic.constants.AlbumSortTypeKey
import com.jagr.fridamusic.constants.ArtistFilter
import com.jagr.fridamusic.constants.ArtistFilterKey
import com.jagr.fridamusic.constants.ArtistSongSortDescendingKey
import com.jagr.fridamusic.constants.ArtistSongSortType
import com.jagr.fridamusic.constants.ArtistSongSortTypeKey
import com.jagr.fridamusic.constants.ArtistSortDescendingKey
import com.jagr.fridamusic.constants.ArtistSortType
import com.jagr.fridamusic.constants.ArtistSortTypeKey
import com.jagr.fridamusic.constants.ExportedSongIdsKey
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.constants.HideYoutubeShortsKey
import com.jagr.fridamusic.constants.LibraryFilter
import com.jagr.fridamusic.constants.PlaylistSortDescendingKey
import com.jagr.fridamusic.constants.PlaylistGridViewKey
import com.jagr.fridamusic.constants.PlaylistSortType
import com.jagr.fridamusic.constants.PlaylistSortTypeKey
import com.jagr.fridamusic.constants.SongFilter
import com.jagr.fridamusic.constants.SongFilterKey
import com.jagr.fridamusic.constants.SongSortDescendingKey
import com.jagr.fridamusic.constants.SongSortType
import com.jagr.fridamusic.constants.SongSortTypeKey
import com.jagr.fridamusic.constants.TopSize
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.db.entities.PlaylistEntity
import com.jagr.fridamusic.db.entities.ArtistEntity
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.db.entities.SongArtistMap
import com.jagr.fridamusic.db.entities.SongEntity
import com.jagr.fridamusic.extensions.filterExplicit
import com.jagr.fridamusic.extensions.filterExplicitAlbums
import com.jagr.fridamusic.extensions.filterVideoSongs
import com.jagr.fridamusic.extensions.filterYoutubeShorts
import com.jagr.fridamusic.extensions.toEnum
import com.jagr.fridamusic.playback.DownloadUtil
import com.jagr.fridamusic.utils.SyncStatus
import com.jagr.fridamusic.utils.SyncUtils
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it[SongFilterKey].toEnum(SongFilter.LIKED),
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                        (it[SongSortDescendingKey] ?: true),
                    ),
                    it[ExportedSongIdsKey] ?: "",
                    Pair(it[HideExplicitKey] ?: false, it[HideVideoSongsKey] ?: false)
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, exportedSongIds, hideConfig) ->
                val (filter, sortType, descending) = filterSort
                val (hideExplicit, hideVideoSongs) = hideConfig
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.LIKED -> database.likedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.DOWNLOADED -> database.downloadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.UPLOADED -> database.uploadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.EXPORTED -> {
                        val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
                        database.getSongsByIdsFlow(ids).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteSongs =
        context.dataStore.data
            .map {
                Triple(
                    it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    it[SongSortDescendingKey] ?: true,
                    Pair(it[HideExplicitKey] ?: false, it[HideVideoSongsKey] ?: false),
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending, hideConfig) ->
                val (hideExplicit, hideVideoSongs) = hideConfig
                database.likedSongs(sortType, descending).map {
                    it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allAlbums =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                        it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                        it[AlbumSortDescendingKey] ?: true,
                    ),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.UPLOADED -> database.albumsUploaded(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val preferences =
        context.dataStore.data
            .map {
                PlaylistLibraryPreferences(
                    sortType = it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE),
                    descending = it[PlaylistSortDescendingKey] ?: true,
                    gridView = it[PlaylistGridViewKey] ?: false,
                    hideYoutubeShorts = it[HideYoutubeShortsKey] ?: false,
                )
            }.distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PlaylistLibraryPreferences(),
            )

    val allPlaylists =
        preferences
            .flatMapLatest { preference ->
                database.playlists(preference.sortType, preference.descending).map {
                    it.filterYoutubeShorts(preference.hideYoutubeShorts)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSortType(sortType: PlaylistSortType) {
        viewModelScope.launch {
            context.dataStore.edit { it[PlaylistSortTypeKey] = sortType.name }
        }
    }

    fun setSortDescending(descending: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[PlaylistSortDescendingKey] = descending }
        }
    }

    fun setGridView(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[PlaylistGridViewKey] = enabled }
        }
    }

    fun createPlaylist(name: String, onComplete: (Boolean) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                database.withTransaction {
                    insert(
                        PlaylistEntity(
                            name = cleanName,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                            isLocal = true,
                        )
                    )
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun importM3u(source: Uri, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lines = checkNotNull(context.contentResolver.openInputStream(source))
                        .bufferedReader()
                        .use { it.readLines() }
                    require(lines.firstOrNull()?.trim()?.startsWith("#EXTM3U") == true)

                    val imported = mutableListOf<ImportedM3uSong>()
                    var pendingInfo: String? = null
                    lines.drop(1).forEach { rawLine ->
                        val line = rawLine.trim()
                        when {
                            line.startsWith("#EXTINF:") -> pendingInfo = line
                            line.isEmpty() || line.startsWith('#') -> Unit
                            else -> {
                                val info = pendingInfo
                                val duration = info?.substringAfter("#EXTINF:")
                                    ?.substringBefore(',')?.toIntOrNull() ?: -1
                                val displayName = info?.substringAfter(',', missingDelimiterValue = "").orEmpty()
                                val artist = displayName.substringBefore(" - ", missingDelimiterValue = "").trim()
                                val title = displayName.substringAfter(" - ", displayName).trim()
                                    .ifBlank { line.substringAfterLast('/') }
                                val parsed = Uri.parse(line)
                                val id = when {
                                    line.startsWith("content://") || line.startsWith("file://") -> line
                                    parsed.getQueryParameter("v") != null -> parsed.getQueryParameter("v")
                                    parsed.host?.contains("youtu.be") == true -> parsed.lastPathSegment
                                    else -> line.takeIf { !it.contains("://") }
                                }
                                if (!id.isNullOrBlank()) {
                                    imported += ImportedM3uSong(id, title, artist, duration)
                                }
                                pendingInfo = null
                            }
                        }
                    }
                    require(imported.isNotEmpty())

                    val playlistName = source.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringBeforeLast('.')
                        ?.takeIf { it.isNotBlank() }
                        ?: "M3U"
                    val playlistEntity = PlaylistEntity(
                        name = playlistName,
                        bookmarkedAt = LocalDateTime.now(),
                        isEditable = true,
                        isLocal = true,
                    )
                    database.withTransaction {
                        insert(playlistEntity)
                        imported.forEach { entry ->
                            insert(
                                SongEntity(
                                    id = entry.id,
                                    title = entry.title,
                                    duration = entry.duration,
                                    isLocal = entry.id.startsWith("content://") || entry.id.startsWith("file://"),
                                )
                            )
                            if (entry.artist.isNotBlank()) {
                                val artist = artistByName(entry.artist) ?: ArtistEntity(
                                    id = ArtistEntity.generateArtistId(),
                                    name = entry.artist,
                                    isLocal = entry.id.startsWith("content://") || entry.id.startsWith("file://"),
                                ).also(::insert)
                                insert(SongArtistMap(entry.id, artist.id, 0))
                            }
                        }
                        addSongToPlaylist(
                            Playlist(playlistEntity, imported.size, emptyList()),
                            imported.map { it.id }.distinct(),
                        )
                    }
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun renamePlaylist(playlist: Playlist, name: String, onComplete: (Boolean) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                database.withTransaction {
                    update(
                        playlist.playlist.copy(
                            name = cleanName,
                            lastUpdateTime = LocalDateTime.now(),
                        )
                    )
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun setPlaylistCover(playlist: Playlist, uri: Uri, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                database.withTransaction {
                    update(
                        playlist.playlist.copy(
                            thumbnailUrl = uri.toString(),
                            lastUpdateTime = LocalDateTime.now(),
                        )
                    )
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun togglePinned(playlist: Playlist) {
        viewModelScope.launch {
            database.withTransaction {
                update(playlist.playlist.copy(isPinned = !playlist.playlist.isPinned))
            }
        }
    }

    fun deletePlaylist(playlist: Playlist, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                database.withTransaction {
                    if (playlist.playlist.browseId != null) {
                        update(playlist.playlist.toggleLike())
                    } else {
                        delete(playlist.playlist)
                    }
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun loadPlaylistSongs(playlistId: String, onLoaded: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = runCatching {
                database.playlistSongs(playlistId).first().map { it.song }
            }.onFailure(::reportException).getOrDefault(emptyList())
            onLoaded(songs)
        }
    }

    fun loadPlaylistsSongs(playlistIds: Set<String>, onLoaded: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val orderedIds = allPlaylists.value.map { it.id }.filter { it in playlistIds }
            val songs = runCatching {
                orderedIds.flatMap { playlistId ->
                    database.playlistSongs(playlistId).first().map { it.song }
                }
            }.onFailure(::reportException).getOrDefault(emptyList())
            onLoaded(songs)
        }
    }

    fun addPlaylistToPlaylist(
        source: Playlist,
        target: Playlist,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (source.id == target.id) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                val sourceIds = database.playlistSongs(source.id).first()
                    .map { it.map.songId }
                    .distinct()
                database.withTransaction {
                    val existingIds = if (sourceIds.isEmpty()) emptySet() else {
                        playlistDuplicates(target.id, sourceIds).toSet()
                    }
                    val newIds = sourceIds.filterNot { it in existingIds }
                    if (newIds.isNotEmpty()) {
                        val currentTarget = getPlaylistByIdBlocking(target.id) ?: target
                        addSongToPlaylist(currentTarget, newIds)
                        update(currentTarget.playlist.copy(lastUpdateTime = LocalDateTime.now()))
                    }
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun addPlaylistsToPlaylist(
        sources: List<Playlist>,
        target: Playlist,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (sources.isEmpty() || sources.any { it.id == target.id }) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                val sourceIds = sources.flatMap { source ->
                    database.playlistSongs(source.id).first().map { it.map.songId }
                }.distinct()
                database.withTransaction {
                    val existingIds = if (sourceIds.isEmpty()) emptySet() else {
                        playlistDuplicates(target.id, sourceIds).toSet()
                    }
                    val newIds = sourceIds.filterNot { it in existingIds }
                    if (newIds.isNotEmpty()) {
                        val currentTarget = getPlaylistByIdBlocking(target.id) ?: target
                        addSongToPlaylist(currentTarget, newIds)
                        update(currentTarget.playlist.copy(lastUpdateTime = LocalDateTime.now()))
                    }
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun exportPlaylist(playlist: Playlist, destination: Uri, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val songs = database.playlistSongs(playlist.id).first().map { it.song }
                    checkNotNull(context.contentResolver.openOutputStream(destination)).bufferedWriter().use { writer ->
                        writer.appendLine("#EXTM3U")
                        songs.forEach { song ->
                            val artist = song.artists.joinToString(", ") { it.name }
                            val displayName = listOf(artist, song.song.title)
                                .filter { it.isNotBlank() }
                                .joinToString(" - ")
                                .replace('\n', ' ')
                                .replace('\r', ' ')
                            writer.appendLine("#EXTINF:${song.song.duration.coerceAtLeast(-1)},$displayName")
                            writer.appendLine(
                                if (song.song.isLocal || song.song.id.startsWith("content://")) {
                                    song.song.id
                                } else {
                                    "https://music.youtube.com/watch?v=${song.song.id}"
                                }
                            )
                        }
                    }
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun exportPlaylists(playlists: List<Playlist>, destination: Uri, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val songs = playlists.flatMap { playlist ->
                        database.playlistSongs(playlist.id).first().map { it.song }
                    }
                    writeM3u(destination, songs)
                }
            }
            onComplete(result.isSuccess)
        }
    }

    private fun writeM3u(destination: Uri, songs: List<Song>) {
        checkNotNull(context.contentResolver.openOutputStream(destination)).bufferedWriter().use { writer ->
            writer.appendLine("#EXTM3U")
            songs.forEach { song ->
                val artist = song.artists.joinToString(", ") { it.name }
                val displayName = listOf(artist, song.song.title)
                    .filter { it.isNotBlank() }
                    .joinToString(" - ")
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                writer.appendLine("#EXTINF:${song.song.duration.coerceAtLeast(-1)},$displayName")
                writer.appendLine(
                    if (song.song.isLocal || song.song.id.startsWith("content://")) {
                        song.song.id
                    } else {
                        "https://music.youtube.com/watch?v=${song.song.id}"
                    }
                )
            }
        }
    }

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

data class PlaylistLibraryPreferences(
    val sortType: PlaylistSortType = PlaylistSortType.CREATE_DATE,
    val descending: Boolean = true,
    val gridView: Boolean = false,
    val hideYoutubeShorts: Boolean = false,
)

private data class ImportedM3uSong(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
)

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                        ?: true),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoSongsKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit, hideVideoSongs) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val syncState = syncUtils.syncState
    val isRefreshing = syncState
        .map { it.overallStatus is SyncStatus.Syncing }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun syncIfStale() {
        syncUtils.tryAutoSync()
    }

    fun refresh() {
        syncUtils.performFullSync()
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
    var artists =
        database
            .artistsBookmarked(
                ArtistSortType.CREATE_DATE,
                true,
            ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = context.dataStore.data
        .map { it[HideYoutubeShortsKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideYoutubeShorts ->
            database.playlists(PlaylistSortType.CREATE_DATE, true).map { it.filterYoutubeShorts(hideYoutubeShorts) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            albums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
