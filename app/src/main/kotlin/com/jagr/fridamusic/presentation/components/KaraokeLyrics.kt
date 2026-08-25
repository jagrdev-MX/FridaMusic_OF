package com.jagr.fridamusic.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.lyrics.LyricsDocument
import com.jagr.fridamusic.lyrics.LyricsEngine
import com.jagr.fridamusic.lyrics.LyricsInterludeAnimationStyle
import com.jagr.fridamusic.lyrics.LyricsLine
import com.jagr.fridamusic.lyrics.LyricsSyncType
import com.jagr.fridamusic.lyrics.LyricsTimelineItem
import com.jagr.fridamusic.lyrics.LyricsWord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MANUAL_SCROLL_PAUSE_MS = 4_000L

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun KaraokeLyrics(
    lyrics: String,
    positionProvider: () -> Long,
    durationMs: Long,
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = remember(lyrics, durationMs) {
        LyricsEngine.normalize(lyrics, (durationMs / 1_000L).toInt())
    }
    val timeline = remember(document) { LyricsEngine.buildTimeline(document) }
    val latestPositionProvider by rememberUpdatedState(positionProvider)
    var currentTimelineIndex by remember(document) { mutableIntStateOf(-1) }

    LaunchedEffect(timeline, offsetMs) {
        snapshotFlow { timeline.currentItemIndex(latestPositionProvider(), offsetMs) }
            .distinctUntilChanged()
            .collect { currentTimelineIndex = it }
    }

    if (document.isEmpty) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.lyrics_not_found),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var followPlayback by remember { mutableStateOf(true) }
    var manualScrollToken by remember { mutableLongStateOf(0L) }
    val scrollOffsetPx by remember(viewportHeightPx) {
        derivedStateOf { -(viewportHeightPx * 0.28f).toInt() }
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            private var userScrollInProgress = false

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    userScrollInProgress = true
                    followPlayback = false
                    manualScrollToken = SystemClock.uptimeMillis()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (userScrollInProgress) {
                    followPlayback = false
                    manualScrollToken = SystemClock.uptimeMillis()
                    userScrollInProgress = false
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(manualScrollToken) {
        if (manualScrollToken == 0L) return@LaunchedEffect
        delay(MANUAL_SCROLL_PAUSE_MS)
        if (SystemClock.uptimeMillis() - manualScrollToken >= MANUAL_SCROLL_PAUSE_MS) {
            followPlayback = true
        }
    }

    LaunchedEffect(currentTimelineIndex, followPlayback, scrollOffsetPx) {
        if (!followPlayback || currentTimelineIndex < 0 || document.syncType == LyricsSyncType.PLAIN) return@LaunchedEffect
        val distance = abs(listState.firstVisibleItemIndex - currentTimelineIndex)
        if (distance > 12) {
            listState.scrollToItem(currentTimelineIndex, scrollOffsetPx)
        } else {
            listState.animateScrollToItem(currentTimelineIndex, scrollOffsetPx)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .onSizeChanged { viewportHeightPx = it.height }
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(top = 16.dp, bottom = 260.dp),
    ) {
        if (document.syncType == LyricsSyncType.PLAIN && !document.isInstrumentalOnly) {
            itemsIndexed(
                items = document.lines,
                key = { index, line -> "plain:$index:${line.text.hashCode()}" },
                contentType = { _, _ -> "plain_lyric" },
            ) { _, line ->
                PlainLyricsLine(line)
            }
        } else {
            itemsIndexed(
                items = timeline.items,
                key = { _, item -> item.key },
                contentType = { _, item -> if (item is LyricsTimelineItem.Line) "lyric_line" else "interlude" },
            ) { index, item ->
                when (item) {
                    is LyricsTimelineItem.Line -> SyncedLyricsLine(
                        item = item,
                        active = index == currentTimelineIndex,
                        completed = currentTimelineIndex >= 0 && index < currentTimelineIndex,
                        followsInterlude = timeline.items.getOrNull(index - 1) is LyricsTimelineItem.Interlude,
                        positionProvider = latestPositionProvider,
                        offsetMs = offsetMs,
                        onSeekTo = onSeekTo,
                    )

                    is LyricsTimelineItem.Interlude -> InstrumentalInterlude(
                        item = item,
                        active = index == currentTimelineIndex,
                        positionProvider = latestPositionProvider,
                        offsetMs = offsetMs,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyricsLine(line: LyricsLine) {
    Text(
        text = line.text,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncedLyricsLine(
    item: LyricsTimelineItem.Line,
    active: Boolean,
    completed: Boolean,
    followsInterlude: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val targetAlpha = when {
        active -> 1f
        completed -> 0.34f
        else -> 0.20f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "lyrics_line_alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.025f else 1f,
        animationSpec = tween(if (followsInterlude && active) 220 else 320, easing = FastOutSlowInEasing),
        label = "lyrics_line_scale",
    )
    val isRtl = remember(item.line.text) { item.line.text.isRtlText() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 0.5f)
            }
            .alpha(alpha)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onSeekTo((item.startTimeMs + offsetMs).coerceAtLeast(0L)) }
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        if (item.line.words.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item.line.words.forEachIndexed { index, word ->
                    val displayText = word.displayText(
                        nextWord = item.line.words.getOrNull(index + 1),
                        isRtl = isRtl,
                        first = index == 0,
                    )
                    KaraokeWord(
                        text = displayText,
                        word = word,
                        activeLine = active,
                        completedLine = completed,
                        positionProvider = positionProvider,
                        offsetMs = offsetMs,
                        isRtl = isRtl,
                    )
                }
            }
        } else {
            Text(
                text = item.line.text,
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun KaraokeWord(
    text: String,
    word: LyricsWord,
    activeLine: Boolean,
    completedLine: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
    isRtl: Boolean,
) {
    Box {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (word.isBackground) 0.24f else 0.34f),
            fontSize = if (word.isBackground) 21.sp else 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = if (word.isBackground) 0.68f else 1f),
            fontSize = if (word.isBackground) 21.sp else 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.drawWithContent {
                val progress = when {
                    completedLine -> 1f
                    !activeLine -> 0f
                    word.endTimeMs <= word.startTimeMs -> if (positionProvider() >= word.startTimeMs + offsetMs) 1f else 0f
                    else -> ((positionProvider() - (word.startTimeMs + offsetMs)).toFloat() /
                        (word.endTimeMs - word.startTimeMs).toFloat()).coerceIn(0f, 1f)
                }
                if (progress > 0f) {
                    if (isRtl) {
                        clipRect(left = size.width * (1f - progress)) { this@drawWithContent.drawContent() }
                    } else {
                        clipRect(right = size.width * progress) { this@drawWithContent.drawContent() }
                    }
                }
            },
        )
    }
}

@Composable
private fun InstrumentalInterlude(
    item: LyricsTimelineItem.Interlude,
    active: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 0.92f else 0.22f,
        animationSpec = tween(280),
        label = "interlude_alpha",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .alpha(alpha),
    ) {
        val duration = (item.endTimeMs - item.startTimeMs).coerceAtLeast(1L)
        val progress = if (active) {
            ((positionProvider() - (item.startTimeMs + offsetMs)).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.16f
        val completion = ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
        when (item.style) {
            LyricsInterludeAnimationStyle.PULSE -> {
                val pulse = (0.5f + 0.5f * sin(progress * PI.toFloat() * 8f))
                repeat(3) { ring ->
                    drawCircle(
                        color = Color.White.copy(alpha = (0.72f - ring * 0.18f) * (1f - completion * 0.35f)),
                        radius = baseRadius * (0.65f + ring * 0.42f + pulse * 0.16f),
                        center = center,
                        style = Stroke(width = 2.2.dp.toPx()),
                    )
                }
            }

            LyricsInterludeAnimationStyle.BURST -> {
                val radius = baseRadius * (0.75f + completion * 1.15f)
                repeat(8) { ray ->
                    val angle = ray * (PI.toFloat() / 4f) + progress * PI.toFloat()
                    val start = Offset(center.x + cos(angle) * radius * 0.45f, center.y + sin(angle) * radius * 0.45f)
                    val end = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f + completion * 0.45f),
                        start = start,
                        end = end,
                        strokeWidth = 2.4.dp.toPx(),
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.75f), baseRadius * 0.36f, center)
            }

            LyricsInterludeAnimationStyle.RIPPLE -> {
                repeat(3) { ring ->
                    val phase = (progress * 3f + ring / 3f) % 1f
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - phase) * 0.68f),
                        radius = baseRadius * (0.45f + phase * 1.45f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.78f), baseRadius * 0.28f, center)
            }
        }
    }
}

private fun LyricsWord.displayText(nextWord: LyricsWord?, isRtl: Boolean, first: Boolean): String {
    val punctuationOnly = text.all { !it.isLetterOrDigit() }
    val nextIsPunctuation = nextWord?.text?.all { !it.isLetterOrDigit() } == true
    return when {
        isRtl && !first && !punctuationOnly -> " $text"
        !isRtl && nextWord != null && !nextIsPunctuation -> "$text "
        else -> text
    }
}

private fun String.isRtlText(): Boolean = firstNotNullOfOrNull { character ->
    when (Character.getDirectionality(character)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
        -> true

        Character.DIRECTIONALITY_LEFT_TO_RIGHT,
        Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
        Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
        -> false

        else -> null
    }
} ?: false
