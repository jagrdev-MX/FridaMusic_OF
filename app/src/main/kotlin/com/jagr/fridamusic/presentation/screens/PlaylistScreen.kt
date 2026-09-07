package com.jagr.fridamusic.presentation.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.playback.queues.ListQueue
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.FridaLoadingDefaults
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.UniversalSongRow
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.presentation.components.universalMediaClickable
import com.jagr.fridamusic.presentation.playYTItem
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.LocalPlaylistViewModel
import com.jagr.fridamusic.viewmodels.OnlinePlaylistViewModel
import com.jagr.fridamusic.viewmodels.PlaylistsViewModel
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale


@Composable
fun LocalPlaylistScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onBack: () -> Unit,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val playlist by viewModel.playlist.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val songs = playlistSongs.map { it.song }
    val currentSongIdFlow = remember(playerConnection) {
        playerConnection?.mediaMetadata?.map { it?.id } ?: flowOf(null)
    }
    val isPlayingFlow = remember(playerConnection) {
        playerConnection?.isEffectivelyPlaying ?: flowOf(false)
    }
    val currentSongId by currentSongIdFlow.collectAsState(initial = null)
    val isPlaying by isPlayingFlow.collectAsState(initial = false)
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }

    val thumbnailUrl = playlist?.thumbnails?.firstOrNull()
    val title = playlist?.playlist?.name ?: ""
    val localDurations = songs.mapNotNull { it.song.duration.takeIf { duration -> duration > 0 } }
    val localDurationSummary = localDurations
        .takeIf { it.size == songs.size && it.isNotEmpty() }
        ?.sum()
        ?.let { formatPlaylistDuration(it) }
    val localMetadata = listOfNotNull(
        songs.takeIf { it.isNotEmpty() }?.let {
            pluralStringResource(R.plurals.n_song, it.size, it.size)
        },
        localDurationSummary,
        playlist?.playlist?.createdAt?.let {
            stringResource(R.string.playlist_added_date, formatPlaylistDate(it))
        },
    ).joinToString(" • ")
    val localShareLink = playlist?.playlist?.takeIf { it.isRemote }?.shareLink

    fun playFromPlaylist(song: Song, queue: List<Song>) {
        val connection = playerConnection
        if (connection == null) {
            onSongClick(song, queue)
            return
        }
        connection.playQueue(
            ListQueue(
                title = title,
                items = queue.map { it.toMediaItem() },
                startIndex = queue.indexOfFirst { it.song.id == song.song.id }.coerceAtLeast(0),
            )
        )
    }

    PlaylistScaffold(
        title = title,
        artistLine = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
        thumbnailUrl = thumbnailUrl,
        metadataLine = localMetadata,
        shareLink = localShareLink,
        onBack = onBack,
        onPlay = { songs.firstOrNull()?.let { playFromPlaylist(it, songs) } },
        onShuffle = {
            val shuffledSongs = songs.shuffled()
            shuffledSongs.firstOrNull()?.let { playFromPlaylist(it, shuffledSongs) }
        },
        isSaved = playlist?.playlist?.bookmarkedAt != null,
        isSaveEnabled = playlist != null,
        onSaveToggle = {
            playlist?.let { current ->
                playlistsViewModel.toggleSavedPlaylist(current) { success ->
                    if (!success) {
                        Toast.makeText(context, R.string.playlist_action_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        isLoading = false,
        error = null,
        onRetry = {},
    ) {
        itemsIndexed(playlistSongs, key = { _, it -> "playlist_song_${it.map.id}" }) { _, playlistSong ->
            val song = playlistSong.song
            val isCurrent = currentSongId == song.song.id
            PlaylistSongRow(
                title = song.song.title,
                artist = song.artists.joinToString(", ") { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                duration = song.song.duration,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                onClick = { playFromPlaylist(song, songs) },
                onPlay = {
                    if (isCurrent && playerConnection != null) {
                        playerConnection.togglePlayPause()
                    } else {
                        playFromPlaylist(song, songs)
                    }
                },
                onMoreClick = {
                    menuContext = song.toSongActionContext(
                        playlistId = viewModel.playlistId,
                        playlistEntryId = playlistSong.map.id,
                    )
                },
            )
        }
    }

    UniversalSongActionsHost(
        context = menuContext,
        onDismiss = { menuContext = null },
    )
}
@Composable
fun OnlinePlaylistScreen(
    onBack: () -> Unit,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val cachedPlaylist by viewModel.dbPlaylist.collectAsState()
    val currentSongIdFlow = remember(playerConnection) {
        playerConnection?.mediaMetadata?.map { it?.id } ?: flowOf(null)
    }
    val isPlayingFlow = remember(playerConnection) {
        playerConnection?.isEffectivelyPlaying ?: flowOf(false)
    }
    val currentSongId by currentSongIdFlow.collectAsState(initial = null)
    val isPlaying by isPlayingFlow.collectAsState(initial = false)
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }

    val hasContinuation = viewModel.continuation != null
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(hasContinuation, isLoadingMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            listState.isScrollInProgress &&
                lastVisible >= total - 5 &&
                !isLoadingMore &&
                hasContinuation
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreSongs()
    }
    val remoteComplete = !isLoading && !isLoadingMore && !hasContinuation
    val remoteDurations = songs.mapNotNull { it.duration?.takeIf { duration -> duration > 0 } }
    val remoteDurationSummary = remoteDurations
        .takeIf { remoteComplete && it.size == songs.size && it.isNotEmpty() }
        ?.sum()
        ?.let { formatPlaylistDuration(it) }
    val reliableRemoteCount = when {
        remoteComplete -> songs.size
        cachedPlaylist?.playlist?.remoteSongCount != null -> cachedPlaylist?.playlist?.remoteSongCount
        else -> null
    }
    val remoteMetadata = listOfNotNull(
        reliableRemoteCount?.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.n_song, it, it)
        },
        remoteDurationSummary,
        cachedPlaylist?.playlist?.createdAt?.let {
            stringResource(R.string.playlist_saved_date, formatPlaylistDate(it))
        },
    ).joinToString(" • ")
    val remoteShareLink = playlist?.shareLink?.takeIf(String::isNotBlank)
        ?: cachedPlaylist?.playlist?.shareLink

    PlaylistScaffold(
        title = playlist?.title ?: cachedPlaylist?.playlist?.name.orEmpty(),
        artistLine = playlist?.author?.name ?: "",
        thumbnailUrl = playlist?.thumbnail ?: cachedPlaylist?.thumbnails?.firstOrNull(),
        description = playlist?.description,
        metadataLine = remoteMetadata,
        shareLink = remoteShareLink,
        onBack = onBack,
        onPlay = {
            playerConnection?.let { connection ->
                val endpoint = playlist?.playEndpoint ?: cachedPlaylist?.playlist?.playEndpoint
                if (endpoint != null) {
                    connection.playQueue(YouTubeQueue(endpoint))
                } else if (songs.isNotEmpty()) {
                    connection.playQueue(
                        ListQueue(
                            title = playlist?.title ?: cachedPlaylist?.playlist?.name,
                            items = songs.map { it.toMediaItem() },
                        )
                    )
                }
            }
        },
        onShuffle = {
            playerConnection?.let { connection ->
                val endpoint = (playlist?.shuffleEndpoint ?: cachedPlaylist?.playlist?.shuffleEndpoint)
                    ?.takeIf { !it.params.isNullOrBlank() }
                if (endpoint != null) {
                    connection.playQueue(YouTubeQueue(endpoint))
                } else if (songs.isNotEmpty()) {
                    connection.playQueue(
                        ListQueue(
                            title = playlist?.title ?: cachedPlaylist?.playlist?.name,
                            items = songs.shuffled().map { it.toMediaItem() },
                        )
                    )
                }
            }
        },
        isSaved = cachedPlaylist?.playlist?.bookmarkedAt != null,
        isSaveEnabled = playlist != null || cachedPlaylist != null,
        onSaveToggle = {
            val remote = playlist
            val cached = cachedPlaylist
            when {
                remote != null -> playlistsViewModel.toggleSavedOnlinePlaylist(remote, cached) { success ->
                    if (!success) {
                        Toast.makeText(context, R.string.playlist_action_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                cached != null -> playlistsViewModel.toggleSavedPlaylist(cached) { success ->
                    if (!success) {
                        Toast.makeText(context, R.string.playlist_action_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        isLoading = isLoading,
        error = error,
        onRetry = { viewModel.retry() },
        listState = listState,
    ) {
        if (songs.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.playlist_is_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }

        itemsIndexed(songs, key = { index, it -> "${index}_${it.id}" }) { _, song ->
            val isCurrent = currentSongId == song.id
            PlaylistSongRow(
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name },
                thumbnailUrl = song.thumbnail,
                duration = song.duration,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                onClick = { playerConnection?.playYTItem(song) },
                onPlay = {
                    if (isCurrent && playerConnection != null) {
                        playerConnection.togglePlayPause()
                    } else {
                        playerConnection?.playYTItem(song)
                    }
                },
                onMoreClick = {
                    menuContext = song.toSongActionContext(
                        playlistId = cachedPlaylist
                            ?.takeIf { it.playlist.isEditable }
                            ?.playlist
                            ?.id,
                    )
                },
            )
        }

        if (isLoadingMore) {
            item {
                FridaLoadingIndicator(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    indicatorSize = FridaLoadingDefaults.SmallIndicatorSize,
                )
            }
        }

        if (relatedItems.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.related),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            items(relatedItems, key = { "related_${it.id}" }) { item ->
                val songItem = item as? SongItem ?: return@items
                val isCurrent = currentSongId == songItem.id
                PlaylistSongRow(
                    title = songItem.title,
                    artist = songItem.artists.joinToString(", ") { it.name },
                    thumbnailUrl = songItem.thumbnail,
                    duration = songItem.duration,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    onClick = { playerConnection?.playYTItem(songItem) },
                    onPlay = {
                        if (isCurrent && playerConnection != null) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection?.playYTItem(songItem)
                        }
                    },
                    onMoreClick = { menuContext = songItem.toSongActionContext() },
                )
            }
        }
    }

    UniversalSongActionsHost(
        context = menuContext,
        onDismiss = { menuContext = null },
    )
}

@Composable
private fun PlaylistScaffold(
    title: String,
    artistLine: String,
    thumbnailUrl: String?,
    description: String? = null,
    metadataLine: String = "",
    shareLink: String? = null,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isSaved: Boolean,
    isSaveEnabled: Boolean,
    onSaveToggle: () -> Unit,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current

    fun copyShareLink() {
        val link = shareLink ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(title, link))
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    fun sharePlaylist() {
        val link = shareLink ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl.resize(width = 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(60.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    )
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!shareLink.isNullOrBlank()) {
                            IconButton(onClick = ::copyShareLink) {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = stringResource(R.string.copy_link),
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = ::sharePlaylist) {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.share),
                                    tint = Color.White,
                                )
                            }
                        }
                        AnimatedPlaylistSaveButton(
                            isSaved = isSaved,
                            enabled = isSaveEnabled,
                            onClick = onSaveToggle,
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl.resize(width = 500),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    if (artistLine.isNotEmpty()) {
                        Text(
                            text = artistLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (!description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.play))
                        }
                        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.shuffle))
                        }
                    }
                    if (metadataLine.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = metadataLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    FridaLoadingIndicator(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        indicatorSize = FridaLoadingDefaults.LargeIndicatorSize,
                    )
                }
                return@LazyColumn
            }

            if (error != null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.error_loading_playlist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
                return@LazyColumn
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            content()
        }
    }
}

@Composable
private fun formatPlaylistDuration(seconds: Int): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, totalMinutes)
    }
}

