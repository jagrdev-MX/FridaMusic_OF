package com.jagr.fridamusic.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.lyrics.LyricsEngine
import com.jagr.fridamusic.lyrics.LyricsInterludeAnimationStyle
import com.jagr.fridamusic.lyrics.LyricsLine
import com.jagr.fridamusic.lyrics.LyricsSyncType
import com.jagr.fridamusic.lyrics.LyricsTimelineItem
import com.jagr.fridamusic.lyrics.LyricsWord
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MANUAL_SCROLL_PAUSE_MS = 4_000L
private const val NATURAL_TRANSITION_MAX_DELTA_MS = 350L
private const val INTERLUDE_COMPLETION_DURATION_MS = 340L
private const val LINE_ENTRANCE_DURATION_MS = 280L
private val AppleMusicEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun KaraokeLyrics(
    mediaId: String,
    lyrics: String,
    positionProvider: () -> Long,
    durationMs: Long,
    offsetMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onEmptyStateChange: (Boolean) -> Unit = {},
) {
    val document = remember(lyrics, durationMs) {
        LyricsEngine.normalize(lyrics, (durationMs / 1_000L).toInt())
    }
    val timeline = remember(document) { LyricsEngine.buildTimeline(document) }
    LaunchedEffect(mediaId, lyrics, document.isEmpty) {
        onEmptyStateChange(document.isEmpty)
    }
    val latestPositionProvider by rememberUpdatedState(positionProvider)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    var currentTimelineIndex by remember(mediaId, timeline) { mutableIntStateOf(-1) }
    var completedInterludeKey by remember(mediaId, timeline) { mutableStateOf<String?>(null) }
    var enteringLineKey by remember(mediaId, timeline) { mutableStateOf<String?>(null) }

    LaunchedEffect(mediaId, timeline, offsetMs) {
        var previousPosition = latestPositionProvider()
        var previousIndex = timeline.currentItemIndex(previousPosition, offsetMs)
        var completionExpiresAtMs = Long.MIN_VALUE
        currentTimelineIndex = previousIndex
        completedInterludeKey = null
        enteringLineKey = null

        snapshotFlow { latestPositionProvider() }.collect { position ->
            val nextIndex = timeline.currentItemIndex(position, offsetMs)
            if (nextIndex != previousIndex) {
                val previousItem = timeline.items.getOrNull(previousIndex)
                val nextItem = timeline.items.getOrNull(nextIndex)
                val naturalInterludeCompletion =
                    previousItem is LyricsTimelineItem.Interlude &&
                        nextItem is LyricsTimelineItem.Line &&
                        nextIndex == previousIndex + 1 &&
                        abs(position - previousPosition) <= NATURAL_TRANSITION_MAX_DELTA_MS &&
                        latestIsPlaying

                if (naturalInterludeCompletion) {
                    completedInterludeKey = previousItem.key
                    enteringLineKey = nextItem.key
                    completionExpiresAtMs = position + INTERLUDE_COMPLETION_DURATION_MS
                } else {
                    completedInterludeKey = null
                    enteringLineKey = null
                    completionExpiresAtMs = Long.MIN_VALUE
                }
                currentTimelineIndex = nextIndex
                previousIndex = nextIndex
            }

            if (completedInterludeKey != null && position > completionExpiresAtMs) {
                completedInterludeKey = null
                enteringLineKey = null
            }
            previousPosition = position
        }
    }

    if (document.isEmpty) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.lyrics_not_found),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    if (document.isInstrumentalOnly) {
        val interlude = timeline.items.firstOrNull() as? LyricsTimelineItem.Interlude
        if (interlude != null) {
            InstrumentalOnlyLyrics(
                item = interlude,
                isPlaying = isPlaying,
                positionProvider = latestPositionProvider,
                offsetMs = offsetMs,
                modifier = modifier,
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
    val readZonePadding = with(LocalDensity.current) {
        (viewportHeightPx * 0.28f).toDp().coerceAtLeast(16.dp)
    }
    val agentLanes = remember(document.lines) {
        document.lines.mapNotNull(LyricsLine::agent).distinct().withIndex().associate { it.value to it.index }
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
        contentPadding = PaddingValues(top = readZonePadding, bottom = 260.dp),
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
                        distanceFromActive = if (currentTimelineIndex >= 0) abs(index - currentTimelineIndex) else Int.MAX_VALUE,
                        agentLane = item.line.agent?.let(agentLanes::get) ?: 0,
                        hasMultipleAgents = agentLanes.size > 1,
                        enteringFromInterlude = enteringLineKey == item.key,
                        positionProvider = latestPositionProvider,
                        offsetMs = offsetMs,
                        onSeekTo = onSeekTo,
                    )

                    is LyricsTimelineItem.Interlude -> InstrumentalInterlude(
                        item = item,
                        active = index == currentTimelineIndex,
                        completed = currentTimelineIndex >= 0 && index < currentTimelineIndex,
                        completionActive = completedInterludeKey == item.key,
                        isPlaying = isPlaying,
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
    val layoutDirection = if (remember(line.text) { line.text.isRtlText() }) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Text(
            text = line.text,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncedLyricsLine(
    item: LyricsTimelineItem.Line,
    active: Boolean,
    completed: Boolean,
    distanceFromActive: Int,
    agentLane: Int,
    hasMultipleAgents: Boolean,
    enteringFromInterlude: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val hierarchyAlpha = when {
        active -> 1f
        completed && distanceFromActive == 1 -> 0.52f
        distanceFromActive == 1 -> 0.44f
        completed && distanceFromActive == 2 -> 0.40f
        distanceFromActive == 2 -> 0.32f
        completed -> 0.31f
        else -> 0.24f
    }
    val targetAlpha = hierarchyAlpha * if (item.line.isBackground) 0.72f else 1f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(360, easing = AppleMusicEasing),
        label = "lyrics_line_alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.024f else 1f,
        animationSpec = tween(320, easing = AppleMusicEasing),
        label = "lyrics_line_scale",
    )
    val isRtl = remember(item.line.text) { item.line.text.isRtlText() }
    val alignToEnd = hasMultipleAgents && agentLane % 2 == 1
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val nudgeDistancePx = with(LocalDensity.current) { 6.dp.toPx() }
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val entranceProgress = if (enteringFromInterlude) {
                        ((positionProvider() - (item.startTimeMs + offsetMs)).toFloat() /
                            LINE_ENTRANCE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    val entranceScale = 0.985f + entranceProgress * 0.015f
                    scaleX = scale * entranceScale
                    scaleY = scale * entranceScale
                    translationY = (1f - entranceProgress) * nudgeDistancePx
                    this.alpha = alpha * (0.72f + entranceProgress * 0.28f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (alignToEnd) 1f else 0f,
                        pivotFractionY = 0.5f,
                    )
                }
                .clickable(
                    indication = indication,
                    interactionSource = interactionSource,
                ) { onSeekTo((item.startTimeMs + offsetMs).coerceAtLeast(0L)) }
                .padding(
                    start = if (alignToEnd) 36.dp else 20.dp,
                    end = if (hasMultipleAgents && !alignToEnd) 36.dp else 20.dp,
                    top = 9.dp,
                    bottom = 9.dp,
                ),
            contentAlignment = if (alignToEnd) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            if (item.line.words.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (alignToEnd) Arrangement.End else Arrangement.Start,
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
                            isBackground = item.line.isBackground || word.isBackground,
                        )
                    }
                }
            } else {
                Text(
                    text = item.line.text,
                    color = Color.White,
                    fontSize = if (item.line.isBackground) 22.sp else 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = if (item.line.isBackground) FontWeight.Bold else FontWeight.ExtraBold,
                    textAlign = if (alignToEnd) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    isBackground: Boolean,
) {
    val fontSize = if (isBackground) 21.sp else 26.sp
    val nudgePx = with(LocalDensity.current) { 3.dp.toPx() }
    val wordStartMs = word.startTimeMs + offsetMs
    val wordEndMs = word.endTimeMs + offsetMs
    Box(
        modifier = Modifier.graphicsLayer {
            val elapsed = if (activeLine) positionProvider() - wordStartMs else Long.MIN_VALUE
            val nudgeProgress = when {
                !activeLine || elapsed < 0L || elapsed >= 270L -> 0f
                elapsed < 90L -> elapsed / 90f
                else -> 1f - (elapsed - 90L) / 180f
            }
            translationX = (if (isRtl) -1f else 1f) * nudgePx * nudgeProgress.coerceIn(0f, 1f)
        },
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (isBackground) 0.24f else 0.36f),
            fontSize = fontSize,
            lineHeight = 34.sp,
            fontWeight = if (isBackground) FontWeight.Bold else FontWeight.ExtraBold,
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = if (isBackground) 0.72f else 1f),
            fontSize = fontSize,
            lineHeight = 34.sp,
            fontWeight = if (isBackground) FontWeight.Bold else FontWeight.ExtraBold,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val featherWidthPx = 12.dp.toPx()
                    onDrawWithContent {
                        val progress = when {
                            completedLine -> 1f
                            !activeLine -> 0f
                            wordEndMs <= wordStartMs -> if (positionProvider() >= wordStartMs) 1f else 0f
                            else -> ((positionProvider() - wordStartMs).toFloat() /
                                (wordEndMs - wordStartMs).toFloat()).coerceIn(0f, 1f)
                        }
                        if (progress <= 0f) return@onDrawWithContent
                        if (progress >= 1f || size.width <= 0f) {
                            drawContent()
                            return@onDrawWithContent
                        }

                        drawContent()
                        val feather = min(featherWidthPx, size.width * 0.25f)
                        val mask = if (isRtl) {
                            val edge = size.width * (1f - progress)
                            val fadeStart = ((edge - feather) / size.width).coerceIn(0f, 1f)
                            val solidStart = (edge / size.width).coerceIn(0f, 1f)
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                fadeStart to Color.Transparent,
                                solidStart to Color.Black,
                                1f to Color.Black,
                            )
                        } else {
                            val edge = size.width * progress
                            val solidEnd = (edge / size.width).coerceIn(0f, 1f)
                            val fadeEnd = ((edge + feather) / size.width).coerceIn(0f, 1f)
                            Brush.horizontalGradient(
                                0f to Color.Black,
                                solidEnd to Color.Black,
                                fadeEnd to Color.Transparent,
                                1f to Color.Transparent,
                            )
                        }
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    }
                },
        )
    }
}

