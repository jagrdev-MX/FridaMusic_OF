package com.jagr.fridamusic.db.entities

import com.jagr.fridamusic.extensions.reversed
import org.junit.Assert.*
import org.junit.Test

class LibraryCountTest {
    private fun playlist(local: Int, remote: Int?, isLocal: Boolean = false) = Playlist(
        PlaylistEntity(id = "test", name = "Test", browseId = "remote", isLocal = isLocal, remoteSongCount = remote),
        local, emptyList(),
    )

    @Test fun remoteMetadataAndLocalRelationsAreNotAddedTwice() {
        assertEquals(15, playlist(4, 15).effectiveSongCount)
        assertEquals(15, playlist(15, 15).effectiveSongCount)
        assertEquals(16, playlist(16, 15).effectiveSongCount)
    }

    @Test fun unknownIsDifferentFromConfirmedEmpty() {
        assertNull(playlist(0, null).effectiveSongCount)
        assertEquals(0, playlist(0, 0).effectiveSongCount)
        assertEquals(4, playlist(4, null).effectiveSongCount)
        assertEquals(0, playlist(0, 15, isLocal = true).effectiveSongCount)
    }

    @Test fun artistCountIsLibraryMembershipNotDiscography() {
        val remote = ArtistEntity(id = "UCtest", name = "Test")
        assertNull(Artist(remote, 0).effectiveSongCount)
        assertEquals(3, Artist(remote, 3).effectiveSongCount)
        assertEquals(0, Artist(remote.copy(isLocal = true), 0).effectiveSongCount)
    }

    @Test fun directionPreservesDatasetAndDoesNotDuplicateItems() {
        val ascending = listOf("a", "b", "c")
        assertEquals(listOf("c", "b", "a"), ascending.reversed(true))
        assertEquals(ascending, ascending.reversed(false))
        assertEquals(ascending.toSet(), ascending.reversed(true).toSet())
        assertEquals(3, ascending.reversed(true).size)
    }
}
