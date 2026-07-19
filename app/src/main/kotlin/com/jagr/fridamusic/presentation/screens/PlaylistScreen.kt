package com.jagr.fridamusic.presentation.screens

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
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.playYTItem
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.LocalPlaylistViewModel
import com.jagr.fridamusic.viewmodels.OnlinePlaylistViewModel
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem


@Composable
fun LocalPlaylistScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onBack: () -> Unit,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val playlist by viewModel.playlist.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val songs = playlistSongs.map { it.song }

    val thumbnailUrl = playlist?.thumbnails?.firstOrNull()
    val title = playlist?.playlist?.name ?: ""

    PlaylistScaffold(
        title = title,
        artistLine = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
        thumbnailUrl = thumbnailUrl,
        onBack = onBack,
        onPlay = { songs.firstOrNull()?.let { onSongClick(it, songs) } },
        onShuffle = { songs.shuffled().firstOrNull()?.let { onSongClick(it, songs) } },
        isLoading = false,
        error = null,
        onRetry = {},
    ) {
        itemsIndexed(songs, key = { _, it -> it.song.id }) { index, song ->
            SongRow(
                index = index + 1,
                title = song.song.title,
                artist = song.artists.joinToString(", ") { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                duration = song.song.duration,
                onClick = { onSongClick(song, songs) },
            )
        }
    }
}


@Composable
fun OnlinePlaylistScreen(
    onBack: () -> Unit,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()

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
        title = playlist?.title ?: "",
        artistLine = playlist?.author?.name ?: "",
        thumbnailUrl = playlist?.thumbnail,
        onBack = onBack,
        onPlay = { songs.firstOrNull()?.let { playerConnection?.playYTItem(it) } },
        onShuffle = { songs.shuffled().firstOrNull()?.let { playerConnection?.playYTItem(it) } },
        isLoading = isLoading,
        error = error,
        onRetry = { viewModel.retry() },
        listState = listState,
    ) {
        itemsIndexed(songs, key = { _, it -> it.id }) { index, song ->
            SongRow(
                index = index + 1,
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name },
                thumbnailUrl = song.thumbnail,
                duration = null,
                onClick = { playerConnection?.playYTItem(song) },
            )
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
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
                SongRow(
                    index = null,
                    title = songItem.title,
                    artist = songItem.artists.joinToString(", ") { it.name },
                    thumbnailUrl = songItem.thumbnail,
                    duration = null,
                    onClick = { playerConnection?.playYTItem(songItem) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistScaffold(
    title: String,
    artistLine: String,
    thumbnailUrl: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
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
                    .fillMaxWidth()
                    .height(340.dp)
                    .blur(60.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
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
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.volver),
                            tint = MaterialTheme.colorScheme.onBackground,
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.reproducir))
                        }
                        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.aleatorio))
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
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
private fun SongRow(
    index: Int?,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    duration: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (index != null) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center,
            )
        } else if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl.resize(width = 96),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
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

        if (duration != null) {
            Text(
                text = "%d:%02d".format(duration / 60, duration % 60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}