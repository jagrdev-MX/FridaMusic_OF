package com.jagr.fridamusic.spotify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyMapperTest {

    @Test
    fun exactSongWithPrimaryArtistAndFeaturedArtistIsAccepted() {
        val evaluation = SpotifyMapper.evaluateMatch(
            spotifyTitle = "Song (feat. Guest)",
            spotifyArtist = "Artist Guest",
            spotifyDurationMs = 210_000,
            candidateTitle = "Song",
            candidateArtist = "Artist",
            candidateDurationSec = 210,
        )

        assertTrue(evaluation.isAcceptable)
    }

    @Test
    fun remixIsRejectedWhenOriginalWasRequested() {
        val evaluation = SpotifyMapper.evaluateMatch(
            spotifyTitle = "Song",
            spotifyArtist = "Artist",
            spotifyDurationMs = 210_000,
            candidateTitle = "Song (Club Remix)",
            candidateArtist = "Artist",
            candidateDurationSec = 211,
        )

        assertFalse(evaluation.isAcceptable)
    }

    @Test
    fun studioVersionIsRejectedWhenLiveVersionWasRequested() {
        val evaluation = SpotifyMapper.evaluateMatch(
            spotifyTitle = "Song - Live",
            spotifyArtist = "Artist",
            spotifyDurationMs = 240_000,
            candidateTitle = "Song",
            candidateArtist = "Artist",
            candidateDurationSec = 240,
        )

        assertFalse(evaluation.isAcceptable)
    }

    @Test
    fun largeDurationMismatchIsRejected() {
        val evaluation = SpotifyMapper.evaluateMatch(
            spotifyTitle = "Song",
            spotifyArtist = "Artist",
            spotifyDurationMs = 210_000,
            candidateTitle = "Song",
            candidateArtist = "Artist",
            candidateDurationSec = 280,
        )

        assertFalse(evaluation.isAcceptable)
    }

    @Test
    fun accentsDoNotPreventAnOtherwiseExactMatch() {
        val evaluation = SpotifyMapper.evaluateMatch(
            spotifyTitle = "Corazón",
            spotifyArtist = "Beyoncé",
            spotifyDurationMs = 200_000,
            candidateTitle = "Corazon",
            candidateArtist = "Beyonce",
            candidateDurationSec = 200,
        )

        assertTrue(evaluation.isAcceptable)
    }

    @Test
    fun genericArtistWordsDoNotMakeDifferentArtistsAcceptable() {
        val evaluation = SpotifyMapper.evaluateMatch(
            spotifyTitle = "Home",
            spotifyArtist = "The Weeknd",
            spotifyDurationMs = 180_000,
            candidateTitle = "Home",
            candidateArtist = "The Beatles",
            candidateDurationSec = 180,
        )

        assertFalse(evaluation.isAcceptable)
    }
}
