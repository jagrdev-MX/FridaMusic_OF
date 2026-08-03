package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.localmedia.LocalSongSortMetadata
import com.jagr.fridamusic.utils.resize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalSongListItem(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "localSongContainer",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "localSongTitle",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onMoreClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = song.thumbnailUrl?.resize(width = 112),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                )
                Text(
                    text = buildString {
                        append(song.artists.joinToString(", ") { it.name })
                        song.song.duration.takeIf { it >= 0 }?.let { duration ->
                            if (isNotEmpty()) append("  ·  ")
                            append(formatLocalSongDuration(duration))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                LocalPlayingBars(
                    active = isPlaying,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(25.dp),
                )
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalPlayingBars(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "localPlayingBars")
    val first by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "localPlayingBar1",
    )
    val second by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(680), RepeatMode.Reverse),
        label = "localPlayingBar2",
    )
    val third by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(430), RepeatMode.Reverse),
        label = "localPlayingBar3",
    )
    val heights = if (active) listOf(first, second, third) else listOf(0.42f, 0.72f, 0.52f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSongActionsSheet(
    song: Song,
    metadata: LocalSongSortMetadata?,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTogglePinned: () -> Unit,
    onGoToAlbum: (() -> Unit)?,
    onGoToArtist: (() -> Unit)?,
    onGoToAlbumArtist: (() -> Unit)?,
    onGoToFolder: (() -> Unit)?,
    onEditMetadata: () -> Unit,
    onEditLyrics: () -> Unit,
    onBlacklist: () -> Unit,
    onDetails: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    model = song.thumbnailUrl?.resize(width = 112),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(song.artists.joinToString(", ") { it.name })
                            val album = metadata?.album ?: song.song.albumName
                            if (!album.isNullOrBlank()) {
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
                FilledTonalIconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (song.song.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(
                            if (song.song.liked) R.string.local_song_remove_favorite else R.string.local_song_add_favorite,
                        ),
                        tint = if (song.song.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LocalSongActionGroup {
                        LocalSongAction(Icons.Rounded.SkipNext, stringResource(R.string.play_next), onPlayNext)
                        LocalSongAction(Icons.Rounded.Queue, stringResource(R.string.add_to_queue), onAddToQueue)
                        LocalSongAction(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            stringResource(R.string.add_to_playlist),
                            onAddToPlaylist,
                        )
                        LocalSongAction(
                            Icons.Rounded.PushPin,
                            stringResource(if (isPinned) R.string.local_song_unpin else R.string.local_song_pin),
                            onTogglePinned,
                        )
                    }
                }
                item {
                    LocalSongActionGroup {
                        LocalSongAction(
                            Icons.Rounded.Album,
                            stringResource(R.string.local_song_go_to_album),
                            onGoToAlbum,
                        )
                        LocalSongAction(
                            Icons.Rounded.Person,
                            stringResource(R.string.local_song_go_to_artist),
                            onGoToArtist,
                        )
                        LocalSongAction(
                            Icons.Rounded.SupervisorAccount,
                            stringResource(R.string.local_song_go_to_album_artist),
                            onGoToAlbumArtist,
                        )
                        LocalSongAction(
                            Icons.Rounded.FolderOpen,
                            stringResource(R.string.local_song_go_to_folder),
                            onGoToFolder,
                        )
                    }
                }
                item {
                    LocalSongActionGroup {
                        LocalSongAction(Icons.Rounded.Edit, stringResource(R.string.local_song_edit_information), onEditMetadata)
                        LocalSongAction(Icons.Rounded.Lyrics, stringResource(R.string.edit_lyrics), onEditLyrics)
                    }
                }
                item {
                    LocalSongActionGroup {
                        LocalSongAction(Icons.Rounded.Block, stringResource(R.string.local_song_blacklist), onBlacklist)
                        LocalSongAction(Icons.Rounded.Info, stringResource(R.string.details), onDetails)
                        LocalSongAction(Icons.Rounded.Share, stringResource(R.string.share), onShare)
                        LocalSongAction(
                            Icons.Rounded.Delete,
                            stringResource(R.string.local_song_delete_from_device),
                            onDelete,
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalSongActionGroup(content: @Composable ColumnScope.() -> Unit) {
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
private fun LocalSongAction(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    destructive: Boolean = false,
) {
    val enabled = onClick != null
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = { onClick?.invoke() },
                onLongClick = {},
            )
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

@Composable
fun LocalPlaylistPickerDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            if (playlists.isEmpty()) {
                Text(stringResource(R.string.no_playlists))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(playlists, key = { it.playlist.id }) { playlist ->
                        Surface(
                            onClick = { onSelect(playlist) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                        ) {
                            Text(
                                text = playlist.playlist.name,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun LocalSongMetadataEditorDialog(
    song: Song,
    metadata: LocalSongSortMetadata?,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String) -> Unit,
) {
    var title by rememberSaveable(song.song.id) { mutableStateOf(metadata?.title ?: song.song.title) }
    var artist by rememberSaveable(song.song.id) {
        mutableStateOf(metadata?.artist ?: song.artists.joinToString(", ") { it.name })
    }
    var album by rememberSaveable(song.song.id) { mutableStateOf(metadata?.album ?: song.song.albumName.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_song_edit_information)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.local_song_title_field)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sort_by_artist)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sort_by_album)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), artist.trim(), album.trim()) },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun LocalSongLyricsEditorDialog(
    songId: String,
    initialLyrics: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var lyrics by rememberSaveable(songId) { mutableStateOf(initialLyrics) }
    var edited by rememberSaveable(songId) { mutableStateOf(false) }
    LaunchedEffect(initialLyrics) {
        if (!edited) lyrics = initialLyrics
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_lyrics)) },
        text = {
            OutlinedTextField(
                value = lyrics,
                onValueChange = {
                    lyrics = it
                    edited = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp),
                label = { Text(stringResource(R.string.lyrics)) },
                minLines = 8,
                maxLines = 16,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(lyrics) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun LocalSongDetailsDialog(
    song: Song,
    metadata: LocalSongSortMetadata?,
    locale: Locale,
    onDismiss: () -> Unit,
) {
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    }
    val zoneId = remember { ZoneId.systemDefault() }
    val details = listOfNotNull(
        stringResource(R.string.local_song_title_field) to song.song.title,
        stringResource(R.string.sort_by_artist) to song.artists.joinToString(", ") { it.name },
        metadata?.album?.takeIf(String::isNotBlank)?.let { stringResource(R.string.sort_by_album) to it },
        metadata?.albumArtist?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.sort_by_album_artist) to it
        },
        metadata?.genre?.takeIf(String::isNotBlank)?.let { stringResource(R.string.sort_by_genre) to it },
        metadata?.trackNumber?.let { stringResource(R.string.sort_by_track_number) to it.toString() },
        song.song.duration.takeIf { it >= 0 }?.let {
            stringResource(R.string.sort_by_length) to formatLocalSongDuration(it)
        },
        metadata?.year?.let { stringResource(R.string.sort_by_year) to it.toString() },
        metadata?.composer?.takeIf(String::isNotBlank)?.let { stringResource(R.string.sort_by_composer) to it },
        metadata?.displayName?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.local_song_file_name) to it
        },
        metadata?.relativePath?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.local_song_folder) to it
        },
        metadata?.mimeType?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.local_song_format) to it
        },
        metadata?.sizeBytes?.let {
            stringResource(R.string.local_song_file_size) to formatLocalFileSize(it)
        },
        metadata?.dateModifiedSeconds?.let {
            stringResource(R.string.sort_by_date_modified) to
                formatter.format(Instant.ofEpochSecond(it).atZone(zoneId).toLocalDateTime())
        },
        metadata?.dateAddedSeconds?.let {
            stringResource(R.string.sort_by_date_added) to
                formatter.format(Instant.ofEpochSecond(it).atZone(zoneId).toLocalDateTime())
        },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                details.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun DeleteLocalSongDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_song_delete_from_device)) },
        text = { Text(stringResource(R.string.local_song_delete_confirmation, title)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatLocalSongDuration(seconds: Int): String =
    "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)

private fun formatLocalFileSize(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) {
        String.format(Locale.ROOT, "%.1f MB", megabytes)
    } else {
        String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    }
}
