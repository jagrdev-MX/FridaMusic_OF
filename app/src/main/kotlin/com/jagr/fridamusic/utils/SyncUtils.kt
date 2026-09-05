

package com.jagr.fridamusic.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import com.jagr.fridamusic.constants.InnerTubeCookieKey
import com.jagr.fridamusic.constants.LastFMUseSendLikes
import com.jagr.fridamusic.constants.LastFullSyncKey
import com.jagr.fridamusic.constants.SYNC_COOLDOWN
import com.jagr.fridamusic.constants.DataSyncIdKey
import com.jagr.fridamusic.constants.VisitorDataKey
import com.jagr.fridamusic.constants.YtmSyncKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.ArtistEntity
import com.jagr.fridamusic.db.entities.PlaylistEntity
import com.jagr.fridamusic.db.entities.PlaylistSongMap
import com.jagr.fridamusic.db.entities.SongEntity
import com.jagr.fridamusic.extensions.collectLatest
import com.jagr.fridamusic.extensions.isInternetConnected
import com.jagr.fridamusic.models.toMediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncOperation {
    data object FullSync : SyncOperation()
    data object LikedSongs : SyncOperation()
    data object LibrarySongs : SyncOperation()
    data object UploadedSongs : SyncOperation()
    data object LikedAlbums : SyncOperation()
    data object UploadedAlbums : SyncOperation()
    data object ArtistsSubscriptions : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    data object AutoSyncPlaylists : SyncOperation()
    data class SinglePlaylist(val browseId: String, val playlistId: String) : SyncOperation()
    data class LikeSong(val song: SongEntity) : SyncOperation()
    data object CleanupDuplicates : SyncOperation()
    data object ClearAllSynced : SyncOperation()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(
        val message: String,
        val kind: SyncErrorKind = SyncErrorKind.TEMPORARY,
    ) : SyncStatus()
    data object Completed : SyncStatus()
}

enum class SyncErrorKind {
    OFFLINE,
    TEMPORARY,
    SESSION_EXPIRED,
}

data class SyncState(
    val overallStatus: SyncStatus = SyncStatus.Idle,
    val likedSongs: SyncStatus = SyncStatus.Idle,
    val librarySongs: SyncStatus = SyncStatus.Idle,
    val uploadedSongs: SyncStatus = SyncStatus.Idle,
    val likedAlbums: SyncStatus = SyncStatus.Idle,
    val uploadedAlbums: SyncStatus = SyncStatus.Idle,
    val artists: SyncStatus = SyncStatus.Idle,
    val playlists: SyncStatus = SyncStatus.Idle,
    val currentOperation: String = ""
)

