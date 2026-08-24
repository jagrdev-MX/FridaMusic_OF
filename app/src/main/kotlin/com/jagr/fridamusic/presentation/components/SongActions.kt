package com.jagr.fridamusic.presentation.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.utils.resize
import com.music.innertube.models.SongItem

data class SongMenuActions(
    val onPlay: (() -> Unit)? = null,
    val onToggleFavorite: (() -> Unit)? = null,
    val onToggleLibrary: (() -> Unit)? = null,
    val onPlayNext: (() -> Unit)? = null,
    val onAddToQueue: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null,
    val onTogglePinned: (() -> Unit)? = null,
    val onGoToAlbum: (() -> Unit)? = null,
    val onGoToArtist: (() -> Unit)? = null,
    val onGoToAlbumArtist: (() -> Unit)? = null,
    val onGoToFolder: (() -> Unit)? = null,
    val onEditMetadata: (() -> Unit)? = null,
    val onEditLyrics: (() -> Unit)? = null,
    val onBlacklist: (() -> Unit)? = null,
    val onDetails: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
)

data class SongMenuPresentation(
    val title: String,
    val artists: String,
    val thumbnailUrl: String?,
    val album: String? = null,
    val isFavorite: Boolean = false,
    val isInLibrary: Boolean = false,
)

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
fun SongActionsSheet(
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    actions: SongMenuActions,
    presentation: SongMenuPresentation = mediaItem.toSongMenuPresentation(),
    isPinned: Boolean = false,
) {
    SongActionsSheetContent(
        presentation = presentation,
        onDismiss = onDismiss,
        actions = actions,
        isPinned = isPinned,
    )
}

@Composable
fun SongActionsSheet(
    song: Song,
    onDismiss: () -> Unit,
    actions: SongMenuActions,
    album: String? = null,
    isPinned: Boolean = false,
) {
    SongActionsSheet(
        mediaItem = song.toMediaItem(),
        onDismiss = onDismiss,
        actions = actions,
        presentation = SongMenuPresentation(
            title = song.song.title,
            artists = song.artists.joinToString(", ") { it.name },
            thumbnailUrl = song.thumbnailUrl,
            album = album ?: song.song.albumName ?: song.album?.title,
            isFavorite = song.song.liked,
            isInLibrary = song.song.inLibrary != null,
        ),
        isPinned = isPinned,
    )
}

