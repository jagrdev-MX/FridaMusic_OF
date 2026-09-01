

package com.jagr.fridamusic.lyrics

import android.content.Context
import com.jagr.fridamusic.constants.EnableSimpMusicKey
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.get
import com.music.simpmusic.SimpMusicLyrics
import kotlin.math.abs

object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableSimpMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = SimpMusicLyrics.getLyrics(id, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        SimpMusicLyrics.getAllLyrics(id, duration, callback)
    }

    override suspend fun getLyricsCandidates(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (LyricsCandidatePayload) -> Unit,
    ) {
        SimpMusicLyrics.getLyricsByVideoId(id)
            .sortedBy { candidate ->
                if (duration > 0) abs((candidate.duration ?: duration) - duration) else 0
            }
            .take(MAX_CANDIDATES)
            .forEach { candidate ->
                val lyrics = candidate.richSyncLyrics?.takeIf(String::isNotBlank)
                    ?: candidate.syncedLyrics?.takeIf(String::isNotBlank)
                    ?: candidate.plainLyrics?.takeIf(String::isNotBlank)
                    ?: return@forEach
                callback(
                    LyricsCandidatePayload(
                        lyrics = lyrics,
                        title = candidate.title,
                        artist = candidate.artist,
                        album = candidate.album,
                        durationSeconds = candidate.duration,
                    ),
                )
            }
    }

    private const val MAX_CANDIDATES = 5
}
