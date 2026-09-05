package com.music.innertube.models

import org.junit.Assert.*
import org.junit.Test

class PlaylistSongCountTest {
    @Test fun exactCountsAndLocalizedGrouping() {
        listOf("1,234 songs", "1.234 canciones", "1 234 videos", "1\u00a0234 tracks", "1234").forEach {
            assertEquals(it, 1234, parsePlaylistSongCount(it))
        }
        assertEquals(0, parsePlaylistSongCount("0 canciones"))
        assertEquals(1, parsePlaylistSongCount("1 canción"))
    }

    @Test fun unknownApproximateAndUnrelatedNumbersAreNotTotals() {
        listOf(null, "", "Artist 123", "1.2K songs", "1,2 mil canciones", "12 songs • 3 hours", "2:30", "999999999999").forEach {
            assertNull(it, parsePlaylistSongCount(it))
        }
    }

    @Test fun metadataCountNeedNotBeLastSubtitleRun() {
        val runs = Runs(listOf(Run("Author", null), Run(" • ", null), Run("1,234 songs", null), Run(" • updated today", null)))
        assertEquals(1234, parsePlaylistSongCount(runs.playlistSongCountText()))
    }
}
