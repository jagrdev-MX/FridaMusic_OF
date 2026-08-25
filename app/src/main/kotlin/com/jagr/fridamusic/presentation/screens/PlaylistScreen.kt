package com.jagr.fridamusic.presentation.screens

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
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.FridaLoadingDefaults
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.LocalPlayingBars
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.toSongActionContext
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
        onBack = onBack,
        onPlay = { songs.firstOrNull()?.let { playFromPlaylist(it, songs) } },
        onShuffle = { songs.shuffled().firstOrNull()?.let { playFromPlaylist(it, songs) } },
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
        itemsIndexed(songs, key = { index, it -> "${index}_${it.song.id}" }) { _, song ->
            PlaylistSongRow(
                title = song.song.title,
                artist = song.artists.joinToString(", ") { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                duration = song.song.duration,
                isCurrent = currentSongId == song.song.id,
                isPlaying = isPlaying,
                onClick = { playFromPlaylist(song, songs) },
                onMoreClick = { menuContext = song.toSongActionContext() },
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

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 5 && !isLoadingMore && hasContinuation
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreSongs()
    }

    PlaylistScaffold(
        title = playlist?.title ?: cachedPlaylist?.playlist?.name.orEmpty(),
        artistLine = playlist?.author?.name ?: "",
        thumbnailUrl = playlist?.thumbnail ?: cachedPlaylist?.thumbnails?.firstOrNull(),
        description = playlist?.description,
        onBack = onBack,
        onPlay = { songs.firstOrNull()?.let { playerConnection?.playYTItem(it) } },
        onShuffle = { songs.shuffled().firstOrNull()?.let { playerConnection?.playYTItem(it) } },
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
            PlaylistSongRow(
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name },
                thumbnailUrl = song.thumbnail,
                duration = song.duration,
                isCurrent = currentSongId == song.id,
                isPlaying = isPlaying,
                onClick = { playerConnection?.playYTItem(song) },
                onMoreClick = { menuContext = song.toSongActionContext() },
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
                PlaylistSongRow(
                    title = songItem.title,
                    artist = songItem.artists.joinToString(", ") { it.name },
                    thumbnailUrl = songItem.thumbnail,
                    duration = songItem.duration,
                    isCurrent = currentSongId == songItem.id,
                    isPlaying = isPlaying,
                    onClick = { playerConnection?.playYTItem(songItem) },
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
                    AnimatedPlaylistSaveButton(
                        isSaved = isSaved,
                        enabled = isSaveEnabled,
                        onClick = onSaveToggle,
                    )
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
private fun PlaylistSongRow(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    duration: Int?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.isNotEmpty()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isCurrent) {
                LocalPlayingBars(
                    active = isPlaying,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(25.dp),
                )
            } else if (duration != null && duration >= 0) {
                Text(
                    text = "%d:%02d".format(duration / 60, duration % 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SongOptionsButton(onClick = onMoreClick)
        }
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
        onClick = onClick,
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
                PlaylistSaveIconState.EMPTY -> MaterialTheme.colorScheme.onBackground
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
