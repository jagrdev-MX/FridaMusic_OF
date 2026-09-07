package com.jagr.fridamusic.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.R
import androidx.compose.ui.res.stringResource

@Composable
fun UniversalSongRow(
    title: String,
    subtitle: String?,
    duration: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    horizontalSpacing: Dp = 12.dp,
    titleFontWeight: FontWeight? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .universalMediaClickable(onClick = onClick, onLongClick = onMoreClick)
            .clipToBounds()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
    ) {
        leadingContent?.invoke()

        Column(
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
        ) {
            MarqueeText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = titleFontWeight,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (duration != null) {
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 36.dp),
            )
        }

        if (isCurrent) {
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

        SongOptionsButton(onClick = onMoreClick)
    }
}