@Singleton
class SyncUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.e(throwable, "Sync coroutine exception")
        }
    }

    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob + exceptionHandler)

    private val syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
    private var processingJob: Job? = null
    private val executionMutex = Mutex()
    private val pendingOperations = ConcurrentHashMap.newKeySet<SyncOperation>()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var lastfmSendLikes = false

    companion object {
        private const val MAX_RETRIES = 2
        private const val INITIAL_RETRY_DELAY_MS = 750L
        private const val DB_OPERATION_DELAY_MS = 50L
    }

    init {
        context.dataStore.data
            .map { it[LastFMUseSendLikes] ?: false }
            .distinctUntilChanged()
            .collectLatest(syncScope) {
                lastfmSendLikes = it
            }

        startProcessingQueue()
    }

    private fun startProcessingQueue() {
        processingJob = syncScope.launch {
            for (operation in syncChannel) {
                try {
                    executionMutex.withLock {
                        processOperation(operation)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing sync operation: $operation")
                    updateState {
                        copy(
                            overallStatus = e.toSyncError(),
                            currentOperation = "",
                        )
                    }
                } finally {
                    pendingOperations.remove(operation)
                }
            }
        }
    }

    private suspend fun enqueue(operation: SyncOperation) {
        if (operation.isCoveredByFullSync() && SyncOperation.FullSync in pendingOperations) {
            Timber.d("Skipping component sync because a full sync is already pending")
            return
        }
        if (pendingOperations.add(operation)) {
            syncChannel.send(operation)
        } else {
            Timber.d("Skipping duplicate sync request: $operation")
        }
    }

    private fun SyncOperation.isCoveredByFullSync(): Boolean =
        when (this) {
            SyncOperation.LikedSongs,
            SyncOperation.LibrarySongs,
            SyncOperation.UploadedSongs,
            SyncOperation.LikedAlbums,
            SyncOperation.UploadedAlbums,
            SyncOperation.ArtistsSubscriptions,
            SyncOperation.SavedPlaylists,
            SyncOperation.AutoSyncPlaylists,
            is SyncOperation.SinglePlaylist -> true
            else -> false
        }

    private suspend fun processOperation(operation: SyncOperation) {
        when (operation) {
            is SyncOperation.FullSync -> executeFullSync()
            is SyncOperation.LikedSongs -> executeSyncLikedSongs()
            is SyncOperation.LibrarySongs -> executeSyncLibrarySongs()
            is SyncOperation.UploadedSongs -> executeSyncUploadedSongs()
            is SyncOperation.LikedAlbums -> executeSyncLikedAlbums()
            is SyncOperation.UploadedAlbums -> executeSyncUploadedAlbums()
            is SyncOperation.ArtistsSubscriptions -> executeSyncArtistsSubscriptions()
            is SyncOperation.SavedPlaylists -> executeSyncSavedPlaylists()
            is SyncOperation.AutoSyncPlaylists -> executeSyncAutoSyncPlaylists()
            is SyncOperation.SinglePlaylist -> executeSyncPlaylist(operation.browseId, operation.playlistId)
            is SyncOperation.LikeSong -> executeLikeSong(operation.song)
            is SyncOperation.CleanupDuplicates -> executeCleanupDuplicatePlaylists()
            is SyncOperation.ClearAllSynced -> executeClearAllSyncedContent()
        }
    }

    private suspend fun restoreStoredSession(): Boolean {
        return try {
            val settings = context.dataStore.data.first()
            val cookie = settings[InnerTubeCookieKey]
                ?.takeIf { it.isNotBlank() && "SAPISID" in parseCookieString(it) }
                ?: return false

            if (YouTube.cookie != cookie) {
                YouTube.cookie = cookie
            }
            YouTube.visitorData = settings[VisitorDataKey]?.takeIf { it.isNotBlank() && it != "null" }
            YouTube.dataSyncId = settings[DataSyncIdKey]
                ?.takeIf { it.isNotBlank() }
                ?.normalizeDataSyncId()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not restore the stored YouTube session")
            false
        }
    }

    private fun String.normalizeDataSyncId(): String =
        takeIf { !contains("||") }
            ?: takeIf { endsWith("||") }?.substringBefore("||")
            ?: substringAfter("||")

    private fun Throwable.isRecoverable(): Boolean =
        when (this) {
            is IOException -> true
            is ResponseException -> response.status.value == 408 ||
                response.status.value == 429 ||
                response.status.value in 500..599
            else -> false
        }

    private fun Throwable.toSyncError(): SyncStatus.Error {
        val kind = when {
            !context.isInternetConnected() -> SyncErrorKind.OFFLINE
            this is ResponseException && response.status.value in setOf(401, 403) ->
                SyncErrorKind.SESSION_EXPIRED
            message?.contains("UNAUTHENTICATED", ignoreCase = true) == true ||
                message?.contains("LOGIN_REQUIRED", ignoreCase = true) == true ->
                SyncErrorKind.SESSION_EXPIRED
            else -> SyncErrorKind.TEMPORARY
        }
        val safeMessage = when (kind) {
            SyncErrorKind.OFFLINE -> "Offline"
            SyncErrorKind.SESSION_EXPIRED -> "YouTube Music session expired"
            SyncErrorKind.TEMPORARY -> "Temporary YouTube Music sync error"
        }
        return SyncStatus.Error(safeMessage, kind)
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = MAX_RETRIES,
        initialDelay: Long = INITIAL_RETRY_DELAY_MS,
        block: suspend () -> Result<T>,
    ): Result<Result<T>> {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                val result = block()
                if (result.isSuccess) {
                    return Result.success(result)
                }

                val error = result.exceptionOrNull()
                    ?: IllegalStateException("YouTube request failed without an exception")
                if (attempt == maxRetries - 1 || !error.isRecoverable()) {
                    return Result.failure(error)
                }
                Timber.w(error, "Recoverable sync request failed (${attempt + 1}/$maxRetries)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == maxRetries - 1 || !e.isRecoverable()) {
                    return Result.failure(e)
                }
                Timber.w(e, "Recoverable sync request failed (${attempt + 1}/$maxRetries)")
            }
            delay(currentDelay)
            currentDelay *= 2
        }
        return Result.failure(IllegalStateException("Maximum sync retries exceeded"))
    }

    private fun updateState(update: SyncState.() -> SyncState) {
        _syncState.value = _syncState.value.update()
    }

    

    fun performFullSync() {
        syncScope.launch {
            enqueue(SyncOperation.FullSync)
        }
    }

    suspend fun performFullSyncSuspend() {
        executionMutex.withLock {
            executeFullSync()
        }
    }

    fun tryAutoSync() {
        syncScope.launch {
            if (!restoreStoredSession()) {
                Timber.d("Skipping auto sync - user not logged in")
                return@launch
            }

            val syncEnabled = context.dataStore.get(YtmSyncKey, true)
            if (!syncEnabled) {
                return@launch
            }
            if (!context.isInternetConnected()) {
                updateState {
                    copy(
                        overallStatus = SyncStatus.Error("Offline", SyncErrorKind.OFFLINE),
                        currentOperation = "",
                    )
                }
                return@launch
            }

            val lastSync = context.dataStore.get(LastFullSyncKey, 0L)
            val currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            if (lastSync > 0 && (currentTime - lastSync) < SYNC_COOLDOWN) {
                return@launch
            }

            enqueue(SyncOperation.FullSync)
        }
    }

    fun runAllSyncs() {
        performFullSync()
    }

    fun likeSong(s: SongEntity) {
        syncScope.launch {
            enqueue(SyncOperation.LikeSong(s))
        }
    }

    fun syncLikedSongs() {
        syncScope.launch {
            enqueue(SyncOperation.LikedSongs)
        }
    }

    fun syncLibrarySongs() {
        syncScope.launch {
            enqueue(SyncOperation.LibrarySongs)
        }
    }

    fun syncUploadedSongs() {
        syncScope.launch {
            enqueue(SyncOperation.UploadedSongs)
        }
    }

    fun syncLikedAlbums() {
        syncScope.launch {
            enqueue(SyncOperation.LikedAlbums)
        }
    }

    fun syncUploadedAlbums() {
        syncScope.launch {
            enqueue(SyncOperation.UploadedAlbums)
        }
    }

    fun syncArtistsSubscriptions() {
        syncScope.launch {
            enqueue(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun syncSavedPlaylists() {
        syncScope.launch {
            enqueue(SyncOperation.SavedPlaylists)
        }
    }

    fun syncAutoSyncPlaylists() {
        syncScope.launch {
            enqueue(SyncOperation.AutoSyncPlaylists)
        }
    }

    fun syncPlaylist(browseId: String, playlistId: String) {
        syncScope.launch {
            enqueue(SyncOperation.SinglePlaylist(browseId, playlistId))
        }
    }

    fun syncAllAlbums() {
        syncScope.launch {
            enqueue(SyncOperation.LikedAlbums)
            enqueue(SyncOperation.UploadedAlbums)
        }
    }

    fun syncAllArtists() {
        syncScope.launch {
            enqueue(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun cleanupDuplicatePlaylists() {
        syncScope.launch {
            enqueue(SyncOperation.CleanupDuplicates)
        }
    }

    fun clearAllSyncedContent() {
        syncScope.launch {
            enqueue(SyncOperation.ClearAllSynced)
        }
    }

    

    suspend fun syncLikedSongsSuspend() = executionMutex.withLock { executeSyncLikedSongs() }
    suspend fun syncLibrarySongsSuspend() = executionMutex.withLock { executeSyncLibrarySongs() }
    suspend fun syncUploadedSongsSuspend() = executionMutex.withLock { executeSyncUploadedSongs() }
    suspend fun syncLikedAlbumsSuspend() = executionMutex.withLock { executeSyncLikedAlbums() }
    suspend fun syncUploadedAlbumsSuspend() = executionMutex.withLock { executeSyncUploadedAlbums() }
    suspend fun syncArtistsSubscriptionsSuspend() = executionMutex.withLock { executeSyncArtistsSubscriptions() }
    suspend fun syncSavedPlaylistsSuspend() = executionMutex.withLock { executeSyncSavedPlaylists() }
    suspend fun syncAutoSyncPlaylistsSuspend() = executionMutex.withLock { executeSyncAutoSyncPlaylists() }
    suspend fun ensurePlaylistSongsLoaded(playlist: PlaylistEntity): Boolean =
        executionMutex.withLock {
            if (!playlist.isRemote) return@withLock true
            val expectedSongCount = playlist.remoteSongCount
            val localSongCount = database.playlistSongCount(playlist.id)
            if (expectedSongCount != null && localSongCount >= expectedSongCount) {
                return@withLock true
            }
            executeSyncPlaylist(checkNotNull(playlist.browseId), playlist.id)
        }
    suspend fun cleanupDuplicatePlaylistsSuspend() = executionMutex.withLock { executeCleanupDuplicatePlaylists() }
    suspend fun clearAllSyncedContentSuspend() = executionMutex.withLock { executeClearAllSyncedContent() }

    suspend fun syncAllAlbumsSuspend() {
        executionMutex.withLock {
            executeSyncLikedAlbums()
            executeSyncUploadedAlbums()
        }
    }

    suspend fun syncAllArtistsSuspend() {
        executionMutex.withLock {
            executeSyncArtistsSubscriptions()
        }
    }

    

    private suspend fun executeFullSync() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping full sync - user not logged in")
            updateState {
                copy(
                    overallStatus = SyncStatus.Error(
                        message = "YouTube Music session unavailable",
                        kind = SyncErrorKind.SESSION_EXPIRED,
                    ),
                    currentOperation = "",
                )
            }
            return@withContext
        }
        if (!context.isInternetConnected()) {
            updateState {
                copy(
                    overallStatus = SyncStatus.Error("Offline", SyncErrorKind.OFFLINE),
                    currentOperation = "",
                )
            }
            return@withContext
        }

        updateState {
            copy(
                overallStatus = SyncStatus.Syncing,
                likedSongs = SyncStatus.Idle,
                librarySongs = SyncStatus.Idle,
                uploadedSongs = SyncStatus.Idle,
                likedAlbums = SyncStatus.Idle,
                uploadedAlbums = SyncStatus.Idle,
                artists = SyncStatus.Idle,
                playlists = SyncStatus.Idle,
                currentOperation = "Starting full sync",
            )
        }

        try {
            
            executeSyncLikedSongs()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncLibrarySongs()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncUploadedSongs()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncLikedAlbums()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncUploadedAlbums()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncArtistsSubscriptions()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncSavedPlaylists()

            val componentErrors = listOf(
                _syncState.value.likedSongs,
                _syncState.value.librarySongs,
                _syncState.value.uploadedSongs,
                _syncState.value.likedAlbums,
                _syncState.value.uploadedAlbums,
                _syncState.value.artists,
                _syncState.value.playlists,
            ).filterIsInstance<SyncStatus.Error>()

            if (componentErrors.isEmpty()) {
                context.dataStore.edit { settings ->
                    settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                }

                updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
                Timber.d("Full sync completed successfully")
            } else {
                val mostImportantError = componentErrors.firstOrNull {
                    it.kind == SyncErrorKind.SESSION_EXPIRED
                } ?: componentErrors.firstOrNull {
                    it.kind == SyncErrorKind.OFFLINE
                } ?: componentErrors.first()
                updateState {
                    copy(
                        overallStatus = mostImportantError,
                        currentOperation = "",
                    )
                }
                Timber.w("Full sync completed with ${componentErrors.size} failed components")
            }
        } catch (e: CancellationException) {
            updateState { copy(overallStatus = SyncStatus.Idle, currentOperation = "") }
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error during full sync")
            updateState { copy(overallStatus = e.toSyncError(), currentOperation = "") }
        }
    }

    private suspend fun executeLikeSong(s: SongEntity) = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping likeSong - user not logged in")
            return@withContext
        }

        withRetry {
            YouTube.likeVideo(s.id, s.liked)
        }.onSuccess {
            enqueue(SyncOperation.LikedSongs)
        }.onFailure { e ->
            Timber.e(e, "Failed to update liked-song state on YouTube")
        }


    }

    private suspend fun executeSyncLikedSongs() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping syncLikedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(likedSongs = SyncStatus.Syncing, currentOperation = "Syncing liked songs") }

        withRetry {
            YouTube.playlist("LM").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.songs
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.likedSongsByNameAsc().first()

                    
                    val lastSync = context.dataStore.get(LastFullSyncKey, 0L)

                    localSongs.filterNot { it.id in remoteIds || it.song.isLocal }.forEach { song ->
                        try {
                            val likedDate = song.song.likedDate
                            if (likedDate == null || likedDate.toEpochSecond(ZoneOffset.UTC) > lastSync) {
                                // Schedule a migration/backfill job for songs with null likedDate
                                withRetry {
                                    YouTube.likeVideo(song.id, true)
                                }.onFailure { e ->
                                    Timber.e(e, "Failed to restore a liked song on YouTube")
                                }
                            } else {
                                database.update(song.song.localToggleLike())
                            }
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update a liked song")
                        }
                    }

                    
                    val now = LocalDateTime.now()
                    remoteSongs.forEachIndexed { index, song ->
                        try {
                            val dbSong = database.song(song.id).firstOrNull()
                            val timestamp = now.minusSeconds(index.toLong())
                            val isVideoSong = song.isVideoSong

                            database.transaction {
                                if (dbSong == null) {
                                    insert(song.toMediaMetadata()) {
                                        it.copy(liked = true, likedDate = timestamp, isVideo = isVideoSong)
                                    }
                                } else if (!dbSong.song.liked || dbSong.song.likedDate != timestamp || dbSong.song.isVideo != isVideoSong) {
                                    update(dbSong.song.copy(liked = true, likedDate = timestamp, isVideo = isVideoSong))
                                }
                            }
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to process a liked song")
                        }
                    }

                    updateState { copy(likedSongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} liked songs")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing liked songs")
                    updateState { copy(likedSongs = e.toSyncError()) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked songs from YouTube")
                updateState { copy(likedSongs = e.toSyncError()) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked songs after retries")
            updateState { copy(likedSongs = e.toSyncError()) }
        }
    }

    private suspend fun executeSyncLibrarySongs() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping syncLibrarySongs - user not logged in")
            return@withContext
        }

        updateState { copy(librarySongs = SyncStatus.Syncing, currentOperation = "Syncing library songs") }

        withRetry {
            YouTube.library("FEmusic_liked_videos").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.songsByNameAsc().first()

                    localSongs.filterNot { it.id in remoteIds }.forEach { song ->
                        try {
                            database.update(song.song.toggleLibrary(syncToYouTube = false))
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update a library song")
                        }
                    }

                    remoteSongs.forEach { song ->
                        try {
                            val dbSong = database.song(song.id).firstOrNull()
                            database.transaction {
                                if (dbSong == null) {
                                    insert(song.toMediaMetadata()) {
                                        it.toggleLibrary(syncToYouTube = false)
                                    }
                                } else if (dbSong.song.inLibrary == null) {
                                    update(dbSong.song.toggleLibrary(syncToYouTube = false))
                                }
                            }
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to process a library song")
                        }
                    }

                    updateState { copy(librarySongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} library songs")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing library songs")
                    updateState { copy(librarySongs = e.toSyncError()) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch library songs from YouTube")
                updateState { copy(librarySongs = e.toSyncError()) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync library songs after retries")
            updateState { copy(librarySongs = e.toSyncError()) }
        }
    }

    private suspend fun executeSyncUploadedSongs() = withContext(Dispatchers.IO) {
        Timber.d("[UPLOAD_DEBUG] executeSyncUploadedSongs() started")
        if (!restoreStoredSession()) {
            Timber.w("[UPLOAD_DEBUG] Skipping syncUploadedSongs - user not logged in")
            return@withContext
        }
        Timber.d("[UPLOAD_DEBUG] User is logged in, proceeding with sync")

        updateState { copy(uploadedSongs = SyncStatus.Syncing, currentOperation = "Syncing uploaded songs") }

        withRetry {
            Timber.d("[UPLOAD_DEBUG] Calling YouTube.library(FEmusic_library_privately_owned_tracks, tabIndex=1)")
            
            YouTube.library("FEmusic_library_privately_owned_tracks", tabIndex = 1).completed()
        }.onSuccess { result ->
            Timber.d("[UPLOAD_DEBUG] withRetry succeeded, result isSuccess=${result.isSuccess}")
            result.onSuccess { page ->
                try {
                    Timber.d("[UPLOAD_DEBUG] Page received, total items: ${page.items.size}")
                    val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                    Timber.d("[UPLOAD_DEBUG] Filtered to ${remoteSongs.size} SongItems")
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.uploadedSongsByNameAsc().first()
                    Timber.d("[UPLOAD_DEBUG] Local uploaded songs count: ${localSongs.size}")

                    val songsToRemove = localSongs.filterNot { it.id in remoteIds }
                    Timber.d("[UPLOAD_DEBUG] Songs to remove from uploaded: ${songsToRemove.size}")
                    songsToRemove.forEach { song ->
                        try {
                            database.update(song.song.toggleUploaded())
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "[UPLOAD_DEBUG] Failed to update an uploaded song")
                        }
                    }

                    remoteSongs.forEach { song ->
                        try {
                            val dbSong = database.song(song.id).firstOrNull()
                            database.transaction {
                                if (dbSong == null) {
                                    insert(song.toMediaMetadata()) { it.toggleUploaded() }
                                } else if (!dbSong.song.isUploaded) {
                                    update(dbSong.song.toggleUploaded())
                                }
                            }
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "[UPLOAD_DEBUG] Failed to process an uploaded song")
                        }
                    }

                    updateState { copy(uploadedSongs = SyncStatus.Completed) }
                    Timber.d("[UPLOAD_DEBUG] Synced ${remoteSongs.size} uploaded songs successfully")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "[UPLOAD_DEBUG] Error processing uploaded songs")
                    updateState { copy(uploadedSongs = e.toSyncError()) }
                }
            }.onFailure { e ->
                Timber.e(e, "[UPLOAD_DEBUG] Failed to fetch uploaded songs from YouTube")
                updateState { copy(uploadedSongs = e.toSyncError()) }
            }
        }.onFailure { e ->
            Timber.e(e, "[UPLOAD_DEBUG] Failed to sync uploaded songs after retries")
            updateState { copy(uploadedSongs = e.toSyncError()) }
        }
    }

    private suspend fun executeSyncLikedAlbums() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping syncLikedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(likedAlbums = SyncStatus.Syncing, currentOperation = "Syncing liked albums") }

        withRetry {
            YouTube.library("FEmusic_liked_albums").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                    val remoteIds = remoteAlbums.map { it.id }.toSet()
                    val localAlbums = database.albumsLikedByNameAsc().first()

                    localAlbums.filterNot { it.id in remoteIds }.forEach { album ->
                        try {
                            database.update(album.album.localToggleLike())
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update a liked album")
                        }
                    }

                    remoteAlbums.forEach { album ->
                        try {
                            val dbAlbum = database.album(album.id).firstOrNull()
                            YouTube.album(album.browseId).onSuccess { albumPage ->
                                if (dbAlbum == null) {
                                    database.insert(albumPage)
                                    database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                        database.update(newDbAlbum.album.localToggleLike())
                                    }
                                } else if (dbAlbum.album.bookmarkedAt == null) {
                                    database.update(dbAlbum.album.localToggleLike())
                                }
                            }
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to process a liked album")
                        }
                    }

                    updateState { copy(likedAlbums = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteAlbums.size} liked albums")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing liked albums")
                    updateState { copy(likedAlbums = e.toSyncError()) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked albums from YouTube")
                updateState { copy(likedAlbums = e.toSyncError()) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked albums after retries")
            updateState { copy(likedAlbums = e.toSyncError()) }
        }
    }

    private suspend fun executeSyncUploadedAlbums() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping syncUploadedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(uploadedAlbums = SyncStatus.Syncing, currentOperation = "Syncing uploaded albums") }

        withRetry {
            YouTube.library("FEmusic_library_privately_owned_releases").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                    val remoteIds = remoteAlbums.map { it.id }.toSet()
                    val localAlbums = database.albumsUploadedByNameAsc().first()

                    localAlbums.filterNot { it.id in remoteIds }.forEach { album ->
                        try {
                            database.update(album.album.toggleUploaded())
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update an uploaded album")
                        }
                    }

                    remoteAlbums.forEach { album ->
                        try {
                            val dbAlbum = database.album(album.id).firstOrNull()
                            YouTube.album(album.browseId).onSuccess { albumPage ->
                                if (dbAlbum == null) {
                                    database.insert(albumPage)
                                    database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                        database.update(newDbAlbum.album.toggleUploaded())
                                    }
                                } else if (!dbAlbum.album.isUploaded) {
                                    database.update(dbAlbum.album.toggleUploaded())
                                }
                            }.onFailure { reportException(it) }
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to process an uploaded album")
                        }
                    }

                    updateState { copy(uploadedAlbums = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteAlbums.size} uploaded albums")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing uploaded albums")
                    updateState { copy(uploadedAlbums = e.toSyncError()) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch uploaded albums from YouTube")
                updateState { copy(uploadedAlbums = e.toSyncError()) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync uploaded albums after retries")
            updateState { copy(uploadedAlbums = e.toSyncError()) }
        }
    }

    private suspend fun executeSyncArtistsSubscriptions() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping syncArtistsSubscriptions - user not logged in")
            return@withContext
        }

        updateState { copy(artists = SyncStatus.Syncing, currentOperation = "Syncing artist subscriptions") }

        withRetry {
            YouTube.library("FEmusic_library_corpus_artists").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteArtists = page.items.filterIsInstance<ArtistItem>()
                    val remoteIds = remoteArtists.map { it.id }.toSet()
                    val localArtists = database.artistsBookmarkedByNameAsc().first()

                    localArtists.filterNot { it.id in remoteIds }.forEach { artist ->
                        try {
                            database.update(artist.artist.localToggleLike())
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update an artist subscription")
                        }
                    }

                    remoteArtists.forEach { artist ->
                        try {
                            val dbArtist = database.artist(artist.id).firstOrNull()
                            val channelId = artist.channelId ?: if (artist.id.startsWith("UC")) {
                                try {
                                    YouTube.getChannelId(artist.id).takeIf { it.isNotEmpty() }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                            } else null

                            database.transaction {
                                if (dbArtist == null) {
                                    insert(
                                        ArtistEntity(
                                            id = artist.id,
                                            name = artist.title,
                                            thumbnailUrl = artist.thumbnail,
                                            channelId = channelId,
                                            bookmarkedAt = LocalDateTime.now()
                                        )
                                    )
                                } else {
                                    val existing = dbArtist.artist
                                    val needsChannelIdUpdate = existing.channelId == null && channelId != null
                                    if (existing.bookmarkedAt == null || needsChannelIdUpdate ||
                                        existing.name != artist.title || existing.thumbnailUrl != artist.thumbnail) {
                                        update(
                                            existing.copy(
                                                name = artist.title,
                                                thumbnailUrl = artist.thumbnail,
                                                channelId = channelId ?: existing.channelId,
                                                bookmarkedAt = existing.bookmarkedAt ?: LocalDateTime.now(),
                                                lastUpdateTime = LocalDateTime.now()
                                            )
                                        )
                                    }
                                }
                            }
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to process an artist subscription")
                        }
                    }

                    updateState { copy(artists = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteArtists.size} artist subscriptions")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing artist subscriptions")
                    updateState { copy(artists = e.toSyncError()) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch artist subscriptions from YouTube")
                updateState { copy(artists = e.toSyncError()) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync artist subscriptions after retries")
            updateState { copy(artists = e.toSyncError()) }
        }
    }

    private suspend fun executeSyncSavedPlaylists() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping syncSavedPlaylists - user not logged in")
            return@withContext
        }

        updateState { copy(playlists = SyncStatus.Syncing, currentOperation = "Syncing saved playlists") }

        withRetry {
            YouTube.library("FEmusic_liked_playlists").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                        .filterNot { it.id == "LM" || it.id == "SE" }
                        .reversed()
                        .distinctBy { it.id }
                    val remoteIds = remotePlaylists.map { it.id }.toSet()

                    val localRemotePlaylists = database.allRemotePlaylists().first()
                    val localByBrowseId = localRemotePlaylists
                        .groupBy { it.playlist.browseId }
                        .mapValues { (_, candidates) ->
                            candidates.firstOrNull { it.playlist.bookmarkedAt != null }
                                ?: candidates.maxBy { it.songCount }
                        }
                    var hadPlaylistErrors = false

                    localRemotePlaylists
                        .filter { it.playlist.bookmarkedAt != null }
                        .filterNot { it.playlist.browseId in remoteIds }
                        .forEach { playlist ->
                            try {
                                database.update(
                                    playlist.playlist.copy(
                                        bookmarkedAt = null,
                                        lastUpdateTime = LocalDateTime.now(),
                                    )
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to update a saved playlist")
                                hadPlaylistErrors = true
                            }
                        }

                    for (playlist in remotePlaylists) {
                        try {
                            val playlistEntity = localByBrowseId[playlist.id]?.playlist

                            if (playlistEntity == null) {
                                val newPlaylistEntity = PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = playlist.isEditable,
                                    bookmarkedAt = LocalDateTime.now(),
                                    remoteSongCount = playlist.remoteSongCount(),
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params
                                )
                                database.transaction {
                                    insert(newPlaylistEntity)
                                }
                                Timber.d("syncSavedPlaylists: Created a new playlist")
                            } else {
                                val updatedPlaylistEntity = playlistEntity.withRemoteMetadata(
                                    playlist = playlist,
                                    bookmarkedAt = playlistEntity.bookmarkedAt ?: LocalDateTime.now(),
                                )
                                if (updatedPlaylistEntity != playlistEntity) {
                                    database.update(
                                        updatedPlaylistEntity.copy(lastUpdateTime = LocalDateTime.now())
                                    )
                                    Timber.d("syncSavedPlaylists: Updated changed playlist metadata")
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync a saved playlist")
                            hadPlaylistErrors = true
                        }
                    }

                    if (hadPlaylistErrors) {
                        updateState {
                            copy(
                                playlists = SyncStatus.Error(
                                    "Some playlists could not be synchronized",
                                    SyncErrorKind.TEMPORARY,
                                )
                            )
                        }
                        Timber.w("Saved playlist sync completed with partial failures")
                    } else {
                        updateState { copy(playlists = SyncStatus.Completed) }
                        Timber.d("Synced ${remotePlaylists.size} saved playlists")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing saved playlists")
                    updateState { copy(playlists = e.toSyncError()) }
                }
            }.onFailure { e ->
                Timber.e(e, "syncSavedPlaylists: Failed to fetch playlists from YouTube")
                updateState { copy(playlists = e.toSyncError()) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync saved playlists after retries")
            updateState { copy(playlists = e.toSyncError()) }
        }
    }

    private suspend fun executeSyncAutoSyncPlaylists() = withContext(Dispatchers.IO) {
        if (!restoreStoredSession()) {
            Timber.w("Skipping syncAutoSyncPlaylists - user not logged in")
            return@withContext
        }

        try {
            val autoSyncPlaylists = database.playlistsByNameAsc().first()
                .filter { it.playlist.isAutoSync && it.playlist.browseId != null }

            Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

            autoSyncPlaylists.forEach { playlist ->
                try {
                    executeSyncPlaylist(playlist.playlist.browseId!!, playlist.playlist.id)
                    delay(DB_OPERATION_DELAY_MS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync an auto-sync playlist")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error syncing auto-sync playlists")
        }
    }

    private suspend fun executeSyncPlaylist(
        browseId: String,
        playlistId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        Timber.d("syncPlaylist: Starting remote playlist sync")

        val requestResult = withRetry {
            YouTube.playlist(browseId).completed()
        }
        val pageResult = requestResult.getOrElse { error ->
            Timber.e(error, "syncPlaylist: Failed after limited retries")
            return@withContext false
        }
        val page = pageResult.getOrElse { error ->
            Timber.e(error, "syncPlaylist: Failed to fetch playlist from YouTube")
            return@withContext false
        }

        try {
            val songs = page.songs.map(SongItem::toMediaMetadata)
            Timber.d("syncPlaylist: Fetched ${songs.size} songs from remote")

            val remoteIds = songs.map { it.id }
            val localIds = database.playlistSongs(playlistId).first()
                .sortedBy { it.map.position }
                .map { it.song.id }

            val storedPlaylist = database.getPlaylistByIdBlocking(playlistId)?.playlist
            val refreshedPlaylist = storedPlaylist?.withRemoteMetadata(
                playlist = page.playlist,
                bookmarkedAt = storedPlaylist.bookmarkedAt,
                remoteSongCount = page.playlist.songCount ?: songs.size,
            )

            if (remoteIds == localIds) {
                if (storedPlaylist != null && refreshedPlaylist != storedPlaylist) {
                    database.update(
                        checkNotNull(refreshedPlaylist).copy(lastUpdateTime = LocalDateTime.now())
                    )
                }
                Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                return@withContext true
            }

            database.withTransaction {
                database.clearPlaylist(playlistId)
                songs.forEachIndexed { idx, song ->
                    if (database.song(song.id).firstOrNull() == null) {
                        database.insert(song)
                    }
                    database.insert(
                        PlaylistSongMap(
                            songId = song.id,
                            playlistId = playlistId,
                            position = idx,
                            setVideoId = song.setVideoId,
                        )
                    )
                }
                refreshedPlaylist?.let {
                    database.update(it.copy(lastUpdateTime = LocalDateTime.now()))
                }
            }
            Timber.d("syncPlaylist: Successfully synced playlist")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error processing playlist sync")
            false
        }
    }

    private fun PlaylistItem.remoteSongCount(): Int? =
        songCount

    private fun PlaylistEntity.withRemoteMetadata(
        playlist: PlaylistItem,
        bookmarkedAt: LocalDateTime?,
        remoteSongCount: Int? = playlist.remoteSongCount(),
    ) = copy(
        name = playlist.title,
        browseId = playlist.id,
        thumbnailUrl = playlist.thumbnail,
        isEditable = playlist.isEditable,
        bookmarkedAt = bookmarkedAt,
        remoteSongCount = remoteSongCount ?: this.remoteSongCount,
        playEndpointParams = playlist.playEndpoint?.params,
        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
        radioEndpointParams = playlist.radioEndpoint?.params,
    )

    private suspend fun executeCleanupDuplicatePlaylists() = withContext(Dispatchers.IO) {
        try {
            val allPlaylists = database.playlistsByNameAsc().first()
            val browseIdGroups = allPlaylists
                .filter { it.playlist.browseId != null }
                .groupBy { it.playlist.browseId }

            for ((browseId, playlists) in browseIdGroups) {
                if (playlists.size > 1) {
                    Timber.w("Found ${playlists.size} duplicate remote playlists")
                    val toKeep = playlists.maxByOrNull { it.songCount } ?: playlists.first()

                    playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                        try {
                            Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                            database.clearPlaylist(duplicate.id)
                            database.delete(duplicate.playlist)
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to remove duplicate playlist: ${duplicate.id}")
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up duplicate playlists")
        }
    }

    private suspend fun executeClearAllSyncedContent() = withContext(Dispatchers.IO) {
        Timber.d("clearAllSyncedContent: Starting cleanup")

        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Clearing synced content") }

        try {
            database.withTransaction {
                
                val likedSongs = database.likedSongsByNameAsc().first()
                likedSongs.forEach {
                    database.update(it.song.copy(liked = false, likedDate = null))
                }

                
                val librarySongs = database.songsByNameAsc().first()
                librarySongs.forEach {
                    if (it.song.inLibrary != null) {
                        database.update(it.song.copy(inLibrary = null))
                    }
                }

                
                val likedAlbums = database.albumsLikedByNameAsc().first()
                likedAlbums.forEach {
                    database.update(it.album.copy(bookmarkedAt = null))
                }

                
                val subscribedArtists = database.artistsBookmarkedByNameAsc().first()
                subscribedArtists.forEach {
                    database.update(it.artist.copy(bookmarkedAt = null))
                }

                
                val savedPlaylists = database.playlistsByNameAsc().first()
                savedPlaylists.forEach {
                    if (it.playlist.browseId != null) {
                        database.clearPlaylist(it.playlist.id)
                        database.delete(it.playlist)
                    }
                }

                
                val uploadedSongs = database.uploadedSongsByNameAsc().first()
                uploadedSongs.forEach {
                    database.update(it.song.copy(isUploaded = false))
                }

                
                val uploadedAlbums = database.albumsUploadedByCreateDateAsc().first()
                uploadedAlbums.forEach {
                    database.update(it.album.copy(isUploaded = false))
                }
            }

            
            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = 0L
            }

            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
            Timber.d("clearAllSyncedContent: Cleanup completed successfully")
        } catch (e: CancellationException) {
            updateState { copy(overallStatus = SyncStatus.Idle, currentOperation = "") }
            throw e
        } catch (e: Exception) {
            Timber.e(e, "clearAllSyncedContent: Error during cleanup")
            updateState { copy(overallStatus = e.toSyncError(), currentOperation = "") }
        }
    }

    fun cancelAllSyncs() {
        processingJob?.cancel()
        startProcessingQueue()
        updateState { SyncState() }
    }
}
