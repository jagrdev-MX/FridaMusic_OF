package com.jagr.fridamusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Room projection for local-library lists.
 *
 * Local playback resolves the live format independently, so eagerly joining the persisted
 * [FormatEntity] for every row only increases the list's memory footprint.
 */
@Immutable
data class LocalSongProjection(
    @Embedded val song: SongEntity,
    @Relation(
        entity = ArtistEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy =
            Junction(
                value = SortedSongArtistMap::class,
                parentColumn = "songId",
                entityColumn = "artistId",
            ),
    )
    val artists: List<ArtistEntity>,
    @Relation(
        entity = AlbumEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy =
            Junction(
                value = SongAlbumMap::class,
                parentColumn = "songId",
                entityColumn = "albumId",
            ),
    )
    val album: AlbumEntity? = null,
) {
    fun toSong(): Song = Song(
        song = song,
        artists = artists,
        album = album,
        format = null,
    )
}

/** Minimal projection used by Home for the small set of pinned local songs. */
@Immutable
data class LocalSongPreview(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val albumId: String?,
    val explicit: Boolean,
    @Relation(
        entity = ArtistEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy =
            Junction(
                value = SortedSongArtistMap::class,
                parentColumn = "songId",
                entityColumn = "artistId",
            ),
    )
    val artists: List<ArtistEntity>,
) {
    val resolvedThumbnailUrl: String?
        get() {
            val mediaStoreAlbumId = albumId?.removePrefix("LOCAL_ALBUM_")?.toLongOrNull()
            return if (mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
                "content://media/external/audio/albumart/$mediaStoreAlbumId"
            } else {
                thumbnailUrl
            }
        }
}
