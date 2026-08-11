

package com.jagr.fridamusic.playback.queues

import androidx.media3.common.MediaItem
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                filterItems { it.metadata?.explicit != true }
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) {
                filterItems { it.metadata?.isVideoSong != true }
            } else {
                this
            }

        private fun filterItems(predicate: (MediaItem) -> Boolean): Status {
            val selectedMediaId = items.getOrNull(mediaItemIndex)?.mediaId
            val filteredItems = items.filter(predicate)
            val filteredIndex = selectedMediaId
                ?.let { id -> filteredItems.indexOfFirst { it.mediaId == id } }
                ?.takeIf { it >= 0 }
                ?: 0
            return copy(
                items = filteredItems,
                mediaItemIndex = filteredIndex.coerceAtMost((filteredItems.size - 1).coerceAtLeast(0)),
            )
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