private fun formatPlaylistDate(date: java.time.LocalDateTime): String =
    date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()),
    )

@Composable
private fun PlaylistSongRow(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    duration: Int?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        UniversalSongRow(
            title = title,
            subtitle = artist,
            duration = duration
                ?.takeIf { it >= 0 }
                ?.let { "%d:%02d".format(it / 60, it % 60) },
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            onClick = onClick,
            onPlay = onPlay,
            onMoreClick = onMoreClick,
            contentPadding = PaddingValues(10.dp),
            titleFontWeight = FontWeight.SemiBold,
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl.resize(width = 112),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

private enum class PlaylistSaveIconState {
    EMPTY,
    SAVED,
    BROKEN,
}

@Composable
private fun AnimatedPlaylistSaveButton(
    isSaved: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var previousSaved by remember { mutableStateOf<Boolean?>(null) }
    var visualState by remember {
        mutableStateOf(if (isSaved) PlaylistSaveIconState.SAVED else PlaylistSaveIconState.EMPTY)
    }
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isSaved, enabled) {
        if (!enabled) return@LaunchedEffect
        val previous = previousSaved
        previousSaved = isSaved
        if (previous == null) {
            visualState = if (isSaved) PlaylistSaveIconState.SAVED else PlaylistSaveIconState.EMPTY
            return@LaunchedEffect
        }
        if (previous == isSaved) return@LaunchedEffect

        if (isSaved) {
            visualState = PlaylistSaveIconState.SAVED
            scale.snapTo(0.65f)
            rotation.snapTo(-6f)
            coroutineScope {
                launch {
                    scale.animateTo(
                        targetValue = 1.22f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                    scale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                }
                launch {
                    rotation.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessLow))
                }
            }
        } else {
            visualState = PlaylistSaveIconState.BROKEN
            scale.snapTo(0.78f)
            rotation.snapTo(-10f)
            coroutineScope {
                launch {
                    scale.animateTo(
                        targetValue = 1.12f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
                launch {
                    rotation.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 360
                            -10f at 0
                            12f at 90
                            -9f at 180
                            7f at 270
                            0f at 360
                        },
                    )
                }
            }
            delay(450)
            visualState = PlaylistSaveIconState.EMPTY
            scale.snapTo(0.82f)
            scale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
        }
    }

    val actionDescription = stringResource(
        if (isSaved) R.string.remove_from_library_label else R.string.save,
    )
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = actionDescription },
    ) {
        Icon(
            imageVector = when (visualState) {
                PlaylistSaveIconState.EMPTY -> Icons.Rounded.FavoriteBorder
                PlaylistSaveIconState.SAVED -> Icons.Rounded.Favorite
                PlaylistSaveIconState.BROKEN -> Icons.Rounded.HeartBroken
            },
            contentDescription = null,
            tint = when (visualState) {
                PlaylistSaveIconState.EMPTY -> Color.White
                PlaylistSaveIconState.SAVED -> MaterialTheme.colorScheme.primary
                PlaylistSaveIconState.BROKEN -> MaterialTheme.colorScheme.error
            },
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
                alpha = if (enabled) 1f else 0.45f
            }.size(28.dp),
        )
    }
}
