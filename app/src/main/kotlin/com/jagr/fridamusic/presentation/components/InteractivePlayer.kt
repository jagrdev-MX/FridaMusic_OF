package com.jagr.fridamusic.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.presentation.screens.NowPlayingScreen
import com.jagr.fridamusic.viewmodels.PlaylistsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun InteractivePlayer(
    playerConnection: PlayerConnection,
    bottomBarHeightPx: Int,
    routeExpanded: Boolean,
    playlistsViewModel: PlaylistsViewModel?,
    onExpanded: () -> Unit,
    onCollapseStarted: () -> Unit,
    onCollapsed: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val mediaId = mediaMetadata?.id ?: return

    val scope = rememberCoroutineScope()
    val state = rememberPlayerSheetState(scope)
    val density = LocalDensity.current
    val velocityThresholdPx = with(density) { 250.dp.toPx() }
    val currentOnExpanded by rememberUpdatedState(onExpanded)
    val currentOnCollapseStarted by rememberUpdatedState(onCollapseStarted)
    val currentOnCollapsed by rememberUpdatedState(onCollapsed)
    val currentOnDismissed by rememberUpdatedState(onDismissed)

    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var miniPlayerHeightPx by remember { mutableFloatStateOf(0f) }
    var metadataNavigationPending by remember { mutableStateOf(false) }
    LaunchedEffect(containerHeightPx, miniPlayerHeightPx, bottomBarHeightPx) {
        if (containerHeightPx > 0f && miniPlayerHeightPx > 0f && bottomBarHeightPx > 0) {
            state.updateOffsets(
                collapsed = (containerHeightPx - miniPlayerHeightPx - bottomBarHeightPx)
                    .coerceAtLeast(0f),
                dismissed = containerHeightPx,
            )
        }
    }

    LaunchedEffect(routeExpanded, state.hasBounds) {
        if (routeExpanded && state.hasBounds && state.isCollapsed) {
            state.expand {}
        }
    }

    fun settle(target: PlayerSheetTarget) {
        when (target) {
            PlayerSheetTarget.Expanded -> currentOnExpanded()
            PlayerSheetTarget.Collapsed -> currentOnCollapsed()
            PlayerSheetTarget.Dismissed -> currentOnDismissed()
        }
    }

    fun collapse() {
        currentOnCollapseStarted()
        state.collapse { settle(PlayerSheetTarget.Collapsed) }
    }

    fun collapseAndNavigate(action: () -> Unit) {
        if (metadataNavigationPending) return
        metadataNavigationPending = true
        currentOnCollapseStarted()
        state.collapse {
            settle(PlayerSheetTarget.Collapsed)
            metadataNavigationPending = false
            action()
        }
    }

    BackHandler(enabled = !state.isCollapsed && !metadataNavigationPending) { collapse() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
            .offset {
                val offset = if (state.hasBounds) state.offset else containerHeightPx
                IntOffset(x = 0, y = offset.roundToInt())
            },
    ) {
        if (state.offset < state.collapsedOffset - 0.5f) {
            NowPlayingScreen(
                playerConnection = playerConnection,
                onBack = ::collapse,
                onNavigateFromPlayer = ::collapseAndNavigate,
                playlistsViewModel = playlistsViewModel,
                modifier = Modifier.graphicsLayer {
                    alpha = ((state.progress - 0.08f) / 0.92f).coerceIn(0f, 1f)
                    val scale = 0.96f + state.progress * 0.04f
                    scaleX = scale
                    scaleY = scale
                },
                collapseDragModifier = Modifier.playerSheetDraggable(
                    state = state,
                    velocityThresholdPx = velocityThresholdPx,
                    enabled = !metadataNavigationPending,
                    onDragStart = {
                        if (state.isExpanded) currentOnCollapseStarted()
                    },
                    onSettled = ::settle,
                ),
            )
        }

        if (!state.isExpanded && !state.isDismissed) {
            MiniPlayer(
                playerConnection = playerConnection,
                onClick = { state.expand { settle(PlayerSheetTarget.Expanded) } },
                modifier = Modifier
                    .onSizeChanged { miniPlayerHeightPx = it.height.toFloat() }
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp)
                    .graphicsLayer {
                        alpha = (1f - state.progress * 4f).coerceIn(0f, 1f)
                        val scale = 1f - state.progress * 0.08f
                        scaleX = scale
                        scaleY = scale
                    }
                    .playerSheetDraggable(
                        state = state,
                        velocityThresholdPx = velocityThresholdPx,
                        enabled = !metadataNavigationPending,
                        onSettled = ::settle,
                    ),
            )
        }
    }
}

