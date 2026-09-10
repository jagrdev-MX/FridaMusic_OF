package com.jagr.fridamusic.presentation.components

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.LyricsEntity
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.db.entities.SpeedDialItem
import com.jagr.fridamusic.localmedia.LocalMediaStoreActionResult
import com.jagr.fridamusic.localmedia.LocalSongMetadataUpdate
import com.jagr.fridamusic.localmedia.LocalSongSortMetadata
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.LocalSongActionsNavigation
import com.jagr.fridamusic.playback.ExoDownloadService
import com.jagr.fridamusic.utils.openLocalAudioFolder
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.LocalSongsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class SongMenuActions(
    val onToggleFavorite: (() -> Unit)? = null,
    val onToggleLibrary: (() -> Unit)? = null,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val onToggleDownload: (() -> Unit)? = null,
    val onPlayNext: (() -> Unit)? = null,
    val onAddToQueue: (() -> Unit)? = null,
    val playlists: List<Playlist> = emptyList(),
    val onAddToPlaylist: ((Playlist) -> Unit)? = null,
    val isInPlaylist: (suspend (Playlist) -> Boolean)? = null,
    val onReaddToPlaylist: ((Playlist) -> Unit)? = null,
    val onRemoveFromPlaylist: (() -> Unit)? = null,
    val isPinned: Boolean = false,
    val onTogglePinned: (() -> Unit)? = null,
    val onGoToAlbum: ((SongAlbumTarget) -> Unit)? = null,
    val onGoToArtist: ((SongNavigationTarget) -> Unit)? = null,
    val onGoToFolder: (() -> Unit)? = null,
    val localSong: Song? = null,
    val localMetadata: LocalSongSortMetadata? = null,
    val onEditMetadata: ((String, String, String) -> Unit)? = null,
    val initialLyrics: String = "",
    val onEditLyrics: ((String) -> Unit)? = null,
    val isBlacklisted: Boolean = false,
    val onBlacklist: (() -> Unit)? = null,
    val onMoveToQueueStart: (() -> Unit)? = null,
    val onMoveToQueueEnd: (() -> Unit)? = null,
    val onRemoveFromQueue: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
)

enum class SongActionsSubview {
    NONE,
    ARTIST_PICKER,
    ALBUM_ARTIST_PICKER,
    DETAILS,
    SHARE,
    SHARE_CARD,
    PLAYLIST,
    EDIT_METADATA,
    EDIT_LYRICS,
    DELETE_CONFIRM,
}

