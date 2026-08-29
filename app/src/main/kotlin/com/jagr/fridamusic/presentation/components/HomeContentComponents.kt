package com.jagr.fridamusic.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.utils.isLocalMediaId
import com.jagr.fridamusic.utils.resize
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

enum class HomeContentType {
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
}

@Composable
fun HomeSectionHeader(
    title: String,
    subtitle: String? = null,
    thumbnail: String? = null,
    circularThumbnail: Boolean = false,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clickModifier = if (onSeeAll != null) {
        Modifier.clickable(onClick = onSeeAll)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(if (circularThumbnail) CircleShape else RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onSeeAll != null) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.see_all_section, title),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
fun YTContentCard(
    item: YTItem,
    currentMediaId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onMore: ((YTItem) -> Unit)? = null,
    isLocal: Boolean = false,
    thumbnailWidth: Int = 480,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentQueueTitle = LocalPlayerConnection.current
        ?.queueTitle
        ?.collectAsState()
        ?.value
    val type = when (item) {
        is SongItem -> HomeContentType.SONG
        is AlbumItem -> HomeContentType.ALBUM
        is ArtistItem -> HomeContentType.ARTIST
        is PlaylistItem -> HomeContentType.PLAYLIST
    }
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name }
            ?: stringResource(R.string.album_text)
        is ArtistItem -> stringResource(R.string.artists)
        is PlaylistItem -> item.author?.name ?: stringResource(R.string.playlists)
    }
    val isActive = when (item) {
        is SongItem -> item.id == currentMediaId
        is AlbumItem -> item.browseId == currentAlbumId
        is PlaylistItem -> currentMediaId != null && item.title == currentQueueTitle
        is ArtistItem -> false
    }

    HomeMediaCard(
        type = type,
        title = item.title,
        subtitle = subtitle,
        imageUrl = item.thumbnail?.resize(width = thumbnailWidth).orEmpty(),
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onPlay = onPlay,
        onMoreClick = onMore?.let { showMenu -> { showMenu(item) } },
        isLocal = isLocal || item.id.isLocalMediaId(),
        fillMaxWidth = fillMaxWidth,
        modifier = modifier,
    )
}

@Composable
fun HomeMediaCard(
    type: HomeContentType,
    title: String,
    subtitle: String?,
    imageUrl: String,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    isLocal: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current
    val dedicatedAction = if (isActive && playerConnection != null) {
        playerConnection::togglePlayPause
    } else {
        onPlay
    }
    val cardModifier = if (fillMaxWidth) {
        modifier.fillMaxWidth()
    } else {
        modifier.width(if (type == HomeContentType.ARTIST) 116.dp else 144.dp)
    }
    val artworkShape: Shape = if (type == HomeContentType.ARTIST) {
        CircleShape
    } else {
        RoundedCornerShape(16.dp)
    }

    Column(
        modifier = cardModifier.universalMediaClickable(
            onClick = onClick,
            onLongClick = onMoreClick,
        ),
        horizontalAlignment = if (type == HomeContentType.ARTIST) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = if (isActive) 2.dp else 0.dp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = artworkShape,
                )
                .clip(artworkShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (onMoreClick != null && type != HomeContentType.ARTIST) {
                SongOptionsButton(
                    onClick = onMoreClick,
                    modifier = Modifier.align(Alignment.TopEnd),
                    iconColor = Color.White,
                    containerColor = Color.Black.copy(alpha = 0.38f),
                )
            }

            if (isLocal) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.filter_local),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            when (type) {
                HomeContentType.SONG -> {
                    if (isActive) {
                        MediaPlaybackIndicator(
                            isPlaying = isPlaying,
                            onClick = dedicatedAction,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else if (dedicatedAction != null) {
                        MediaArtworkActionButton(
                            icon = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            onClick = dedicatedAction,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                HomeContentType.ALBUM -> {
                    if (isActive) {
                        MediaPlaybackIndicator(
                            isPlaying = isPlaying,
                            onClick = dedicatedAction,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(7.dp),
                        )
                    } else if (dedicatedAction != null) {
                        MediaArtworkActionButton(
                            icon = Icons.Rounded.Album,
                            contentDescription = stringResource(R.string.play),
                            onClick = dedicatedAction,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(7.dp),
                        )
                    }
                }

                HomeContentType.PLAYLIST -> {
                    if (isActive) {
                        MediaPlaybackIndicator(
                            isPlaying = isPlaying,
                            onClick = dedicatedAction,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                        )
                    } else {
                        MediaArtworkActionButton(
                            icon = Icons.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.playlists),
                            onClick = dedicatedAction,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                        )
                    }
                }

                HomeContentType.ARTIST -> Unit
            }
        }

        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (type == HomeContentType.ARTIST) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (type == HomeContentType.ARTIST) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun MediaArtworkActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 26.dp,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.38f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
        modifier = modifier
            .size(buttonSize)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
fun MediaPlaybackIndicator(
    isPlaying: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 44.dp,
    indicatorSize: Dp = 25.dp,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.38f),
        contentColor = Color.White,
        shape = CircleShape,
        modifier = modifier
            .size(buttonSize)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LocalPlayingBars(
                active = isPlaying,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(indicatorSize),
            )
        }
    }
}
