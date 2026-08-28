package com.jagr.fridamusic.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FridaLoadingDefaults {
    val InlineIndicatorSize = 18.dp
    val ActionIndicatorSize = 22.dp
    val SmallIndicatorSize = 28.dp
    val MediumIndicatorSize = 32.dp
    val LargeIndicatorSize = 40.dp
}

/**
 * FridaMusic's shared indeterminate content loader.
 *
 * The caller owns the loading region through [modifier], so partial loads do not need to block the
 * rest of the screen. The Material 3 component owns its animation and progress semantics.
 */
@Composable
fun FridaLoadingIndicator(
    modifier: Modifier = Modifier,
    indicatorSize: Dp = FridaLoadingDefaults.MediumIndicatorSize,
    contentAlignment: Alignment = Alignment.Center,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier,
        contentAlignment = contentAlignment,
    ) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(indicatorSize),
            color = color,
        )
    }
}
