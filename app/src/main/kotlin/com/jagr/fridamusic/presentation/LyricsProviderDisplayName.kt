package com.jagr.fridamusic.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.lyrics.LyricsProviderRegistry

@Composable
fun String.toLyricsProviderDisplayName(): String = when (this) {
    "YouTubeSubtitle", "YouTube Subtitle" -> stringResource(R.string.lyrics_provider_synced_subtitle)
    "YouTubeMusic", "YouTube Music" -> stringResource(R.string.lyrics_provider_synced_music)
    else -> LyricsProviderRegistry.getDisplayName(this)
}
