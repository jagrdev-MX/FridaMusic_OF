

package com.jagr.fridamusic.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    val resolvedItems = nextResult.items.map { it.toMediaItem() }
                    val preloadMediaItem = preloadItem?.toMediaItem()
                    val selectedIndex = preloadMediaItem?.let { selected ->
                        resolvedItems.indexOfFirst { it.mediaId == selected.mediaId }
                    } ?: -1
                    val queueItems = when {
                        preloadMediaItem == null -> resolvedItems
                        selectedIndex >= 0 -> resolvedItems.toMutableList().apply {
                            // Keep the exact metadata/artwork from the item the user selected.
                            this[selectedIndex] = preloadMediaItem
                        }
                        else -> listOf(preloadMediaItem) + resolvedItems
                    }
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = queueItems,
                        mediaItemIndex = when {
                            preloadMediaItem == null -> nextResult.currentIndex ?: 0
                            selectedIndex >= 0 -> selectedIndex
                            else -> 0
                        },
                    )
                } catch (e: Exception) {
                    lastException = e
                    
                    if (attempt == 0 && endpoint.videoId != null && endpoint.playlistId == null) {
                        endpoint = WatchEndpoint(
                            videoId = endpoint.videoId,
                            playlistId = "RDAMVM${endpoint.videoId}"
                        )
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext nextResult.items.map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null 
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song
            )
        }
    }
}
