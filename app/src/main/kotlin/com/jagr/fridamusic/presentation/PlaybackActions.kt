package com.jagr.fridamusic.presentation

import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.models.toMediaMetadata
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.playback.queues.ListQueue
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.jagr.fridamusic.utils.isLocalMediaId
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem


fun PlayerConnection.playSong(song: Song, queue: List<Song> = listOf(song)) {
    playSong(song, queue, preferPlayerCache = false)
}

fun PlayerConnection.playCachedSong(song: Song, queue: List<Song> = listOf(song)) {
    playSong(song, queue, preferPlayerCache = true)
}

private fun PlayerConnection.playSong(
    song: Song,
    queue: List<Song>,
    preferPlayerCache: Boolean,
) {
    val startIndex = queue.indexOfFirst { it.song.id == song.song.id }.coerceAtLeast(0)
    playQueue(
        ListQueue(
            title = null,
            items = queue.map { it.toMediaItem(preferPlayerCache) },
            startIndex = startIndex,
        )
    )
}


fun PlayerConnection.playYTItem(item: YTItem) {
    if (item !is SongItem) return

    if (item.id.isLocalMediaId()) {
        playQueue(
            ListQueue(
                title = null,
                items = listOf(item.toMediaItem()),
            )
        )
        return
    }

    playQueue(
        YouTubeQueue(
            endpoint = item.endpoint ?: WatchEndpoint(videoId = item.id),
            preloadItem = item.toMediaMetadata(),
        )
    )
}
