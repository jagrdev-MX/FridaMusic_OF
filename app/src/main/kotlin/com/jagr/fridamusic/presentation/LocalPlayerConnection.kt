package com.jagr.fridamusic.presentation

import androidx.compose.runtime.staticCompositionLocalOf
import com.jagr.fridamusic.playback.PlayerConnection


val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { null }