package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

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