@Composable
fun SongActionsSheet(
    song: SongItem,
    onDismiss: () -> Unit,
    actions: SongMenuActions,
) {
    SongActionsSheet(
        mediaItem = song.toMediaItem(),
        onDismiss = onDismiss,
        actions = actions,
        presentation = SongMenuPresentation(
            title = song.title,
            artists = song.artists.joinToString(", ") { it.name },
            thumbnailUrl = song.thumbnail,
            album = song.album?.name,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionsSheetContent(
    presentation: SongMenuPresentation,
    onDismiss: () -> Unit,
    actions: SongMenuActions,
    isPinned: Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasPlaybackActions = actions.onPlay != null || actions.onPlayNext != null ||
        actions.onAddToQueue != null || actions.onAddToPlaylist != null || actions.onTogglePinned != null
    val onToggleLibrary = actions.onToggleLibrary
    val hasNavigationActions = actions.onGoToAlbum != null || actions.onGoToArtist != null ||
        actions.onGoToAlbumArtist != null || actions.onGoToFolder != null
    val hasEditActions = actions.onEditMetadata != null || actions.onEditLyrics != null
    val hasOtherActions = actions.onBlacklist != null || actions.onDetails != null ||
        actions.onShare != null || actions.onDelete != null

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
                    model = presentation.thumbnailUrl?.resize(width = 112),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = presentation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(presentation.artists)
                            presentation.album?.takeIf(String::isNotBlank)?.let { album ->
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
                actions.onToggleFavorite?.let { onToggleFavorite ->
                    FilledTonalIconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (presentation.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(
                                if (presentation.isFavorite) R.string.local_song_remove_favorite else R.string.local_song_add_favorite,
                            ),
                            tint = if (presentation.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasPlaybackActions) item {
                    SongActionGroup {
                        actions.onPlay?.let { SongAction(Icons.Rounded.PlayArrow, stringResource(R.string.play), it) }
                        actions.onPlayNext?.let { SongAction(Icons.Rounded.SkipNext, stringResource(R.string.play_next), it) }
                        actions.onAddToQueue?.let { SongAction(Icons.Rounded.Queue, stringResource(R.string.add_to_queue), it) }
                        actions.onAddToPlaylist?.let {
                            SongAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist), it)
                        }
                        actions.onTogglePinned?.let {
                            SongAction(
                                Icons.Rounded.PushPin,
                                stringResource(if (isPinned) R.string.local_song_unpin else R.string.local_song_pin),
                                it,
                            )
                        }
                    }
                }
                if (onToggleLibrary != null) item {
                    SongActionGroup {
                        SongAction(
                            if (presentation.isInLibrary) Icons.Rounded.LibraryAddCheck else Icons.Rounded.LibraryAdd,
                            stringResource(
                                if (presentation.isInLibrary) R.string.remove_from_library_label else R.string.add_to_library_label,
                            ),
                            onToggleLibrary,
                        )
                    }
                }
                if (hasNavigationActions) item {
                    SongActionGroup {
                        actions.onGoToAlbum?.let { SongAction(Icons.Rounded.Album, stringResource(R.string.local_song_go_to_album), it) }
                        actions.onGoToArtist?.let { SongAction(Icons.Rounded.Person, stringResource(R.string.local_song_go_to_artist), it) }
                        actions.onGoToAlbumArtist?.let {
                            SongAction(Icons.Rounded.SupervisorAccount, stringResource(R.string.local_song_go_to_album_artist), it)
                        }
                        actions.onGoToFolder?.let { SongAction(Icons.Rounded.FolderOpen, stringResource(R.string.local_song_go_to_folder), it) }
                    }
                }
                if (hasEditActions) item {
                    SongActionGroup {
                        actions.onEditMetadata?.let { SongAction(Icons.Rounded.Edit, stringResource(R.string.local_song_edit_information), it) }
                        actions.onEditLyrics?.let { SongAction(Icons.Rounded.Lyrics, stringResource(R.string.edit_lyrics), it) }
                    }
                }
                if (hasOtherActions) item {
                    SongActionGroup {
                        actions.onBlacklist?.let { SongAction(Icons.Rounded.Block, stringResource(R.string.local_song_blacklist), it) }
                        actions.onDetails?.let { SongAction(Icons.Rounded.Info, stringResource(R.string.details), it) }
                        actions.onShare?.let { SongAction(Icons.Rounded.Share, stringResource(R.string.share), it) }
                        actions.onDelete?.let {
                            SongAction(
                                Icons.Rounded.Delete,
                                stringResource(R.string.local_song_delete_from_device),
                                it,
                                destructive = true,
                            )
                        }
                    }
                }
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
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

fun MediaItem.toSongMenuPresentation(): SongMenuPresentation {
    val taggedMetadata = metadata
    val platformMetadata = mediaMetadata
    return SongMenuPresentation(
        title = taggedMetadata?.title
            ?: platformMetadata.displayTitle?.toString()
            ?: platformMetadata.title?.toString()
            ?: mediaId,
        artists = taggedMetadata?.artists?.joinToString(", ") { it.name }
            ?: platformMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = taggedMetadata?.thumbnailUrl ?: platformMetadata.artworkUri?.toString(),
        album = taggedMetadata?.album?.title ?: platformMetadata.albumTitle?.toString(),
        isFavorite = taggedMetadata?.liked == true,
        isInLibrary = taggedMetadata?.inLibrary != null,
    )
}