@Composable
private fun InstrumentalInterlude(
    item: LyricsTimelineItem.Interlude,
    active: Boolean,
    completed: Boolean,
    completionActive: Boolean,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            active || completionActive -> 0.94f
            completed -> 0.34f
            else -> 0.24f
        },
        animationSpec = tween(300, easing = AppleMusicEasing),
        label = "interlude_alpha",
    )
    LyricsInterludeIndicator(
        startTimeMs = item.startTimeMs,
        endTimeMs = item.endTimeMs,
        style = item.style,
        active = active,
        completionActive = completionActive,
        isPlaying = isPlaying,
        positionProvider = positionProvider,
        offsetMs = offsetMs,
        prominent = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .alpha(alpha),
    )
}

@Composable
private fun InstrumentalOnlyLyrics(
    item: LyricsTimelineItem.Interlude,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LyricsInterludeIndicator(
                startTimeMs = item.startTimeMs,
                endTimeMs = item.endTimeMs,
                style = item.style,
                active = true,
                completionActive = false,
                isPlaying = isPlaying,
                positionProvider = positionProvider,
                offsetMs = offsetMs,
                prominent = true,
                modifier = Modifier.size(156.dp),
            )
            Text(
                text = stringResource(R.string.instrumental),
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LyricsInterludeIndicator(
    startTimeMs: Long,
    endTimeMs: Long,
    style: LyricsInterludeAnimationStyle,
    active: Boolean,
    completionActive: Boolean,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
    prominent: Boolean,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val duration = (endTimeMs - startTimeMs).coerceAtLeast(1L)
        val position = if (active || completionActive || prominent) positionProvider() else startTimeMs + offsetMs
        val relativePosition = position - (startTimeMs + offsetMs)
        val progress = if (active || prominent) {
            (relativePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val completionProgress = if (completionActive) {
            ((position - (endTimeMs + offsetMs)).toFloat() / INTERLUDE_COMPLETION_DURATION_MS.toFloat())
                .coerceIn(0f, 1f)
        } else {
            -1f
        }
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * if (prominent) 0.15f else 0.16f
        val motionStrength = if (isPlaying) 1f else 0.88f
        val visualTimeMs = relativePosition.coerceAtLeast(0L)
        val breathingPhase = (visualTimeMs % 1_400L).toFloat() / 1_400f * PI.toFloat() * 2f
        val completionApproach = ((progress - 0.84f) / 0.16f).coerceIn(0f, 1f)
        val coreColor = Color.White.copy(alpha = if (prominent) 0.82f else 0.76f)

        when (style) {
            LyricsInterludeAnimationStyle.PULSE -> {
                val pulse = 0.5f + 0.5f * sin(breathingPhase)
                drawCircle(
                    color = coreColor,
                    radius = baseRadius * (0.34f + pulse * 0.06f * motionStrength),
                    center = center,
                )
                repeat(2) { ring ->
                    drawCircle(
                        color = Color.White.copy(alpha = (0.50f - ring * 0.16f) * (1f - completionApproach * 0.25f)),
                        radius = baseRadius * (0.72f + ring * 0.48f + pulse * 0.12f * motionStrength),
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx()),
                    )
                }
            }

            LyricsInterludeAnimationStyle.BURST -> {
                val radius = baseRadius * (0.72f + completionApproach * 0.48f)
                repeat(6) { ray ->
                    val angle = ray * (PI.toFloat() / 3f) + breathingPhase * 0.08f
                    val start = Offset(center.x + cos(angle) * radius * 0.58f, center.y + sin(angle) * radius * 0.58f)
                    val end = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    drawLine(
                        color = Color.White.copy(alpha = 0.20f + completionApproach * 0.48f),
                        start = start,
                        end = end,
                        strokeWidth = 1.8.dp.toPx(),
                    )
                }
                drawCircle(coreColor, baseRadius * (0.30f + completionApproach * 0.06f), center)
            }

            LyricsInterludeAnimationStyle.RIPPLE -> {
                repeat(2) { ring ->
                    val phase = ((visualTimeMs % 1_800L).toFloat() / 1_800f + ring * 0.5f) % 1f
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - phase) * 0.52f * motionStrength),
                        radius = baseRadius * (0.45f + phase * 1.35f),
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx()),
                    )
                }
                drawCircle(coreColor, baseRadius * 0.28f, center)
            }
        }

        if (completionProgress in 0f..1f) {
            val fade = 1f - completionProgress
            val radius = baseRadius * (0.58f + completionProgress * 1.45f)
            repeat(8) { ray ->
                val angle = ray * (PI.toFloat() / 4f)
                val startRadius = radius * 0.58f
                drawLine(
                    color = Color.White.copy(alpha = fade * fade * 0.72f),
                    start = Offset(
                        center.x + cos(angle) * startRadius,
                        center.y + sin(angle) * startRadius,
                    ),
                    end = Offset(
                        center.x + cos(angle) * radius,
                        center.y + sin(angle) * radius,
                    ),
                    strokeWidth = 1.8.dp.toPx(),
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = fade * 0.62f),
                radius = baseRadius * (0.42f + completionProgress * 0.18f),
                center = center,
            )
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
