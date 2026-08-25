package com.jagr.fridamusic.lyrics

enum class LyricsSyncType {
    PLAIN,
    LINE_SYNCED,
    WORD_SYNCED,
}

enum class LyricsFormat {
    PLAIN_TEXT,
    LRC,
    ENHANCED_LRC,
    TTML,
}

data class LyricsWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isBackground: Boolean = false,
)

data class LyricsLine(
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
    val agent: String? = null,
    val isBackground: Boolean = false,
    val isInstrumental: Boolean = false,
    val endTimeEstimated: Boolean = false,
)

data class LyricsDocument(
    val rawLyrics: String,
    val syncType: LyricsSyncType,
    val format: LyricsFormat,
    val lines: List<LyricsLine>,
    val durationMs: Long? = null,
) {
    val isEmpty: Boolean
        get() = lines.isEmpty()

    val isInstrumentalOnly: Boolean
        get() = lines.isNotEmpty() && lines.all(LyricsLine::isInstrumental)
}

enum class LyricsInterludeAnimationStyle {
    PULSE,
    BURST,
    RIPPLE,
}

sealed interface LyricsTimelineItem {
    val key: String
    val startTimeMs: Long
    val endTimeMs: Long

    data class Line(
        val lineIndex: Int,
        val line: LyricsLine,
        override val startTimeMs: Long,
        override val endTimeMs: Long,
    ) : LyricsTimelineItem {
        override val key: String = "line:$lineIndex:$startTimeMs:${line.text.hashCode()}"
    }

    data class Interlude(
        override val startTimeMs: Long,
        override val endTimeMs: Long,
        val style: LyricsInterludeAnimationStyle,
        val explicitMarker: Boolean,
    ) : LyricsTimelineItem {
        override val key: String = "interlude:$startTimeMs:$endTimeMs"
    }
}

data class LyricsTimeline(
    val items: List<LyricsTimelineItem>,
) {
    fun currentItemIndex(positionMs: Long, offsetMs: Long = 0L): Int {
        if (items.isEmpty()) return -1
        // A positive offset moves lyric timestamps later, matching the offset shown to users.
        val lyricPositionMs = positionMs - offsetMs
        var low = 0
        var high = items.lastIndex
        var answer = -1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (items[middle].startTimeMs <= lyricPositionMs) {
                answer = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return answer.takeIf { index ->
            index >= 0 && lyricPositionMs < items[index].endTimeMs
        } ?: -1
    }
}

