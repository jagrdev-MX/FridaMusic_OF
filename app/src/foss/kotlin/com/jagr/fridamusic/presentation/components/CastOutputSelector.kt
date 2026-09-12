package com.jagr.fridamusic.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jagr.fridamusic.playback.PlayerConnection

@Composable
fun CastRouteButton(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) = Unit

@Composable
fun CastOutputSelector(playerConnection: PlayerConnection) = Unit

@Composable
fun CastDrawerItem(
    playerConnection: PlayerConnection?,
    content: @Composable (onClick: () -> Unit) -> Unit,
) = Unit
