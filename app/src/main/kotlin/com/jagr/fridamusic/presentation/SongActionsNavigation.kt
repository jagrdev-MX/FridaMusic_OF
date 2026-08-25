package com.jagr.fridamusic.presentation

import androidx.compose.runtime.staticCompositionLocalOf

data class SongActionsNavigation(
    val openAlbum: (String) -> Unit = {},
    val openArtist: (String) -> Unit = {},
)

val LocalSongActionsNavigation = staticCompositionLocalOf { SongActionsNavigation() }