@Stable
private class PlayerSheetState(
    private val scope: CoroutineScope,
) {
    private val animatable = Animatable(0f)
    private val motionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    var collapsedOffset by mutableFloatStateOf(0f)
        private set

    var dismissedOffset by mutableFloatStateOf(0f)
        private set

    val hasBounds: Boolean
        get() = collapsedOffset > 0f

    val offset: Float
        get() = animatable.value

    val progress by derivedStateOf {
        if (!hasBounds) 0f else (1f - offset / collapsedOffset).coerceIn(0f, 1f)
    }

    val isCollapsed by derivedStateOf {
        !hasBounds || abs(offset - collapsedOffset) < 0.5f
    }

    val isExpanded by derivedStateOf {
        hasBounds && offset < 0.5f
    }

    val isDismissed by derivedStateOf {
        hasBounds && abs(offset - dismissedOffset) < 0.5f
    }

    suspend fun updateOffsets(collapsed: Float, dismissed: Float) {
        if (collapsed <= 0f || dismissed <= collapsed) return
        if (abs(collapsed - collapsedOffset) < 0.5f &&
            abs(dismissed - dismissedOffset) < 0.5f
        ) return

        val oldOffset = collapsedOffset
        val wasCollapsed = oldOffset <= 0f || abs(offset - oldOffset) < 0.5f
        val previousProgress = if (oldOffset > 0f) {
            (1f - offset / oldOffset).coerceIn(0f, 1f)
        } else {
            0f
        }

        collapsedOffset = collapsed
        dismissedOffset = dismissed
        animatable.updateBounds(lowerBound = 0f, upperBound = dismissed)
        animatable.snapTo(
            if (wasCollapsed) collapsed else collapsed * (1f - previousProgress),
        )
    }

    fun dragBy(delta: Float) {
        if (!hasBounds) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo((offset + delta).coerceIn(0f, dismissedOffset))
        }
    }

    fun settle(
        velocityY: Float,
        velocityThresholdPx: Float,
        onSettled: (PlayerSheetTarget) -> Unit,
    ) {
        val target = when {
            velocityY < -velocityThresholdPx -> {
                if (offset > collapsedOffset) PlayerSheetTarget.Collapsed
                else PlayerSheetTarget.Expanded
            }
            velocityY > velocityThresholdPx -> {
                if (offset >= collapsedOffset) PlayerSheetTarget.Dismissed
                else PlayerSheetTarget.Collapsed
            }
            offset > collapsedOffset -> {
                val dismissProgress =
                    (offset - collapsedOffset) / (dismissedOffset - collapsedOffset)
                if (dismissProgress >= 0.32f) PlayerSheetTarget.Dismissed
                else PlayerSheetTarget.Collapsed
            }
            progress >= 0.32f -> PlayerSheetTarget.Expanded
            else -> PlayerSheetTarget.Collapsed
        }

        when (target) {
            PlayerSheetTarget.Expanded -> expand { onSettled(target) }
            PlayerSheetTarget.Collapsed -> collapse { onSettled(target) }
            PlayerSheetTarget.Dismissed -> dismiss { onSettled(target) }
        }
    }

    fun expand(onFinished: () -> Unit) {
        animateTo(0f, onFinished)
    }

    fun collapse(onFinished: () -> Unit) {
        animateTo(collapsedOffset, onFinished)
    }

    private fun dismiss(onFinished: () -> Unit) {
        animateTo(dismissedOffset, onFinished)
    }

    private fun animateTo(target: Float, onFinished: () -> Unit) {
        if (!hasBounds) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(target, motionSpec)
            onFinished()
        }
    }
}

private enum class PlayerSheetTarget {
    Expanded,
    Collapsed,
    Dismissed,
}

@Composable
private fun rememberPlayerSheetState(scope: CoroutineScope): PlayerSheetState =
    remember(scope) { PlayerSheetState(scope) }

private fun Modifier.playerSheetDraggable(
    state: PlayerSheetState,
    velocityThresholdPx: Float,
    enabled: Boolean = true,
    onDragStart: () -> Unit = {},
    onSettled: (PlayerSheetTarget) -> Unit,
): Modifier = if (!enabled) this else pointerInput(state, velocityThresholdPx) {
    val velocityTracker = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = {
            velocityTracker.resetTracking()
            onDragStart()
        },
        onVerticalDrag = { change, dragAmount ->
            velocityTracker.addPointerInputChange(change)
            change.consume()
            state.dragBy(dragAmount)
        },
        onDragCancel = {
            velocityTracker.resetTracking()
            state.settle(0f, velocityThresholdPx, onSettled)
        },
        onDragEnd = {
            val velocityY = velocityTracker.calculateVelocity().y
            velocityTracker.resetTracking()
            state.settle(velocityY, velocityThresholdPx, onSettled)
        },
    )
}
