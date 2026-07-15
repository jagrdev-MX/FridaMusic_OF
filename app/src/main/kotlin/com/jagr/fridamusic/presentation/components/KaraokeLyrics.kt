package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.lyrics.LyricsUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun KaraokeLyrics(
    lyrics: String,
    positionMs: Long,
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedLyrics = remember(lyrics) { LyricsUtils.parseLyrics(lyrics) }
    val currentLineIndex = remember(parsedLyrics, positionMs, offsetMs) {
        LyricsUtils.findCurrentLineIndex(parsedLyrics, positionMs + offsetMs)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var viewportHeightPx by remember { mutableIntStateOf(0) }

    val topPaddingDp: Dp = 16.dp
    val anchorFraction = 0.28f

    val scrollOffsetPx by remember(viewportHeightPx) {
        derivedStateOf {
            val topPx = with(density) { topPaddingDp.toPx() }.toInt()
            val usable = (viewportHeightPx - topPx).coerceAtLeast(0)
            -(topPx + (usable * anchorFraction).toInt())
        }
    }

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            scope.launch {
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = scrollOffsetPx,
                )
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.onSizeChanged { viewportHeightPx = it.height },
        contentPadding = PaddingValues(top = topPaddingDp, bottom = 300.dp),
        userScrollEnabled = true,
    ) {
        itemsIndexed(parsedLyrics) { index, entry ->
            val distance = index - currentLineIndex
            val targetAlpha = when {
                distance == 0  -> 1.00f
                distance ==  1 -> 0.55f
                distance ==  2 -> 0.38f
                distance ==  3 -> 0.26f
                distance  >  3 -> 0.16f
                distance == -1 -> 0.38f
                distance == -2 -> 0.22f
                else           -> 0.12f
            }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 380),
                label = "lyric_alpha_$index",
            )

            val targetFontSize = when {
                distance == 0  -> 30f
                abs(distance) == 1 -> 24f
                abs(distance) == 2 -> 22f
                else               -> 20f
            }
            val animatedFontSize by animateFloatAsState(
                targetValue = targetFontSize,
                animationSpec = tween(durationMillis = 350),
                label = "lyric_size_$index",
            )

            val fontWeight = if (distance == 0) FontWeight.ExtraBold else FontWeight.Medium

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSeekTo(entry.time) }
                    .padding(vertical = 8.dp)
                    .alpha(alpha),
            ) {
                Text(
                    text = entry.text,
                    fontSize = animatedFontSize.sp,
                    fontWeight = fontWeight,
                    color = Color.White,
                    lineHeight = (animatedFontSize * 1.35f).sp,
                )
            }
        }
    }
}