@Composable
fun SongOptionsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = Color.Transparent,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = iconColor,
        ),
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more_options),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun UniversalSongActionsHost(
    context: SongActionContext?,
    onDismiss: () -> Unit,
    onMoveToQueueStart: (() -> Unit)? = null,
    onMoveToQueueEnd: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    localSongsViewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val selectedContext = context ?: return
    val androidContext = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val navigation = LocalSongActionsNavigation.current
    val scope = rememberCoroutineScope()
    val database = playerConnection?.database
    val sortMetadata by localSongsViewModel.sortMetadata.collectAsState()
    val pinnedLocalIds by localSongsViewModel.pinnedSongIds.collectAsState()
    val blacklistedSongIds by localSongsViewModel.blacklistedSongIds.collectAsState()
    val localMetadata = sortMetadata[selectedContext.mediaId]
    val storedSongFlow = remember(database, selectedContext.mediaId) {
        database?.song(selectedContext.mediaId) ?: flowOf(null)
    }
    val storedSong by storedSongFlow.collectAsState(initial = selectedContext.databaseSong)
    val lyricsFlow = remember(database, selectedContext.mediaId) {
        database?.lyrics(selectedContext.mediaId) ?: flowOf(null)
    }
    val lyrics by lyricsFlow.collectAsState(initial = null)
    val playlistsFlow = remember(database) { database?.playlistsByCreateDateAsc() ?: flowOf(emptyList()) }
    val playlists by playlistsFlow.collectAsState(initial = emptyList())
    val remotePinnedFlow = remember(database, selectedContext.mediaId) {
        database?.speedDialDao?.isPinned(selectedContext.mediaId) ?: flowOf(false)
    }
    val remotePinned by remotePinnedFlow.collectAsState(initial = false)
    val effectiveContext = remember(selectedContext, storedSong, localMetadata) {
        storedSong?.toSongActionContext(
            localMetadata = localMetadata,
            albumArtists = selectedContext.albumArtists,
        )?.copy(
            mediaItem = selectedContext.mediaItem,
            remoteIds = selectedContext.remoteIds,
            playlistId = selectedContext.playlistId,
            playlistEntryId = selectedContext.playlistEntryId,
        ) ?: selectedContext.withLocalMetadata(localMetadata)
    }
    val isLocal = effectiveContext.source == SongActionSource.LOCAL_FILE
    val isPinned = if (isLocal) effectiveContext.mediaId in pinnedLocalIds else remotePinned
    val isBlacklisted = isLocal && effectiveContext.mediaId in blacklistedSongIds
    val downloadUtil = playerConnection?.service?.mediaLibrarySessionCallback?.downloadUtil
    val downloadFlow = remember(downloadUtil, effectiveContext.mediaId) {
        downloadUtil?.getDownload(effectiveContext.mediaId) ?: flowOf(null)
    }
    val download by downloadFlow.collectAsState(initial = null)
    val isDownloaded = download?.state == Download.STATE_COMPLETED
    val isDownloading = download?.state == Download.STATE_QUEUED || download?.state == Download.STATE_DOWNLOADING
    val playbackPosition by (playerConnection?.playbackPositionMs ?: flowOf(0L)).collectAsState(initial = 0L)
    val currentMediaId by (playerConnection?.mediaMetadata ?: flowOf(null)).collectAsState(initial = null)
    val sharePosition = if (currentMediaId?.id == effectiveContext.mediaId) playbackPosition else 0L

    var pendingMetadataUpdate by remember { mutableStateOf<LocalSongMetadataUpdate?>(null) }
    var pendingDeleteSongId by remember { mutableStateOf<String?>(null) }

    fun toast(messageRes: Int) {
        android.widget.Toast.makeText(androidContext, messageRes, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun removeDeletedSongFromQueue(songId: String) {
        val player = playerConnection?.player ?: return
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId == songId) player.removeMediaItem(index)
        }
    }

    fun completeMetadataUpdate() {
        val update = pendingMetadataUpdate ?: return
        localSongsViewModel.updateSongMetadata(update, requestPermission = false) { result ->
            if (result == LocalMediaStoreActionResult.Success) toast(R.string.local_song_information_saved)
            else toast(R.string.local_song_action_failed)
            pendingMetadataUpdate = null
        }
    }

    fun completeLegacyDelete() {
        val songId = pendingDeleteSongId ?: return
        localSongsViewModel.deleteSongFromDevice(songId, requestPermission = false) { result ->
            if (result == LocalMediaStoreActionResult.Success) {
                removeDeletedSongFromQueue(songId)
                toast(R.string.local_song_deleted)
            } else {
                toast(R.string.local_song_action_failed)
            }
            pendingDeleteSongId = null
            onDismiss()
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when {
                pendingMetadataUpdate != null -> completeMetadataUpdate()
                pendingDeleteSongId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val songId = pendingDeleteSongId
                    localSongsViewModel.refreshAfterExternalMediaAction()
                    songId?.let(::removeDeletedSongFromQueue)
                    toast(R.string.local_song_deleted)
                    pendingDeleteSongId = null
                    onDismiss()
                }
                pendingDeleteSongId != null -> completeLegacyDelete()
            }
        } else {
            val wasDeleting = pendingDeleteSongId != null
            pendingMetadataUpdate = null
            pendingDeleteSongId = null
            if (wasDeleting) onDismiss()
        }
    }

    val legacyWritePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (pendingMetadataUpdate != null) completeMetadataUpdate() else completeLegacyDelete()
        } else {
            val wasDeleting = pendingDeleteSongId != null
            toast(R.string.local_song_action_failed)
            pendingMetadataUpdate = null
            pendingDeleteSongId = null
            if (wasDeleting) onDismiss()
        }
    }

    fun launchMediaPermission(result: LocalMediaStoreActionResult.PermissionRequired) {
        runCatching {
            mediaPermissionLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
        }.onFailure {
            val wasDeleting = pendingDeleteSongId != null
            toast(R.string.local_song_action_failed)
            pendingMetadataUpdate = null
            pendingDeleteSongId = null
            if (wasDeleting) onDismiss()
        }
    }

    fun startMetadataUpdate(title: String, artist: String, album: String) {
        val update = LocalSongMetadataUpdate(effectiveContext.mediaId, title, artist, album)
        pendingDeleteSongId = null
        pendingMetadataUpdate = update
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(androidContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        localSongsViewModel.updateSongMetadata(update, requestPermission = true) { result ->
            when (result) {
                LocalMediaStoreActionResult.Success -> {
                    toast(R.string.local_song_information_saved)
                    pendingMetadataUpdate = null
                }
                is LocalMediaStoreActionResult.PermissionRequired -> launchMediaPermission(result)
                is LocalMediaStoreActionResult.Failure -> {
                    toast(R.string.local_song_action_failed)
                    pendingMetadataUpdate = null
                }
            }
        }
    }

    fun startDelete() {
        val songId = effectiveContext.mediaId
        pendingMetadataUpdate = null
        pendingDeleteSongId = songId
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(androidContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        localSongsViewModel.deleteSongFromDevice(songId, requestPermission = true) { result ->
            when (result) {
                LocalMediaStoreActionResult.Success -> {
                    removeDeletedSongFromQueue(songId)
                    toast(R.string.local_song_deleted)
                    pendingDeleteSongId = null
                    onDismiss()
                }
                is LocalMediaStoreActionResult.PermissionRequired -> launchMediaPermission(result)
                is LocalMediaStoreActionResult.Failure -> {
                    toast(R.string.local_song_action_failed)
                    pendingDeleteSongId = null
                    onDismiss()
                }
            }
        }
    }

    fun toggleFavorite() {
        val db = database ?: return
        val existing = storedSong
        if (existing != null) {
            db.query {
                update(if (existing.song.isLocal) existing.song.localToggleLike() else existing.song.toggleLike())
            }
        } else {
            db.query { insert(effectiveContext.toMediaMetadata()) { it.copy(liked = true, likedDate = java.time.LocalDateTime.now()) } }
        }
    }

    fun toggleLibrary() {
        val db = database ?: return
        val existing = storedSong
        if (existing != null) {
            db.query { update(existing.song.toggleLibrary(syncToYouTube = !existing.song.isLocal)) }
        } else {
            db.query { insert(effectiveContext.toMediaMetadata()) { it.copy(inLibrary = java.time.LocalDateTime.now()) } }
        }
    }

    val actions = SongMenuActions(
        onToggleFavorite = database?.let { { toggleFavorite() } },
        onToggleLibrary = if (!isLocal && database != null) ({ toggleLibrary() }) else null,
        isDownloaded = isDownloaded,
        isDownloading = isDownloading,
        onToggleDownload = if (!isLocal && !isDownloading) {
            {
                if (isDownloaded) {
                    DownloadService.sendRemoveDownload(
                        androidContext,
                        ExoDownloadService::class.java,
                        effectiveContext.mediaId,
                        false,
                    )
                } else {
                    DownloadService.sendAddDownload(
                        androidContext,
                        ExoDownloadService::class.java,
                        DownloadRequest.Builder(effectiveContext.mediaId, effectiveContext.mediaId.toUri())
                            .setCustomCacheKey(effectiveContext.mediaId)
                            .setData(effectiveContext.title.toByteArray())
                            .build(),
                        false,
                    )
                }
            }
        } else null,
        onPlayNext = playerConnection?.let { { it.playNext(effectiveContext.mediaItem) } },
        onAddToQueue = playerConnection?.let { { it.addToQueue(effectiveContext.mediaItem) } },
        playlists = playlists.filter { it.playlist.isEditable },
        onAddToPlaylist = database?.let { db ->
            { playlist ->
                db.query {
                    insert(effectiveContext.toMediaMetadata())
                    addSongToPlaylist(playlist, listOf(effectiveContext.mediaId))
                }
            }
        },
        isInPlaylist = database?.let { db ->
            { playlist ->
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    db.checkInPlaylist(playlist.id, effectiveContext.mediaId) > 0
                }
            }
        },
        onReaddToPlaylist = database?.let { db ->
            { playlist ->
                db.query {
                    insert(effectiveContext.toMediaMetadata())
                    prependSongToPlaylist(playlist.id, effectiveContext.mediaId)
                }
            }
        },
        onRemoveFromPlaylist = effectiveContext.playlistId?.let { playlistId ->
            database?.let { db ->
                {
                    db.query {
                        removeSongFromPlaylist(
                            playlistId = playlistId,
                            songId = effectiveContext.mediaId,
                            entryId = effectiveContext.playlistEntryId,
                        )
                    }
                }
            }
        },
        isPinned = isPinned,
        onTogglePinned = if (isLocal) {
            { localSongsViewModel.togglePinnedSong(effectiveContext.mediaId) }
        } else if (database != null) {
            {
                scope.launch(Dispatchers.IO) {
                    if (isPinned) {
                        database.speedDialDao.delete(effectiveContext.mediaId)
                    } else {
                        database.speedDialDao.insert(
                            SpeedDialItem(
                                id = effectiveContext.mediaId,
                                title = effectiveContext.title,
                                subtitle = effectiveContext.artists.joinToString(", ") { it.name },
                                thumbnailUrl = effectiveContext.artworkUrl,
                                type = "SONG",
                            ),
                        )
                    }
                }
            }
        } else null,
        onGoToAlbum = { target -> target.id?.let(navigation.openAlbum) },
        onGoToArtist = { target -> target.id?.let(navigation.openArtist) },
        onGoToFolder = effectiveContext.localFile?.takeIf { it.canOpenFolder }?.let { file ->
            {
                if (!openLocalAudioFolder(androidContext, file.relativePath, file.absolutePath)) {
                    toast(R.string.local_song_folder_unavailable)
                }
            }
        },
        localSong = storedSong,
        localMetadata = localMetadata,
        onEditMetadata = if (isLocal && storedSong != null) ::startMetadataUpdate else null,
        initialLyrics = lyrics?.lyrics.orEmpty(),
        onEditLyrics = database?.let { db ->
            { value ->
                db.query { upsert(LyricsEntity(effectiveContext.mediaId, value.trim(), provider = "Manual")) }
            }
        },
        isBlacklisted = isBlacklisted,
        onBlacklist = if (isLocal) {
            { localSongsViewModel.setSongBlacklisted(effectiveContext.mediaId, blacklisted = !isBlacklisted) }
        } else null,
        onMoveToQueueStart = onMoveToQueueStart,
        onMoveToQueueEnd = onMoveToQueueEnd,
        onRemoveFromQueue = onRemoveFromQueue,
        onDelete = if (isLocal && storedSong != null) ::startDelete else null,
    )

    SongActionsSheet(
        context = effectiveContext,
        onDismiss = onDismiss,
        actions = actions,
        playbackPositionMs = sharePosition,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    context: SongActionContext,
    onDismiss: () -> Unit,
    actions: SongMenuActions,
    playbackPositionMs: Long = 0L,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var subview by remember(context.mediaId) { mutableStateOf(SongActionsSubview.NONE) }
    var duplicatePlaylist by remember(context.mediaId) { mutableStateOf<Playlist?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val capabilities = resolveSongActionCapabilities(
        context = context,
        availability = SongActionAvailability(
            playerAvailable = actions.onPlayNext != null && actions.onAddToQueue != null,
            playlistAvailable = actions.onAddToPlaylist != null,
            pinAvailable = actions.onTogglePinned != null,
            metadataEditorAvailable = actions.onEditMetadata != null && actions.localSong != null,
            lyricsEditorAvailable = actions.onEditLyrics != null,
            blacklistAvailable = actions.onBlacklist != null,
            queueRemovalAvailable = actions.onRemoveFromQueue != null,
            deleteAvailable = actions.onDelete != null,
        ),
    )
    val navigableArtists = context.artists.filter(SongNavigationTarget::isNavigable)
    val navigableAlbumArtists = context.albumArtists.filter(SongNavigationTarget::isNavigable)

    fun performAndDismiss(action: (() -> Unit)?) {
        action?.invoke()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = context.artworkUrl?.resize(width = 112),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(context.artists.joinToString(", ") { it.name })
                            context.album?.title?.takeIf(String::isNotBlank)?.let { album ->
                                if (isNotEmpty()) append("  ·  ")
                                append(album)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                actions.onToggleFavorite?.let { toggle ->
                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggle()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        val favorite = actions.localSong?.song?.liked == true
                        Icon(
                            imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(
                                if (favorite) R.string.local_song_remove_favorite else R.string.local_song_add_favorite,
                            ),
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                actions.onAddToPlaylist?.let {
                    FilledTonalIconButton(
                        onClick = { subview = SongActionsSubview.PLAYLIST },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_to_playlist),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (context.source != SongActionSource.LOCAL_FILE) item(key = "download") {
                    SongActionGroup {
                        SongAction(
                            icon = if (actions.isDownloaded) Icons.Rounded.Delete else Icons.Rounded.Download,
                            label = stringResource(
                                when {
                                    actions.isDownloading -> R.string.downloading_ellipsis
                                    actions.isDownloaded -> R.string.remove_download
                                    else -> R.string.action_download
                                },
                            ),
                            enabled = !actions.isDownloading,
                        ) {
                            performAndDismiss(actions.onToggleDownload)
                        }
                    }
                }
                item(key = "playback") {
                    SongActionGroup {
                        if (SongActionCapability.PLAY_NEXT in capabilities) {
                            SongAction(Icons.Rounded.SkipNext, stringResource(R.string.play_next)) {
                                performAndDismiss(actions.onPlayNext)
                            }
                        }
                        if (SongActionCapability.ADD_TO_QUEUE in capabilities) {
                            SongAction(Icons.Rounded.Queue, stringResource(R.string.add_to_queue)) {
                                performAndDismiss(actions.onAddToQueue)
                            }
                        }
                        if (SongActionCapability.ADD_TO_PLAYLIST in capabilities) {
                            SongAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) {
                                subview = SongActionsSubview.PLAYLIST
                            }
                        }
                        actions.onRemoveFromPlaylist?.let { remove ->
                            SongAction(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove_from_playlist)) {
                                performAndDismiss(remove)
                            }
                        }
                        if (SongActionCapability.PIN in capabilities) {
                            SongAction(
                                Icons.Rounded.PushPin,
                                stringResource(if (actions.isPinned) R.string.local_song_unpin else R.string.local_song_pin),
                                onClick = actions.onTogglePinned ?: {},
                            )
                        }
                        actions.onMoveToQueueStart?.let { moveToStart ->
                            SongAction(Icons.Rounded.ArrowUpward, stringResource(R.string.move_to_queue_start)) {
                                performAndDismiss(moveToStart)
                            }
                        }
                        actions.onMoveToQueueEnd?.let { moveToEnd ->
                            SongAction(Icons.Rounded.ArrowDownward, stringResource(R.string.move_to_queue_end)) {
                                performAndDismiss(moveToEnd)
                            }
                        }
                        if (SongActionCapability.REMOVE_FROM_QUEUE in capabilities) {
                            SongAction(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove_from_queue)) {
                                performAndDismiss(actions.onRemoveFromQueue)
                            }
                        }
                    }
                }
                if (capabilities.any { it in EXPLORE_CAPABILITIES }) item(key = "explore") {
                    SongActionGroup {
                        if (SongActionCapability.GO_TO_ALBUM in capabilities) {
                            SongAction(Icons.Rounded.Album, stringResource(R.string.local_song_go_to_album)) {
                                context.album?.let { actions.onGoToAlbum?.invoke(it) }
                                onDismiss()
                            }
                        }
                        if (SongActionCapability.GO_TO_ARTIST in capabilities) {
                            SongAction(Icons.Rounded.Person, stringResource(R.string.local_song_go_to_artist)) {
                                if (navigableArtists.size == 1) {
                                    actions.onGoToArtist?.invoke(navigableArtists.single())
                                    onDismiss()
                                } else {
                                    subview = SongActionsSubview.ARTIST_PICKER
                                }
                            }
                        }
                        if (SongActionCapability.GO_TO_ALBUM_ARTIST in capabilities) {
                            SongAction(Icons.Rounded.SupervisorAccount, stringResource(R.string.local_song_go_to_album_artist)) {
                                if (navigableAlbumArtists.size == 1) {
                                    actions.onGoToArtist?.invoke(navigableAlbumArtists.single())
                                    onDismiss()
                                } else {
                                    subview = SongActionsSubview.ALBUM_ARTIST_PICKER
                                }
                            }
                        }
                        if (SongActionCapability.OPEN_FOLDER in capabilities) {
                            SongAction(Icons.Rounded.FolderOpen, stringResource(R.string.local_song_go_to_folder)) {
                                performAndDismiss(actions.onGoToFolder)
                            }
                        }
                    }
                }
                if (capabilities.any { it in MANAGE_CAPABILITIES }) item(key = "manage") {
                    SongActionGroup {
                        if (SongActionCapability.EDIT_METADATA in capabilities) {
                            SongAction(Icons.Rounded.Edit, stringResource(R.string.local_song_edit_information)) {
                                subview = SongActionsSubview.EDIT_METADATA
                            }
                        }
                        if (SongActionCapability.EDIT_LYRICS in capabilities) {
                            SongAction(Icons.Rounded.Lyrics, stringResource(R.string.edit_lyrics)) {
                                subview = SongActionsSubview.EDIT_LYRICS
                            }
                        }
                        if (SongActionCapability.BLACKLIST in capabilities) {
                            SongAction(
                                if (actions.isBlacklisted) Icons.Rounded.RemoveCircleOutline else Icons.Rounded.Block,
                                stringResource(
                                    if (actions.isBlacklisted) R.string.local_song_remove_from_blacklist
                                    else R.string.local_song_blacklist,
                                ),
                            ) {
                                performAndDismiss(actions.onBlacklist)
                            }
                        }
                    }
                }
                item(key = "details") {
                    SongActionGroup {
                        SongAction(Icons.Rounded.Info, stringResource(R.string.details)) {
                            subview = SongActionsSubview.DETAILS
                        }
                    }
                }
                item(key = "share") {
                    SongActionGroup {
                        if (SongActionCapability.SHARE_LINK in capabilities ||
                            SongActionCapability.SHARE_FILE in capabilities
                        ) {
                            SongAction(Icons.Rounded.Share, stringResource(R.string.share)) {
                                subview = SongActionsSubview.SHARE
                            }
                        }
                        SongAction(Icons.Rounded.Share, stringResource(R.string.share_now_playing)) {
                            subview = SongActionsSubview.SHARE_CARD
                        }
                    }
                }
                if (SongActionCapability.DELETE_LOCAL_FILE in capabilities) item(key = "destructive") {
                    SongActionGroup {
                        SongAction(
                            Icons.Rounded.Delete,
                            stringResource(R.string.local_song_delete_from_device),
                            destructive = true,
                            onClick = { subview = SongActionsSubview.DELETE_CONFIRM },
                        )
                    }
                }
            }
        }
    }

    when (subview) {
        SongActionsSubview.ARTIST_PICKER -> SongTargetPickerDialog(
            title = stringResource(R.string.local_song_go_to_artist),
            targets = navigableArtists,
            onDismiss = { subview = SongActionsSubview.NONE },
            onSelect = {
                actions.onGoToArtist?.invoke(it)
                onDismiss()
            },
        )
        SongActionsSubview.ALBUM_ARTIST_PICKER -> SongTargetPickerDialog(
            title = stringResource(R.string.local_song_go_to_album_artist),
            targets = navigableAlbumArtists,
            onDismiss = { subview = SongActionsSubview.NONE },
            onSelect = {
                actions.onGoToArtist?.invoke(it)
                onDismiss()
            },
        )
        SongActionsSubview.DETAILS -> SongDetailsDialog(
            context = context,
            onDismiss = { subview = SongActionsSubview.NONE },
        )
        SongActionsSubview.SHARE -> SongShareOptionsDialog(
            context = context,
            onDismiss = { subview = SongActionsSubview.NONE },
            onOpenShareCard = { subview = SongActionsSubview.SHARE_CARD },
        )
        SongActionsSubview.SHARE_CARD -> FridaShareCardDialog(
            context = context,
            playbackPositionMs = playbackPositionMs,
            onDismiss = { subview = SongActionsSubview.NONE },
        )
        SongActionsSubview.PLAYLIST -> SongPlaylistPickerDialog(
            playlists = actions.playlists,
            onDismiss = { subview = SongActionsSubview.NONE },
            onSelect = { playlist ->
                scope.launch {
                    val duplicate = actions.isInPlaylist?.invoke(playlist) == true
                    subview = SongActionsSubview.NONE
                    if (duplicate) {
                        duplicatePlaylist = playlist
                    } else {
                        actions.onAddToPlaylist?.invoke(playlist)
                    }
                }
            },
        )
        SongActionsSubview.EDIT_METADATA -> actions.localSong?.let { song ->
            LocalSongMetadataEditorDialog(
                song = song,
                metadata = actions.localMetadata,
                onDismiss = { subview = SongActionsSubview.NONE },
                onSave = { title, artist, album ->
                    actions.onEditMetadata?.invoke(title, artist, album)
                    subview = SongActionsSubview.NONE
                },
            )
        }
        SongActionsSubview.EDIT_LYRICS -> LocalSongLyricsEditorDialog(
            songId = context.mediaId,
            initialLyrics = actions.initialLyrics,
            onDismiss = { subview = SongActionsSubview.NONE },
            onSave = {
                actions.onEditLyrics?.invoke(it)
                subview = SongActionsSubview.NONE
            },
        )
        SongActionsSubview.DELETE_CONFIRM -> DeleteLocalSongDialog(
            title = context.title,
            onDismiss = { subview = SongActionsSubview.NONE },
            onConfirm = {
                actions.onDelete?.invoke()
                subview = SongActionsSubview.NONE
            },
        )
        SongActionsSubview.NONE -> Unit
    }

    duplicatePlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { duplicatePlaylist = null },
            title = { Text(stringResource(R.string.duplicates)) },
            text = {
                Text(
                    stringResource(
                        R.string.duplicate_song_readd_description,
                        playlist.playlist.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        (actions.onReaddToPlaylist ?: actions.onAddToPlaylist)?.invoke(playlist)
                        duplicatePlaylist = null
                    },
                ) {
                    Text(stringResource(R.string.add_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicatePlaylist = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SongTargetPickerDialog(
    title: String,
    targets: List<SongNavigationTarget>,
    onDismiss: () -> Unit,
    onSelect: (SongNavigationTarget) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                targets.forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(target) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(target.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SongDetailsDialog(
    context: SongActionContext,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale) }
    val zone = remember { ZoneId.systemDefault() }
    val identity = listOfNotNull(
        stringResource(R.string.local_song_title_field) to context.title,
        context.artists.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.sort_by_artist) to context.artists.joinToString(", ") { artist -> artist.name }
        },
        context.album?.title?.takeIf(String::isNotBlank)?.let { stringResource(R.string.sort_by_album) to it },
        context.albumArtists.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.sort_by_album_artist) to context.albumArtists.joinToString(", ") { artist -> artist.name }
        },
    )
    val music = listOfNotNull(
        context.metadata.genres.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.sort_by_genre) to context.metadata.genres.joinToString(", ")
        },
        context.durationMs?.takeIf { it >= 0L }?.let {
            stringResource(R.string.sort_by_length) to formatSongDuration(it)
        },
        context.metadata.composers.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.sort_by_composer) to context.metadata.composers.joinToString(", ")
        },
    )
    val file = listOfNotNull(
        context.localFile?.displayName?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.local_song_file_name) to it
        },
        (context.localFile?.relativePath ?: context.localFile?.absolutePath)?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.local_song_folder) to it
        },
        context.metadata.audioFormat?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.local_song_format) to it
        },
        context.metadata.codec?.takeIf(String::isNotBlank)?.let { stringResource(R.string.codecs) to it },
        context.metadata.bitrate?.takeIf { it > 0 }?.let { stringResource(R.string.bitrate) to "${it / 1000} kbps" },
        context.metadata.sampleRate?.takeIf { it > 0 }?.let { stringResource(R.string.sample_rate) to "$it Hz" },
        (context.metadata.fileSizeBytes ?: context.localFile?.sizeBytes)?.takeIf { it > 0L }?.let {
            stringResource(R.string.local_song_file_size) to formatFileSize(it)
        },
    )
    val dates = listOfNotNull(
        context.metadata.dateModifiedEpochSeconds?.let {
            stringResource(R.string.sort_by_date_modified) to formatter.format(Instant.ofEpochSecond(it).atZone(zone).toLocalDateTime())
        },
        context.metadata.dateAddedEpochSeconds?.let {
            stringResource(R.string.sort_by_date_added) to formatter.format(Instant.ofEpochSecond(it).atZone(zone).toLocalDateTime())
        },
        context.metadata.dateCreatedEpochSeconds?.let {
            stringResource(R.string.song_date_created) to formatter.format(Instant.ofEpochSecond(it).atZone(zone).toLocalDateTime())
        },
        context.metadata.releaseDate?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.song_release_date) to it
        },
        context.metadata.releaseYear?.let { stringResource(R.string.sort_by_year) to it.toString() },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailsGroup(stringResource(R.string.song_details_identity), identity)
                if (music.isNotEmpty()) DetailsGroup(stringResource(R.string.song_details_music), music)
                if (file.isNotEmpty()) DetailsGroup(stringResource(R.string.song_details_file), file)
                if (dates.isNotEmpty()) DetailsGroup(stringResource(R.string.song_details_dates), dates)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun DetailsGroup(title: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        rows.forEach { (label, value) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SongActionGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SongAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(23.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

private fun formatSongDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1_000L
    return "%d:%02d".format(Locale.ROOT, seconds / 60L, seconds % 60L)
}

private fun formatFileSize(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) String.format(Locale.ROOT, "%.1f MB", megabytes)
    else String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
}

private val EXPLORE_CAPABILITIES = setOf(
    SongActionCapability.GO_TO_ALBUM,
    SongActionCapability.GO_TO_ARTIST,
    SongActionCapability.GO_TO_ALBUM_ARTIST,
    SongActionCapability.OPEN_FOLDER,
)

private val MANAGE_CAPABILITIES = setOf(
    SongActionCapability.EDIT_METADATA,
    SongActionCapability.EDIT_LYRICS,
    SongActionCapability.BLACKLIST,
)
