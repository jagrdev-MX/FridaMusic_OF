package com.jagr.fridamusic.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.utils.resize

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(
    playerConnection: PlayerConnection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val playbackPositionMs by playerConnection.playbackPositionMs.collectAsState()
    val playbackDurationMs by playerConnection.playbackDurationMs.collectAsState()
    val song = mediaMetadata ?: return
    val progress = if (playbackDurationMs != C.TIME_UNSET && playbackDurationMs > 0L) {
        (playbackPositionMs.toFloat() / playbackDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(64.dp)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularArtwork(
            imageUrl = song.thumbnailUrl?.resize(width = 96),
            contentDescription = song.title,
            progress = progress,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            modifier = Modifier.size(56.dp)
        )

        Column(modifier = Modifier.weight(1f).clipToBounds()) {
            MarqueeText(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MarqueeText(
                text = song.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = { playerConnection.seekToPrevious() },
                enabled = canSkipPrevious,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous),
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = { playerConnection.togglePlayPause() },
                enabled = playbackState != Player.STATE_BUFFERING,
                modifier = Modifier.size(38.dp),
            ) {
                if (playbackState == Player.STATE_BUFFERING) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                } else {
                    Icon(
                        imageVector = when {
                            playbackState == Player.STATE_ENDED -> Icons.Rounded.Replay
                            isPlaying -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = when {
                            playbackState == Player.STATE_ENDED -> stringResource(R.string.replay)
                            isPlaying -> stringResource(R.string.pause)
                            else -> stringResource(R.string.play)
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(
                onClick = { playerConnection.seekToNext() },
                enabled = canSkipNext,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircularArtwork(
    imageUrl: String?,
    contentDescription: String?,
    progress: Float,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isBuffering) {
            CircularWavyProgressIndicator(
                modifier = Modifier.matchParentSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.matchParentSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
