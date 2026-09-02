package com.jagr.fridamusic.db.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocalSongProjectionTest {
    @Test
    fun largeLocalLibraryProjectionDoesNotMaterializeFormat() {
        val localSongs = List(10_000) { index ->
            LocalSongProjection(
                song = SongEntity(
                    id = "content://media/external/audio/media/$index",
                    title = "Local song $index",
                    isLocal = true,
                ),
                artists = emptyList(),
            )
        }

        val playbackSongs = localSongs.map(LocalSongProjection::toSong)

        assertEquals(localSongs.size, playbackSongs.size)
        assertSame(localSongs.first().song, playbackSongs.first().song)
        assertNull(playbackSongs.first().format)
        assertNull(playbackSongs.last().format)
        assertFalse(
            LocalSongProjection::class.java.declaredFields.any { field ->
                field.type == FormatEntity::class.java
            },
        )
    }
}
