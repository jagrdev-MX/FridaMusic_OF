

package com.jagr.fridamusic.lyrics

import android.content.Context
import com.music.lrclib.LrcLib
import com.jagr.fridamusic.constants.EnableLrcLibKey
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.get
import kotlin.math.abs

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, album, callback)
    }

    override suspend fun getLyricsCandidates(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (LyricsCandidatePayload) -> Unit,
    ) {
        LrcLib.searchTracks(title, artist, album)
            .sortedBy { track ->
                if (duration > 0) abs(track.duration.toInt() - duration) else 0
            }
            .take(MAX_CANDIDATES)
            .forEach { track ->
                val lyrics = track.syncedLyrics ?: track.plainLyrics ?: return@forEach
                callback(
                    LyricsCandidatePayload(
                        lyrics = lyrics,
                        title = track.trackName,
                        artist = track.artistName,
                        durationSeconds = track.duration.toInt(),
                    ),
                )
            }
    }

    private const val MAX_CANDIDATES = 5
}
