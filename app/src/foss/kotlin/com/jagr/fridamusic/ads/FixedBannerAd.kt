package com.jagr.fridamusic.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class FixedBannerPlacement {
    HOME_FIXED,
}

@Composable
fun FixedBannerAd(
    placement: FixedBannerPlacement,
    modifier: Modifier = Modifier,
    onVisibleHeightChanged: (Dp) -> Unit = {},
) {
    SideEffect { onVisibleHeightChanged(0.dp) }
}
