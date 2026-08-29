package com.jagr.fridamusic.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem

@Entity(tableName = "speed_dial_item")
data class SpeedDialItem(
    @PrimaryKey val id: String,
    val secondaryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val type: String, 
    val explicit: Boolean = false,
    val createDate: Long = System.currentTimeMillis()
) {
    fun toYTItem(): YTItem {
        return when (type) {
            "SONG" -> SongItem(
                id = id,
                title = title,
                artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) } ?: emptyList(),
                thumbnail = thumbnailUrl ?: "",
                explicit = explicit
            )
            "ALBUM" -> AlbumItem(
                browseId = id,
                playlistId = secondaryId ?: "",
                title = title,
                artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) },
                thumbnail = thumbnailUrl ?: "",
                explicit = explicit
            )
            "ARTIST" -> ArtistItem(
                id = id,
                title = title,
                thumbnail = thumbnailUrl,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            "PLAYLIST" -> {
                val playlistId = secondaryId
                    ?: id.takeIf { it.startsWith("VL") }?.removePrefix("VL")
                val endpoint = playlistId?.takeIf(String::isNotBlank)?.let {
                    WatchEndpoint(playlistId = it)
                }
                PlaylistItem(
                    id = id,
                    title = title,
                    author = subtitle?.let { Artist(name = it, id = null) },
                    songCountText = null,
                    thumbnail = thumbnailUrl,
                    playEndpoint = endpoint,
                    shuffleEndpoint = endpoint,
                    radioEndpoint = endpoint,
                )
            }
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }

    companion object {
        fun fromYTItem(item: YTItem): SpeedDialItem {
            return when (item) {
                is SongItem -> SpeedDialItem(
                    id = item.id,
                    title = item.title,
                    subtitle = item.artists.joinToString(", ") { it.name },
                    thumbnailUrl = item.thumbnail,
                    type = "SONG",
                    explicit = item.explicit
                )
                is AlbumItem -> SpeedDialItem(
                    id = item.browseId,
                    secondaryId = item.playlistId,
                    title = item.title,
                    subtitle = item.artists?.joinToString(", ") { it.name },
                    thumbnailUrl = item.thumbnail,
                    type = "ALBUM",
                    explicit = item.explicit
                )
                is ArtistItem -> SpeedDialItem(
                    id = item.id,
                    title = item.title,
                    thumbnailUrl = item.thumbnail,
                    type = "ARTIST"
                )
                is PlaylistItem -> SpeedDialItem(
                    id = item.id,
                    secondaryId = (
                        item.playEndpoint ?: item.radioEndpoint ?: item.shuffleEndpoint
                        )?.playlistId,
                    title = item.title,
                    subtitle = item.author?.name,
                    thumbnailUrl = item.thumbnail,
                    type = "PLAYLIST"
                )
            }
        }
    }
}
