package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val DEFAULT_DIRECT_ANIMATION_ITEM_LIMIT = 16

internal suspend fun LazyListState.animateReselectScrollToTop(
    directAnimationItemLimit: Int = DEFAULT_DIRECT_ANIMATION_ITEM_LIMIT,
) {
    if (firstVisibleItemIndex > directAnimationItemLimit) {
        scrollToItem(directAnimationItemLimit / 2)
    }

    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
        animateScrollToItem(0)
    }
}

internal suspend fun LazyGridState.animateReselectScrollToTop(
    directAnimationItemLimit: Int = DEFAULT_DIRECT_ANIMATION_ITEM_LIMIT,
) {
    if (firstVisibleItemIndex > directAnimationItemLimit) {
        scrollToItem(directAnimationItemLimit / 2)
    }

    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
        animateScrollToItem(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberQuickReturnTopAppBarState(): TopAppBarState = rememberTopAppBarState()

/**
 * Quick-return delta adapted from Arturo254/OpenTune AppBar.kt (GPL-3.0).
 * The connection only observes descendant scroll and never consumes it.
 */
@Composable
internal fun rememberQuickReturnConnection(state: TopAppBarState): NestedScrollConnection =
    rememberQuickReturnConnection(state, rememberCoroutineScope())

@Composable
private fun rememberQuickReturnConnection(
    state: TopAppBarState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): NestedScrollConnection =
    remember(state, coroutineScope) {
        object : NestedScrollConnection {
            private var lastVerticalDelta = 0f
            private var settleJob: Job? = null

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                val limit = state.heightOffsetLimit
                if (limit >= 0f || limit == -Float.MAX_VALUE) return Offset.Zero

                val delta = consumed.y + available.y.coerceAtLeast(0f)
                if (delta != 0f) {
                    settleJob?.cancel()
                    settleJob = null
                    lastVerticalDelta = delta
                    state.contentOffset += consumed.y
                    state.heightOffset = (state.heightOffset + delta).coerceIn(limit, 0f)
                    if (state.heightOffset == 0f) state.contentOffset = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val limit = state.heightOffsetLimit
                if (limit >= 0f || limit == -Float.MAX_VALUE) return Velocity.Zero

                val direction = available.y.takeUnless { it == 0f } ?: lastVerticalDelta
                val target = when {
                    direction > 0f -> 0f
                    direction < 0f -> limit
                    state.heightOffset > limit / 2f -> 0f
                    else -> limit
                }
                lastVerticalDelta = 0f
                settleJob?.cancel()
                settleJob = coroutineScope.launch {
                    state.animateQuickReturnTo(target)
                }
                return Velocity.Zero
            }
        }
    }

internal suspend fun TopAppBarState.animateQuickReturnVisible() {
    animateQuickReturnTo(0f)
}

private suspend fun TopAppBarState.animateQuickReturnTo(target: Float) {
    val boundedTarget = target.coerceIn(heightOffsetLimit, 0f)
    if (heightOffset == boundedTarget) return

    animate(
        initialValue = heightOffset,
        targetValue = boundedTarget,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    ) { value, _ ->
        heightOffset = value.coerceIn(heightOffsetLimit, 0f)
    }
    contentOffset = 0f
}
