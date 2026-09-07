package com.jagr.fridamusic.presentation.components

import androidx.compose.ui.platform.LocalContext
import com.jagr.fridamusic.utils.songCountText
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.PlaylistSortType
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.utils.resize

@Composable
fun PlaylistLibraryControls(
    sortLabel: String,
    descending: Boolean,
    gridView: Boolean,
    onSortClick: () -> Unit,
    onGridViewChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onSortClick,
            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = sortLabel.uppercase(locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Icon(
                    imageVector = if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        LibraryViewModeToggle(
            gridView = gridView,
            onGridViewChanged = onGridViewChanged,
        )
    }
}

@Composable
fun LibraryViewModeToggle(
    gridView: Boolean,
    onGridViewChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            PlaylistViewModeButton(
                selected = !gridView,
                icon = Icons.AutoMirrored.Rounded.ViewList,
                contentDescription = stringResource(R.string.playlist_view_list),
                onClick = { onGridViewChanged(false) },
            )
            PlaylistViewModeButton(
                selected = gridView,
                icon = Icons.Rounded.GridView,
                contentDescription = stringResource(R.string.playlist_view_grid),
                onClick = { onGridViewChanged(true) },
            )
        }
    }
}

@Composable
private fun PlaylistViewModeButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "playlistViewMode",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(12.dp).size(22.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistLibraryListItem(
    playlist: Playlist,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    selected: Boolean = false,
    selectionMode: Boolean = false,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "playlistListContainer",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaylistArtwork(
                playlist = playlist,
                modifier = Modifier.size(62.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = playlist.playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                playlist.songCountText(LocalContext.current.resources)?.let { countText ->
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(25.dp),
                )
            } else if (isCurrent) {
                MediaPlaybackIndicator(
                    isPlaying = isPlaying,
                    onClick = onPlay,
                    buttonSize = 40.dp,
                    indicatorSize = 23.dp,
                )
            } else {
                MediaArtworkActionButton(
                    icon = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    onClick = onPlay,
                    buttonSize = 40.dp,
                    iconSize = 23.dp,
                )
            }
            SongOptionsButton(
                onClick = onMoreClick,
                iconColor = Color.White,
                containerColor = Color.Black.copy(alpha = 0.38f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistLibraryGridItem(
    playlist: Playlist,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    selected: Boolean = false,
    selectionMode: Boolean = false,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "playlistGridContainer",
    )
    Surface(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                PlaylistArtwork(
                    playlist = playlist,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!selectionMode) {
                    SongOptionsButton(
                        onClick = onMoreClick,
                        modifier = Modifier.align(Alignment.TopEnd),
                        iconColor = Color.White,
                        containerColor = Color.Black.copy(alpha = 0.38f),
                    )
                }
                if (selectionMode) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(9.dp).size(24.dp),
                        )
                    }
                } else if (isCurrent) {
                    MediaPlaybackIndicator(
                        isPlaying = isPlaying,
                        onClick = onPlay,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    )
                } else {
                    MediaArtworkActionButton(
                        icon = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        onClick = onPlay,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            MarqueeText(
                text = playlist.playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            playlist.songCountText(LocalContext.current.resources)?.let { countText ->
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlaylistArtwork(playlist: Playlist, modifier: Modifier = Modifier) {
    val thumbnail = playlist.thumbnails.firstOrNull()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {}
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail.resize(width = 420),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistLibrarySortSheet(
    selectedSort: PlaylistSortType,
    descending: Boolean,
    onSortSelected: (PlaylistSortType) -> Unit,
    onDescendingChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.playlist_section_title).uppercase(locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.playlist_sort_by),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PlaylistSortDirectionOption(
                        selected = !descending,
                        icon = Icons.Rounded.ArrowUpward,
                        label = stringResource(R.string.sort_ascending_short),
                        onClick = { onDescendingChanged(false) },
                    )
                    PlaylistSortDirectionOption(
                        selected = descending,
                        icon = Icons.Rounded.ArrowDownward,
                        label = stringResource(R.string.sort_descending_short),
                        onClick = { onDescendingChanged(true) },
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                PlaylistSortType.entries.forEach { sortType ->
                    val selected = selectedSort == sortType
                    Surface(
                        onClick = { onSortSelected(sortType) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    ) {
                        Text(
                            text = stringResource(sortType.labelRes()),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSortDirectionOption(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistLibraryActionsSheet(
    playlist: Playlist,
    canEditMetadata: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onSelect: (() -> Unit)?,
    onTogglePinned: () -> Unit,
    onChooseCover: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlaylistArtwork(playlist = playlist, modifier = Modifier.size(64.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    playlist.songCountText(LocalContext.current.resources)?.let { countText ->
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            PlaylistActionGroup {
                PlaylistActionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.play_next), onPlayNext)
                PlaylistActionRow(Icons.Rounded.PlayArrow, stringResource(R.string.play), onPlay)
                PlaylistActionRow(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.add_to_queue), onAddToQueue)
                PlaylistActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist), onAddToPlaylist)
                PlaylistActionRow(Icons.Rounded.CheckCircle, stringResource(R.string.select_item), onSelect)
                PlaylistActionRow(
                    Icons.Rounded.PushPin,
                    stringResource(if (playlist.playlist.isPinned) R.string.local_song_unpin else R.string.local_song_pin),
                    onTogglePinned,
                )
            }

            if (onChooseCover != null || onRename != null) {
                PlaylistActionGroup {
                    PlaylistActionRow(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.playlist_choose_cover), onChooseCover)
                    PlaylistActionRow(Icons.Rounded.Edit, stringResource(R.string.playlist_rename), onRename)
                }
            }

            PlaylistActionGroup {
                PlaylistActionRow(Icons.Rounded.SaveAlt, stringResource(R.string.playlist_save_file), onExport)
                PlaylistActionRow(
                    Icons.Rounded.Delete,
                    stringResource(R.string.delete),
                    onDelete,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun PlaylistActionGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(content = content)
    }
}

@Composable
private fun PlaylistActionRow(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    destructive: Boolean = false,
) {
    val enabled = onClick != null
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp), tint = color)
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_playlist_confirm, playlistName)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun EmptyPlaylistsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            modifier = Modifier.size(46.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_playlist_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun PlaylistSortType.labelRes(): Int = when (this) {
    PlaylistSortType.NAME -> R.string.sort_by_name
    PlaylistSortType.SONG_COUNT -> R.string.playlist_sort_song_count
    PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
    PlaylistSortType.CREATE_DATE -> R.string.playlist_sort_date_added
}
