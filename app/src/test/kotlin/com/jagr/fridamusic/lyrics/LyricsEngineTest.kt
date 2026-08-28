package com.jagr.fridamusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEngineTest {
    @Test
    fun `large temporal gap becomes compact interlude`() {
        val document = syncedDocument(
            durationMs = 30_000L,
            lines = listOf(
                line(0L, 3_000L, "First line"),
                line(16_000L, 19_000L, "Second line"),
            ),
        )

        val interlude = LyricsEngine.buildTimeline(document).items
            .filterIsInstance<LyricsTimelineItem.Interlude>()
            .first()

        assertEquals(3_000L, interlude.startTimeMs)
        assertEquals(16_000L, interlude.endTimeMs)
    }

    @Test
    fun `positive offset moves lyric later`() {
        val timeline = LyricsEngine.buildTimeline(
            syncedDocument(
                durationMs = 5_000L,
                lines = listOf(line(1_000L, 4_000L, "Line")),
            ),
        )

        assertEquals(-1, timeline.currentItemIndex(positionMs = 1_000L, offsetMs = 700L))
        assertEquals(0, timeline.currentItemIndex(positionMs = 1_700L, offsetMs = 700L))
    }

    @Test
    fun `short synced result is rejected for long song`() {
        val partial = syncedDocument(
            durationMs = 220_000L,
            lines = listOf(
                line(0L, 2_000L, "One"),
                line(25_000L, 27_000L, "Two"),
                line(50_000L, 52_000L, "Three"),
                line(75_000L, 77_000L, "Four"),
            ),
        )
        val complete = syncedDocument(
            durationMs = 220_000L,
            lines = (0..9).map { index ->
                val start = index * 21_000L
                line(start, start + 3_000L, "Line $index")
            },
        )

        val partialScore = LyricsEngine.score(partial, "BetterLyrics", 220).total
        val completeScore = LyricsEngine.score(complete, "BetterLyrics", 220).total

        assertTrue(partialScore < 15)
        assertTrue(completeScore > partialScore)
    }

    @Test
    fun `auto sync accepts stable global offset`() {
        val selected = syncedDocument(
            durationMs = 20_000L,
            lines = listOf(
                line(1_000L, 2_000L, "Same first line"),
                line(5_000L, 6_000L, "Same second line"),
                line(9_000L, 10_000L, "Same third line"),
            ),
        )
        val reference = syncedDocument(
            durationMs = 20_000L,
            lines = listOf(
                line(1_700L, 2_700L, "Same first line"),
                line(5_700L, 6_700L, "Same second line"),
                line(9_700L, 10_700L, "Same third line"),
            ),
        )

        assertEquals(700, LyricsEngine.estimateAutoSyncOffsetMs(selected, reference))
    }

    private fun syncedDocument(durationMs: Long, lines: List<LyricsLine>) = LyricsDocument(
        rawLyrics = "test",
        syncType = LyricsSyncType.LINE_SYNCED,
        format = LyricsFormat.LRC,
        lines = lines,
        durationMs = durationMs,
    )

    private fun line(startMs: Long, endMs: Long, text: String) = LyricsLine(
        startTimeMs = startMs,
        endTimeMs = endMs,
        text = text,
    )
}
