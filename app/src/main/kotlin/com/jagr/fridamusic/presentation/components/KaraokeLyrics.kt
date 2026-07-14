package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.lyrics.LyricsUtils
import kotlinx.coroutines.launch

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

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            scope.launch {
                listState.animateScrollToItem(currentLineIndex, scrollOffset = -150)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 150.dp)
    ) {
        itemsIndexed(parsedLyrics) { index, entry ->
            val isCurrentLine = index == currentLineIndex
            val color by animateColorAsState(
                targetValue = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.4f),
                label = "lyric_color"
            )
            val scale by animateFloatAsState(
                targetValue = if (isCurrentLine) 1.1f else 1f,
                label = "lyric_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(entry.time) }
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = entry.text,
                    fontSize = (28 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    lineHeight = 36.sp
                )
            }
        }
    }
}
