package com.jagr.fridamusic.presentation

import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.models.toMediaMetadata
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.playback.queues.ListQueue
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem


fun PlayerConnection.playSong(song: Song, queue: List<Song> = listOf(song)) {
    val startIndex = queue.indexOfFirst { it.song.id == song.song.id }.coerceAtLeast(0)
    playQueue(
        ListQueue(
            title = null,
            items = queue.map { it.toMediaItem() },
            startIndex = startIndex,
        )
    )
}


fun PlayerConnection.playYTItem(item: YTItem) {
    if (item !is SongItem) return
    playQueue(
        YouTubeQueue(
            endpoint = item.endpoint ?: WatchEndpoint(videoId = item.id),
            preloadItem = item.toMediaMetadata(),
        )
    )
}