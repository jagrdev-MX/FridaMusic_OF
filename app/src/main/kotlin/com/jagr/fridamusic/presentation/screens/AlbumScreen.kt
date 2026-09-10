package com.jagr.fridamusic.presentation.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.AnimatedLibraryHeartButton
import com.jagr.fridamusic.presentation.components.FridaLoadingDefaults
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.SongNavigationTarget
import com.jagr.fridamusic.presentation.components.UniversalSongRow
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.presentation.components.YTContentCard
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.playback.queues.YouTubeAlbumRadio
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.AlbumViewModel
import com.jagr.fridamusic.R
import com.music.innertube.models.AlbumItem
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val currentSongIdFlow = remember(playerConnection) {
        playerConnection?.mediaMetadata?.map { it?.id } ?: flowOf(null)
    }
    val currentAlbumIdFlow = remember(playerConnection) {
        playerConnection?.mediaMetadata?.map { it?.album?.id } ?: flowOf(null)
    }
    val isPlayingFlow = remember(playerConnection) {
        playerConnection?.isEffectivelyPlaying ?: flowOf(false)
    }
    val currentSongId by currentSongIdFlow.collectAsState(initial = null)
    val currentAlbumId by currentAlbumIdFlow.collectAsState(initial = null)
    val isPlaying by isPlayingFlow.collectAsState(initial = false)
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isBookmarkUpdating by viewModel.isBookmarkUpdating.collectAsState()
    val description by viewModel.description.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val releasesForYou by viewModel.releasesForYou.collectAsState()

    val album = albumWithSongs

    if (album == null || (isLoading && album.songs.isEmpty())) {
        FridaLoadingIndicator(
            modifier = Modifier.fillMaxSize(),
            indicatorSize = FridaLoadingDefaults.LargeIndicatorSize,
        )
        return
    }

    val songs = album.songs
    val thumbnailUrl = album.album.thumbnailUrl
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }
    var remoteMenuAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    val albumArtists = remember(album.artists) {
        album.artists.map { SongNavigationTarget(name = it.name, id = it.id) }
    }
    val albumLink = album.album.playlistId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { "https://share.echomusic.fun/playlist?list=$it" }
    val knownDurations = songs.mapNotNull { it.song.duration.takeIf { duration -> duration > 0 } }
    val totalDuration = knownDurations.takeIf { it.size == songs.size && it.isNotEmpty() }?.sum()
    val durationSummary = totalDuration?.let { formatDurationSummary(it) }
    val summary = listOfNotNull(
        album.album.year?.toString(),
        songs.takeIf { it.isNotEmpty() }?.let {
            pluralStringResource(R.plurals.n_song, it.size, it.size)
        },
        durationSummary,
    ).joinToString(" • ")

    fun copyAlbumLink() {
        val link = albumLink ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(album.album.title, link))
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    fun shareAlbumLink() {
        val link = albumLink ?: return
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp),
        ) {

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (albumLink != null) {
                        IconButton(onClick = ::copyAlbumLink) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = stringResource(R.string.copy_link),
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = ::shareAlbumLink) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.share),
                                tint = Color.White,
                            )
                        }
                    }
                    AnimatedLibraryHeartButton(
                        isSaved = album.album.bookmarkedAt != null,
                        enabled = !isBookmarkUpdating,
                        contentDescription = stringResource(
                            if (album.album.bookmarkedAt != null) {
                                R.string.remove_album_from_library
                            } else {
                                R.string.add_album_to_library
                            }
                        ),
                        onClick = viewModel::toggleBookmark,
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
                    AsyncImage(
                        model = thumbnailUrl?.resize(width = 600),
                        contentDescription = album.album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = album.album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        album.artists.forEach { artist ->
                            if (artist.id.isNotBlank()) {
                                AssistChip(
                                    onClick = { onArtistClick(artist.id) },
                                    label = { Text(artist.name, maxLines = 1) },
                                )
                            } else {
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (songs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { songs.firstOrNull()?.let { onSongClick(it, songs) } },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.play))
                            }
                            OutlinedButton(
                                onClick = {
                                    songs.shuffled().firstOrNull()?.let { onSongClick(it, songs) }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.shuffle))
                            }
                        }
                    }
                }
            }


            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            itemsIndexed(songs, key = { _, it -> it.song.id }) { index, song ->
                val isCurrent = currentSongId == song.song.id
                val openMenu = {
                    menuContext = song.toSongActionContext(albumArtists = albumArtists)
                }
                UniversalSongRow(
                    title = song.song.title,
                    subtitle = song.artists.joinToString(", ") { it.name },
                    duration = song.song.duration
                        .takeIf { it >= 0 }
                        ?.let(::formatDuration),
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song, songs) },
                    onPlay = {
                        if (isCurrent && playerConnection != null) {
                            playerConnection.togglePlayPause()
                        } else {
                            onSongClick(song, songs)
                        }
                    },
                    onMoreClick = openMenu,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalSpacing = 14.dp,
                    leadingContent = {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }


            if (!description.isNullOrBlank()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = stringResource(R.string.about_album),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    Text(
                        text = description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }


            if (otherVersions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.other_versions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(otherVersions, key = { "other_${it.browseId}" }) { albumItem ->
                            YTContentCard(
                                item = albumItem,
                                currentMediaId = currentSongId,
                                currentAlbumId = currentAlbumId,
                                currentQueueTitle = null,
                                isPlaying = isPlaying,
                                onClick = { onAlbumClick(albumItem) },
                                onPlay = albumItem.playlistId.takeIf(String::isNotBlank)?.let { playlistId ->
                                    playerConnection?.let { connection ->
                                        { connection.playQueue(YouTubeAlbumRadio(playlistId)) }
                                    }
                                },
                                onMore = { remoteMenuAlbum = albumItem },
                                thumbnailWidth = 300,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }


            if (releasesForYou.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.releases_for_you),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(releasesForYou, key = { "release_${it.browseId}" }) { albumItem ->
                            YTContentCard(
                                item = albumItem,
                                currentMediaId = currentSongId,
                                currentAlbumId = currentAlbumId,
                                currentQueueTitle = null,
                                isPlaying = isPlaying,
                                onClick = { onAlbumClick(albumItem) },
                                onPlay = albumItem.playlistId.takeIf(String::isNotBlank)?.let { playlistId ->
                                    playerConnection?.let { connection ->
                                        { connection.playQueue(YouTubeAlbumRadio(playlistId)) }
                                    }
                                },
                                onMore = { remoteMenuAlbum = albumItem },
                                thumbnailWidth = 300,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        UniversalSongActionsHost(
            context = menuContext,
            onDismiss = { menuContext = null },
        )
        UniversalYTItemActionsHost(
            item = remoteMenuAlbum,
            onDismiss = { remoteMenuAlbum = null },
            onOpen = { remoteMenuAlbum?.let(onAlbumClick) },
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun formatDurationSummary(seconds: Int): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, totalMinutes)
    }
}
