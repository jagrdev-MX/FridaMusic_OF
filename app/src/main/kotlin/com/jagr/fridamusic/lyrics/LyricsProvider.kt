

package com.jagr.fridamusic.lyrics

import android.content.Context

interface LyricsProvider {
    val name: String

    fun isEnabled(context: Context): Boolean

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>

    suspend fun getLyricsCandidates(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsCandidatePayload) -> Unit,
    ) {
        getAllLyrics(id, title, artist, duration, album) { lyrics ->
            callback(LyricsCandidatePayload(lyrics = lyrics))
        }
    }

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration, album).onSuccess(callback)
    }
}

data class LyricsCandidatePayload(
    val lyrics: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationSeconds: Int? = null,
)
