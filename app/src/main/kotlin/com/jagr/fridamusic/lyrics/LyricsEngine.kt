package com.jagr.fridamusic.lyrics

import com.jagr.fridamusic.betterlyrics.TTMLParser
import com.jagr.fridamusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import java.util.Locale
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToInt

object LyricsEngine {
    const val INTERLUDE_MIN_GAP_MS = 6_000L
    const val LINE_INTERLUDE_HOLD_MS = 3_200L
    const val MAX_AUTO_SYNC_OFFSET_MS = 10_000L
    private const val MAX_AUTO_SYNC_DEVIATION_MS = 450L
    private const val MIN_AUTO_SYNC_MATCHES = 3

    private val instrumentalMarkers = setOf(
        "instrumental",
        "instrumental break",
        "music",
        "interlude",
        "solo",
        "guitar solo",
    )

    fun normalize(rawLyrics: String, durationSeconds: Int = 0): LyricsDocument {
        val durationMs = durationSeconds.takeIf { it > 0 }?.times(1_000L)
        val cleaned = cleanRawLyrics(rawLyrics)
        if (cleaned.isBlank() || cleaned == LYRICS_NOT_FOUND) {
            return LyricsDocument(
                rawLyrics = rawLyrics,
                syncType = LyricsSyncType.PLAIN,
                format = LyricsFormat.PLAIN_TEXT,
                lines = emptyList(),
                durationMs = durationMs,
            )
        }

        if (isTtml(cleaned)) {
            val parsed = TTMLParser.parseTTML(cleaned)
            if (parsed.isNotEmpty()) {
                val parsedLines = parsed.flatMap { line -> listOf(line) + line.backgroundLines }
                val lines = parsedLines.mapIndexed { index, line ->
                    val words = line.words.mapNotNull { word ->
                        word.text.takeIf(String::isNotBlank)?.let {
                            LyricsWord(
                                text = it,
                                startTimeMs = (word.startTime * 1_000.0).toLong(),
                                endTimeMs = (word.endTime * 1_000.0).toLong()
                                    .coerceAtLeast((word.startTime * 1_000.0).toLong()),
                                isBackground = line.isBackground,
                            )
                        }
                    }
                    val startTimeMs = (line.startTime * 1_000.0).toLong()
                    val nextStartTimeMs = parsedLines.getOrNull(index + 1)?.startTime?.times(1_000.0)?.toLong()
                    val preciseEndTimeMs = words.maxOfOrNull(LyricsWord::endTimeMs)?.takeIf { it > startTimeMs }
                    LyricsLine(
                        startTimeMs = startTimeMs,
                        endTimeMs = preciseEndTimeMs ?: minOf(
                            nextStartTimeMs ?: durationMs ?: (startTimeMs + LINE_INTERLUDE_HOLD_MS),
                            startTimeMs + LINE_INTERLUDE_HOLD_MS,
                        ),
                        text = line.text,
                        words = words,
                        agent = line.agent,
                        isBackground = line.isBackground,
                        isInstrumental = isInstrumentalMarker(line.text),
                        endTimeEstimated = preciseEndTimeMs == null,
                    )
                }.sortedBy { it.startTimeMs }
                return LyricsDocument(
                    rawLyrics = rawLyrics,
                    syncType = if (lines.any { it.words.isNotEmpty() }) LyricsSyncType.WORD_SYNCED else LyricsSyncType.LINE_SYNCED,
                    format = LyricsFormat.TTML,
                    lines = lines,
                    durationMs = durationMs,
                )
            }
        }

        val entries = LyricsUtils.parseLyrics(cleaned)
        if (entries.isNotEmpty()) {
            val lines = entries.mapIndexed { index, entry ->
                val startMs = entry.time
                val nextStartMs = entries.getOrNull(index + 1)?.time?.takeIf { it > startMs }
                val words = entry.words.orEmpty().mapNotNull { word ->
                    word.text.takeIf(String::isNotBlank)?.let {
                        val wordStartMs = (word.startTime * 1_000.0).toLong()
                        LyricsWord(
                            text = it,
                            startTimeMs = wordStartMs,
                            endTimeMs = (word.endTime * 1_000.0).toLong().coerceAtLeast(wordStartMs),
                            isBackground = entry.isBackground,
                        )
                    }
                }
                val preciseEndMs = words.maxOfOrNull(LyricsWord::endTimeMs)?.takeIf { it > startMs }
                val estimatedEndMs = minOf(
                    nextStartMs ?: durationMs ?: (startMs + LINE_INTERLUDE_HOLD_MS),
                    startMs + LINE_INTERLUDE_HOLD_MS,
                ).coerceAtLeast(startMs)
                LyricsLine(
                    startTimeMs = startMs,
                    endTimeMs = preciseEndMs ?: estimatedEndMs,
                    text = entry.text,
                    words = words,
                    agent = entry.agent,
                    isBackground = entry.isBackground,
                    isInstrumental = isInstrumentalMarker(entry.text),
                    endTimeEstimated = preciseEndMs == null,
                )
            }
            val hasWords = lines.any { line -> line.words.any { it.endTimeMs >= it.startTimeMs } }
            return LyricsDocument(
                rawLyrics = rawLyrics,
                syncType = if (hasWords) LyricsSyncType.WORD_SYNCED else LyricsSyncType.LINE_SYNCED,
                format = if (hasWords) LyricsFormat.ENHANCED_LRC else LyricsFormat.LRC,
                lines = lines,
                durationMs = durationMs,
            )
        }

        val plainLines = cleaned.lines()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isLrcMetadataLine)
            .map { text ->
                LyricsLine(
                    startTimeMs = null,
                    endTimeMs = null,
                    text = text,
                    isInstrumental = isInstrumentalMarker(text),
                )
            }
        return LyricsDocument(
            rawLyrics = rawLyrics,
            syncType = LyricsSyncType.PLAIN,
            format = LyricsFormat.PLAIN_TEXT,
            lines = plainLines,
            durationMs = durationMs,
        )
    }

    fun buildTimeline(document: LyricsDocument): LyricsTimeline {
        if (document.isInstrumentalOnly && document.durationMs != null && document.durationMs > 0L) {
            return LyricsTimeline(listOf(interlude(0L, document.durationMs, explicitMarker = true)))
        }
        if (document.syncType == LyricsSyncType.PLAIN || document.lines.isEmpty()) {
            return LyricsTimeline(emptyList())
        }

        val timedLines = document.lines.mapIndexedNotNull { originalIndex, line ->
            line.takeIf { it.startTimeMs != null }?.let { originalIndex to it }
        }.sortedBy { it.second.startTimeMs }
        if (timedLines.isEmpty()) return LyricsTimeline(emptyList())

        val items = mutableListOf<LyricsTimelineItem>()
        val firstStart = timedLines.first().second.startTimeMs ?: 0L
        if (firstStart >= INTERLUDE_MIN_GAP_MS && !timedLines.first().second.isInstrumental) {
            items += interlude(0L, firstStart, explicitMarker = false)
        }

        timedLines.forEachIndexed { index, (originalIndex, line) ->
            val startMs = line.startTimeMs ?: return@forEachIndexed
            val nextStartMs = timedLines.getOrNull(index + 1)?.second?.startTimeMs ?: document.durationMs
            if (line.isInstrumental) {
                val endMs = (nextStartMs ?: line.endTimeMs ?: startMs).coerceAtLeast(startMs)
                if (endMs > startMs) items += interlude(startMs, endMs, explicitMarker = true)
                return@forEachIndexed
            }

            val lineEndMs = (nextStartMs ?: document.durationMs ?: line.endTimeMs ?: Long.MAX_VALUE)
                .coerceAtLeast(startMs + 1L)
            items += LyricsTimelineItem.Line(
                lineIndex = originalIndex,
                line = line,
                startTimeMs = startMs,
                endTimeMs = lineEndMs,
            )

            val gapStartMs = line.endTimeMs ?: startMs
            val gapEndMs = nextStartMs ?: return@forEachIndexed
            val nextIsMarker = timedLines.getOrNull(index + 1)?.second?.isInstrumental == true
            if (!nextIsMarker && gapEndMs - gapStartMs >= INTERLUDE_MIN_GAP_MS) {
                items += interlude(gapStartMs, gapEndMs, explicitMarker = false)
            }
        }

        return LyricsTimeline(items.sortedBy { it.startTimeMs })
    }

    fun score(
        document: LyricsDocument,
        providerName: String,
        songDurationSeconds: Int,
        preferredProvider: Boolean = false,
        metadataMatch: LyricsMetadataMatch = LyricsMetadataMatch(),
    ): LyricsScore {
        if (document.isEmpty) return LyricsScore(Int.MIN_VALUE, listOf("empty"))

        var total = when (document.syncType) {
            LyricsSyncType.WORD_SYNCED -> 70
            LyricsSyncType.LINE_SYNCED -> 45
            LyricsSyncType.PLAIN -> 20
        }
        val reasons = mutableListOf(document.syncType.name)

        val usefulLines = document.lines.count { it.text.isNotBlank() }
        total += when {
            usefulLines < 2 -> -25
            usefulLines < 5 -> -5
            usefulLines <= 300 -> 10
            else -> -10
        }

        if (document.syncType != LyricsSyncType.PLAIN && songDurationSeconds > 0) {
            val durationMs = songDurationSeconds * 1_000L
            val lyricEndMs = document.lines.maxOfOrNull { line ->
                line.words.maxOfOrNull(LyricsWord::endTimeMs) ?: line.startTimeMs ?: 0L
            } ?: 0L
            val coverage = lyricEndMs.toDouble() / durationMs.toDouble()
            when {
                coverage < 0.35 && songDurationSeconds >= 90 -> {
                    total -= 55
                    reasons += "very_low_coverage"
                }
                coverage < 0.60 && songDurationSeconds >= 90 -> {
                    total -= 25
                    reasons += "low_coverage"
                }
                coverage in 0.75..1.20 -> total += 15
                coverage > 1.35 -> {
                    total -= 40
                    reasons += "duration_overrun"
                }
            }
        }

        if (document.syncType == LyricsSyncType.WORD_SYNCED) {
            val words = document.lines.flatMap(LyricsLine::words)
            val validWords = words.count { it.text.isNotBlank() && it.endTimeMs >= it.startTimeMs }
            val validRatio = validWords.toDouble() / words.size.coerceAtLeast(1)
            if (validRatio >= 0.9 && words.size >= 10) total += 15 else total -= 20
        }

        total += sourceWeight(providerName)
        if (preferredProvider) total += 5
        total += metadataMatch.score(reasons)
        return LyricsScore(total, reasons)
    }

    fun matchMetadata(
        requestedTitle: String,
        requestedArtist: String,
        requestedAlbum: String?,
        requestedDurationSeconds: Int,
        candidate: LyricsCandidatePayload,
    ): LyricsMetadataMatch {
        val embedded = extractEmbeddedMetadata(candidate.lyrics)
        val candidateTitle = candidate.title ?: embedded["ti"]
        val candidateArtist = candidate.artist ?: embedded["ar"]
        val candidateAlbum = candidate.album ?: embedded["al"]
        val candidateDuration = candidate.durationSeconds
            ?: embedded["length"]?.let(::parseDurationSeconds)

        val requestedTitleIdentity = titleIdentity(requestedTitle)
        val candidateTitleIdentity = candidateTitle?.let(::titleIdentity)
        val titleSimilarity = candidateTitleIdentity?.let {
            stringSimilarity(requestedTitleIdentity.base, it.base)
        }
        val qualifierConflict = candidateTitleIdentity?.let {
            requestedTitleIdentity.qualifiers != it.qualifiers &&
                (requestedTitleIdentity.qualifiers.isNotEmpty() || it.qualifiers.isNotEmpty())
        } ?: false
        val artistSimilarity = candidateArtist?.let {
            stringSimilarity(primaryArtist(requestedArtist), primaryArtist(it))
        }
        val albumSimilarity = candidateAlbum
            ?.takeIf { !requestedAlbum.isNullOrBlank() }
            ?.let { stringSimilarity(normalizeIdentity(requestedAlbum.orEmpty()), normalizeIdentity(it)) }
        val durationDeviation = candidateDuration
            ?.takeIf { it > 0 && requestedDurationSeconds > 0 }
            ?.let { abs(it - requestedDurationSeconds) }
        val identityRejected = qualifierConflict ||
            (titleSimilarity != null && titleSimilarity < MIN_TITLE_IDENTITY) ||
            (artistSimilarity != null && artistSimilarity < MIN_ARTIST_IDENTITY) ||
            (durationDeviation != null && durationDeviation > MAX_DURATION_DEVIATION_SECONDS)

        return LyricsMetadataMatch(
            titleSimilarity = titleSimilarity,
            artistSimilarity = artistSimilarity,
            albumSimilarity = albumSimilarity,
            durationDeviationSeconds = durationDeviation,
            qualifierConflict = qualifierConflict,
            identityRejected = identityRejected,
        )
    }

    fun estimateAutoSyncOffsetMs(
        selected: LyricsDocument,
        exactPlaybackReference: LyricsDocument,
    ): Int? {
        if (selected.syncType == LyricsSyncType.PLAIN || exactPlaybackReference.syncType == LyricsSyncType.PLAIN) {
            return null
        }
        val referenceTimes = exactPlaybackReference.lines
            .mapNotNull { line ->
                val key = normalizeMatchText(line.text)
                val time = line.startTimeMs
                if (key.length >= 4 && time != null) key to time else null
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }

        val differences = selected.lines.mapNotNull { line ->
            val selectedTime = line.startTimeMs ?: return@mapNotNull null
            referenceTimes[normalizeMatchText(line.text)]?.minus(selectedTime)
        }.sorted()
        if (differences.size < MIN_AUTO_SYNC_MATCHES) return null

        val median = differences[differences.size / 2]
        if (abs(median) > MAX_AUTO_SYNC_OFFSET_MS) return null
        val deviations = differences.map { abs(it - median) }.sorted()
        val medianDeviation = deviations[deviations.size / 2]
        if (medianDeviation > MAX_AUTO_SYNC_DEVIATION_MS) return null
        return (median / 50.0).roundToInt() * 50
    }

    private fun interlude(startMs: Long, endMs: Long, explicitMarker: Boolean): LyricsTimelineItem.Interlude {
        val styles = LyricsInterludeAnimationStyle.entries
        val style = styles[((startMs / 1_000L) % styles.size).toInt()]
        return LyricsTimelineItem.Interlude(startMs, endMs, style, explicitMarker)
    }

    private fun sourceWeight(providerName: String): Int = when (providerName) {
        "BetterLyrics" -> 14
        "YouLyPlus" -> 12
        "SimpMusic" -> 11
        "YouTube Subtitle" -> 10
        "LrcLib" -> 9
        "Kugou" -> 7
        "YouTube Music" -> 2
        "Paxsenix", "Unison" -> 0
        else -> 0
    }

    private fun LyricsMetadataMatch.score(reasons: MutableList<String>): Int {
        if (identityRejected) {
            reasons += "identity_rejected"
            return IDENTITY_REJECTION_SCORE
        }
        var score = 0
        titleSimilarity?.let {
            if (it < 0.72) {
                score -= 70
                reasons += "title_mismatch"
            } else if (it >= 0.9) score += 12
        }
        artistSimilarity?.let {
            if (it < 0.60) {
                score -= 65
                reasons += "artist_mismatch"
            } else if (it >= 0.85) score += 10
        }
        if (qualifierConflict) {
            score -= 140
            reasons += "version_mismatch"
        }
        albumSimilarity?.let { if (it >= 0.85) score += 4 }
        durationDeviationSeconds?.let {
            when {
                it <= 2 -> score += 10
                it <= 6 -> score += 4
                it > 15 -> {
                    score -= 55
                    reasons += "duration_mismatch"
                }
            }
        }
        return score
    }

    private fun titleIdentity(title: String): TitleIdentity {
        val normalized = normalizeIdentity(title)
        val qualifiers = versionQualifiers.mapNotNullTo(linkedSetOf()) { (qualifier, patterns) ->
            qualifier.takeIf { patterns.any { pattern -> containsPhrase(normalized, pattern) } }
        }
        val qualifierPatterns = versionQualifiers.values.flatten()
            .sortedByDescending(String::length)
            .joinToString("|") { Regex.escape(it) }
        val base = normalized
            .replace(Regex("\\b(?:$qualifierPatterns)\\b"), " ")
            .replace(Regex("\\b(?:official|audio|video|lyrics?|visualizer|hd|hq|4k)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return TitleIdentity(base = base.ifBlank { normalized }, qualifiers = qualifiers)
    }

    private fun primaryArtist(artist: String): String = normalizeIdentity(artist)
        .split(Regex("\\s+(?:feat(?:uring)?|ft|with|x|and|y)\\s+|\\s*[,&;/]\\s*"), limit = 2)
        .firstOrNull()
        .orEmpty()
        .trim()

    private fun normalizeIdentity(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun stringSimilarity(first: String, second: String): Double {
        if (first == second) return 1.0
        if (first.isBlank() || second.isBlank()) return 0.0
        val maxLength = maxOf(first.length, second.length)
        var previous = IntArray(second.length + 1) { it }
        first.forEachIndexed { firstIndex, firstChar ->
            val current = IntArray(second.length + 1)
            current[0] = firstIndex + 1
            second.forEachIndexed { secondIndex, secondChar ->
                current[secondIndex + 1] = minOf(
                    current[secondIndex] + 1,
                    previous[secondIndex + 1] + 1,
                    previous[secondIndex] + if (firstChar == secondChar) 0 else 1,
                )
            }
            previous = current
        }
        val editSimilarity = 1.0 - previous.last().toDouble() / maxLength
        val firstTokens = first.split(' ').filter(String::isNotBlank).toSet()
        val secondTokens = second.split(' ').filter(String::isNotBlank).toSet()
        val tokenSimilarity = if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            0.0
        } else {
            firstTokens.intersect(secondTokens).size.toDouble() / firstTokens.union(secondTokens).size
        }
        return maxOf(editSimilarity, tokenSimilarity)
    }

    private fun extractEmbeddedMetadata(lyrics: String): Map<String, String> =
        Regex("^\\[(ti|ar|al|length):([^]]+)]$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .findAll(lyrics)
            .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2].trim() }

    private fun parseDurationSeconds(value: String): Int? {
        value.trim().toIntOrNull()?.let { return it }
        val parts = value.trim().split(':').mapNotNull(String::toIntOrNull)
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3_600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private fun containsPhrase(value: String, phrase: String): Boolean =
        Regex("(?:^| )${Regex.escape(phrase)}(?: |$)").containsMatchIn(value)

    private fun cleanRawLyrics(rawLyrics: String): String = rawLyrics
        .trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    private fun isTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("<") && (
            trimmed.contains("<tt", ignoreCase = true) ||
                trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
            )
    }

    private fun isInstrumentalMarker(text: String): Boolean {
        val normalized = text.trim()
            .trim('[', ']', '(', ')', '{', '}')
            .lowercase(Locale.ROOT)
            .replace(Regex("[♪♫♩♬]+"), "")
            .trim()
        return normalized in instrumentalMarkers ||
            (normalized.isBlank() && text.any { it in "♪♫♩♬" })
    }

    private fun isLrcMetadataLine(text: String): Boolean =
        Regex("^\\[(ar|al|ti|au|by|length|re|ve|offset):.*]$", RegexOption.IGNORE_CASE).matches(text)

    private fun normalizeMatchText(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private data class TitleIdentity(
        val base: String,
        val qualifiers: Set<String>,
    )

    private const val MIN_TITLE_IDENTITY = 0.72
    private const val MIN_ARTIST_IDENTITY = 0.60
    private const val MAX_DURATION_DEVIATION_SECONDS = 20
    private const val IDENTITY_REJECTION_SCORE = -200

    private val versionQualifiers = linkedMapOf(
        "live" to listOf("live", "en vivo", "directo", "concert", "sesion", "session"),
        "remix" to listOf("remix", "remezcla", "mix"),
        "remastered" to listOf("remastered", "remaster", "remasterizado", "remasterizada"),
        "acoustic" to listOf("acoustic", "acustico", "acustica", "unplugged"),
        "instrumental" to listOf("instrumental"),
        "karaoke" to listOf("karaoke"),
        "sped_up" to listOf("sped up", "speed up", "acelerado", "acelerada"),
        "slowed" to listOf("slowed", "slowed down", "ralentizado", "ralentizada"),
        "nightcore" to listOf("nightcore"),
        "radio_edit" to listOf("radio edit", "radio version", "edicion de radio"),
        "extended" to listOf("extended", "extended mix", "version extendida"),
        "demo" to listOf("demo"),
        "cover" to listOf("cover"),
        "version" to listOf("version"),
    )
}

data class LyricsScore(
    val total: Int,
    val reasons: List<String>,
)

data class LyricsMetadataMatch(
    val titleSimilarity: Double? = null,
    val artistSimilarity: Double? = null,
    val albumSimilarity: Double? = null,
    val durationDeviationSeconds: Int? = null,
    val qualifierConflict: Boolean = false,
    val identityRejected: Boolean = false,
)
