package com.jagr.fridamusic.utils.qobuz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Models

@Serializable
data class QobuzSearchData(
    val query: String? = null,
    val tracks: QobuzTrackList? = null,
    val albums: QobuzAlbumList? = null,
    val switchTo: String? = null,
)

@Serializable
data class QobuzTrackList(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val items: List<QobuzTrack> = emptyList(),
)

@Serializable
data class QobuzAlbumList(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val items: List<QobuzAlbum> = emptyList(),
)

@Serializable
data class QobuzTrack(
    val id: Long,
    val title: String,
    val duration: Int = 0,
    val isrc: String? = null,
    val performer: QobuzPerformer? = null,
    val album: QobuzAlbum? = null,
    @SerialName("maximum_bit_depth") val maximumBitDepth: Int = 0,
    @SerialName("maximum_sampling_rate") val maximumSamplingRate: Float = 0f,
    val streamable: Boolean = true,
    val hires: Boolean = false,
    val version: String? = null,
)

@Serializable
data class QobuzPerformer(
    val id: Long? = null,
    val name: String,
)

@Serializable
data class QobuzAlbum(
    val id: String? = null,
    val title: String? = null,
    @SerialName("tracks_count") val tracksCount: Int = 0,
    val artist: QobuzPerformer? = null,
    val image: QobuzImage? = null,
    val upc: String? = null,
    @SerialName("released_at") val releasedAt: Long = 0L,
)

@Serializable
data class QobuzImage(
    val small: String? = null,
    val thumbnail: String? = null,
    val large: String? = null,
    val back: String? = null,
)

object QobuzQuality {
    const val MP3_320: Int = 5
    const val FLAC_CD: Int = 6
    const val FLAC_HIRES_96: Int = 7
    const val FLAC_HIRES_192: Int = 27
}
