

package com.jagr.fridamusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

@Immutable
data class Artist(
    @Embedded
    val artist: ArtistEntity,
    val songCount: Int,
    val timeListened: Int? = 0,
) : LocalItem() {
    // This is the materialized library count, never a remote discography total.
    val effectiveSongCount: Int?
        get() = songCount.takeIf { it > 0 || artist.isLocal }

    override val id: String
        get() = artist.id
    override val title: String
        get() = artist.name
    override val thumbnailUrl: String?
        get() = artist.thumbnailUrl
}
