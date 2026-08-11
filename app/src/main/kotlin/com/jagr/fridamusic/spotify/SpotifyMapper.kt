/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.jagr.fridamusic.spotify

import com.jagr.fridamusic.spotify.models.SpotifyPlaylist
import com.jagr.fridamusic.spotify.models.SpotifyTrack
import java.text.Normalizer

/**
 * Utility object for creating search queries from Spotify track data.
 * The actual mapping to Metrolist MediaMetadata is done in the app module
 * where MediaMetadata class is available.
 */
object SpotifyMapper {

    // Pre-compiled regex patterns for title normalization (avoids re-creation on each call)
    private val FEAT_PATTERN = Regex("(?:\\(|\\[)\\s*(feat\\.?|ft\\.?|featuring)\\b.*?(?:\\)|\\])", RegexOption.IGNORE_CASE)
    private val NON_ALNUM_PATTERN = Regex("[^\\p{L}\\p{N}\\s]")
    private val MULTI_SPACE_PATTERN = Regex("\\s+")

    private val strictVariantPatterns = linkedMapOf(
        "live" to Regex("\\blive\\b"),
        "remix" to Regex("\\b(remix|rmx)\\b"),
        "sped_up" to Regex("\\bsped\\s+up\\b"),
        "slowed" to Regex("\\b(slowed|slowed\\s+down)\\b"),
        "cover" to Regex("\\bcover\\b"),
        "karaoke" to Regex("\\b(karaoke|minus\\s+one)\\b"),
        "instrumental" to Regex("\\binstrumental\\b"),
        "acoustic" to Regex("\\bacoustic\\b"),
        "nightcore" to Regex("\\bnightcore\\b"),
    )
    private val softVariantPatterns = linkedMapOf(
        "lyrics" to Regex("\\b(lyric|lyrics|lyric\\s+video)\\b"),
        "remaster" to Regex("\\b(remaster|remastered)\\b"),
        "edit" to Regex("\\b(radio\\s+edit|extended\\s+(mix|version))\\b"),
    )
    private val neutralEditorialPatterns = listOf(
        Regex("\\bofficial\\s+(audio|video)\\b"),
        Regex("\\bmusic\\s+video\\b"),
    )
    private val genericArtistTokens = setOf(
        "the", "and", "with", "feat", "featuring", "ft",
        "el", "la", "los", "las", "y", "de", "del",
    )

    private const val NORM_CACHE_MAX_SIZE = 256
    private const val EARLY_EXIT_THRESHOLD = 0.95
    private const val MIN_MATCH_SCORE = 0.72
    private const val MIN_TITLE_SCORE = 0.68
    private const val MIN_ARTIST_SCORE = 0.45

    /**
     * LRU cache for normalized strings. Avoids re-running 7 regex replacements
     * on the same Spotify title/artist across multiple candidate comparisons.
     * Bounded to [NORM_CACHE_MAX_SIZE] entries to limit memory usage.
     */
    private val normalizeCache = object : LinkedHashMap<String, String>(
        NORM_CACHE_MAX_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > NORM_CACHE_MAX_SIZE
    }

    /**
     * LRU cache for pre-computed bigram sets. Avoids re-creating Set<String>
     * on every stringSimilarity call for the same normalized string.
     */
    private val bigramCache = object : LinkedHashMap<String, Set<String>>(
        NORM_CACHE_MAX_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>?): Boolean =
            size > NORM_CACHE_MAX_SIZE
    }

    /**
     * Pre-computed data for one side of a match comparison.
     * Created once per Spotify track and reused across all candidates.
     */
    data class PrecomputedTrack(
        val normalizedTitle: String,
        val titleBigrams: Set<String>,
        val normalizedArtist: String,
        val artistBigrams: Set<String>,
        val durationMs: Int,
        val strictVariants: Set<String>,
        val softVariants: Set<String>,
    )

    data class MatchEvaluation(
        val score: Double,
        val titleScore: Double,
        val artistScore: Double,
        val durationScore: Double,
        val variantsCompatible: Boolean,
        val durationCompatible: Boolean,
    ) {
        val isAcceptable: Boolean
            get() = variantsCompatible &&
                durationCompatible &&
                titleScore >= SpotifyMapper.MIN_TITLE_SCORE &&
                artistScore >= SpotifyMapper.MIN_ARTIST_SCORE &&
                score >= SpotifyMapper.MIN_MATCH_SCORE
    }

    /**
     * Builds a YouTube search query from a Spotify track.
     * The query is optimized for finding the matching song on YouTube Music.
     */
    fun buildSearchQuery(track: SpotifyTrack): String {
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val title = track.name
        return if (artist.isEmpty()) title else "$artist $title"
    }

    /**
     * Returns the best thumbnail URL from a Spotify playlist, preferring medium resolution.
     */
    fun getPlaylistThumbnail(playlist: SpotifyPlaylist): String? {
        return playlist.images.let { images ->
            // Prefer 300x300 or similar medium size, fallback to first
            images.firstOrNull { it.width in 200..400 }?.url
                ?: images.firstOrNull()?.url
        }
    }

    /**
     * Returns the best thumbnail URL from a Spotify track's album art.
     */
    fun getTrackThumbnail(track: SpotifyTrack): String? {
        return track.album?.images?.let { images ->
            images.firstOrNull { it.width in 200..400 }?.url
                ?: images.firstOrNull()?.url
        }
    }

    /**
     * Pre-computes normalized title/artist and their bigrams for a Spotify track.
     * Call once before scoring against multiple candidates to avoid redundant work.
     */
    fun precompute(
        title: String,
        artist: String,
        durationMs: Int,
    ): PrecomputedTrack {
        val normTitle = cachedNormalize(title, stripVariants = true)
        val normArtist = cachedNormalize(artist, stripVariants = false)
        return PrecomputedTrack(
            normalizedTitle = normTitle,
            titleBigrams = cachedBigrams(normTitle),
            normalizedArtist = normArtist,
            artistBigrams = cachedBigrams(normArtist),
            durationMs = durationMs,
            strictVariants = variants(title, strictVariantPatterns),
            softVariants = variants(title, softVariantPatterns),
        )
    }

    /**
     * Computes a match confidence score (0.0 - 1.0) between a Spotify track and
     * a candidate result based on title, artist, and duration similarity.
     */
    fun matchScore(
        spotifyTitle: String,
        spotifyArtist: String,
        spotifyDurationMs: Int,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): Double = evaluateMatch(
        spotifyTitle = spotifyTitle,
        spotifyArtist = spotifyArtist,
        spotifyDurationMs = spotifyDurationMs,
        candidateTitle = candidateTitle,
        candidateArtist = candidateArtist,
        candidateDurationSec = candidateDurationSec,
    ).score

    fun evaluateMatch(
        spotifyTitle: String,
        spotifyArtist: String,
        spotifyDurationMs: Int,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): MatchEvaluation = evaluatePrecomputed(
        precomputed = precompute(spotifyTitle, spotifyArtist, spotifyDurationMs),
        candidateTitle = candidateTitle,
        candidateArtist = candidateArtist,
        candidateDurationSec = candidateDurationSec,
    )

    /**
     * Scores a candidate against pre-computed Spotify track data.
     * This is the fast path: normalization and bigrams for the Spotify side
     * are computed once and reused across all candidates.
     */
    fun matchScorePrecomputed(
        precomputed: PrecomputedTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): Double = evaluatePrecomputed(
        precomputed = precomputed,
        candidateTitle = candidateTitle,
        candidateArtist = candidateArtist,
        candidateDurationSec = candidateDurationSec,
    ).score

    fun evaluatePrecomputed(
        precomputed: PrecomputedTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): MatchEvaluation {
        val normCandidateTitle = cachedNormalize(candidateTitle, stripVariants = true)
        val normCandidateArtist = cachedNormalize(candidateArtist, stripVariants = false)

        val titleScore = bigramSimilarity(
            precomputed.normalizedTitle, precomputed.titleBigrams,
            normCandidateTitle, cachedBigrams(normCandidateTitle),
        )
        val artistScore = artistSimilarity(
            precomputed.normalizedArtist, precomputed.artistBigrams,
            normCandidateArtist, cachedBigrams(normCandidateArtist),
        )

        val durationScore = durationScore(precomputed.durationMs, candidateDurationSec)
        val candidateStrictVariants = variants(candidateTitle, strictVariantPatterns)
        val candidateSoftVariants = variants(candidateTitle, softVariantPatterns)
        val softVariantDifference =
            (precomputed.softVariants union candidateSoftVariants) -
                (precomputed.softVariants intersect candidateSoftVariants)
        val score = (
            titleScore * 0.45 + artistScore * 0.35 + durationScore * 0.20 -
                softVariantDifference.size * 0.08
            ).coerceIn(0.0, 1.0)

        val durationCompatible = if (candidateDurationSec == null || precomputed.durationMs <= 0) {
            true
        } else {
            kotlin.math.abs(precomputed.durationMs / 1000 - candidateDurationSec) <= 15
        }
        return MatchEvaluation(
            score = score,
            titleScore = titleScore,
            artistScore = artistScore,
            durationScore = durationScore,
            variantsCompatible = precomputed.strictVariants == candidateStrictVariants,
            durationCompatible = durationCompatible,
        )
    }

    /** Threshold above which we consider a match good enough to skip remaining candidates. */
    fun earlyExitThreshold(): Double = EARLY_EXIT_THRESHOLD

    private fun durationScore(spotifyDurationMs: Int, candidateDurationSec: Int?): Double {
        if (candidateDurationSec == null || spotifyDurationMs <= 0) return 0.5
        val diff = kotlin.math.abs(spotifyDurationMs / 1000 - candidateDurationSec)
        return when {
            diff <= 2 -> 1.0
            diff <= 5 -> 0.8
            diff <= 10 -> 0.5
            diff <= 30 -> 0.2
            else -> 0.0
        }
    }

    /**
     * Normalizes a title for comparison, with LRU caching.
     */
    private fun cachedNormalize(text: String, stripVariants: Boolean): String {
        val cacheKey = "${if (stripVariants) 't' else 'a'}:$text"
        normalizeCache[cacheKey]?.let { return it }
        val normalized = normalize(text, stripVariants)
        normalizeCache[cacheKey] = normalized
        return normalized
    }

    /**
     * Returns cached bigrams for a normalized string.
     */
    private fun cachedBigrams(normalized: String): Set<String> {
        bigramCache[normalized]?.let { return it }
        val bigrams = if (normalized.length < 2) emptySet() else normalized.windowed(2).toSet()
        bigramCache[normalized] = bigrams
        return bigrams
    }

    private fun normalize(text: String, stripVariants: Boolean): String {
        var normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(FEAT_PATTERN, "")
        val normalizedWithVariants = normalized
            .replace(NON_ALNUM_PATTERN, " ")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()
        if (stripVariants) {
            (strictVariantPatterns.values + softVariantPatterns.values + neutralEditorialPatterns)
                .forEach { pattern -> normalized = normalized.replace(pattern, " ") }
        }
        val result = normalized
            .replace(NON_ALNUM_PATTERN, " ")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()
        return result.ifBlank { normalizedWithVariants }
    }

    private fun variants(text: String, patterns: Map<String, Regex>): Set<String> {
        val searchable = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(NON_ALNUM_PATTERN, " ")
            .replace(MULTI_SPACE_PATTERN, " ")
        return patterns.mapNotNullTo(linkedSetOf()) { (name, pattern) ->
            name.takeIf { pattern.containsMatchIn(searchable) }
        }
    }

    /**
     * Dice coefficient using pre-computed bigram sets.
     */
    private fun bigramSimilarity(
        a: String, bigramsA: Set<String>,
        b: String, bigramsB: Set<String>,
    ): Double {
        if (a == b) return 1.0
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0
        val intersection = bigramsA.count { it in bigramsB }
        return (2.0 * intersection) / (bigramsA.size + bigramsB.size)
    }

    private fun artistSimilarity(
        a: String,
        bigramsA: Set<String>,
        b: String,
        bigramsB: Set<String>,
    ): Double {
        val dice = bigramSimilarity(a, bigramsA, b, bigramsB)
        val tokensA = a.split(' ')
            .filter { it.isNotBlank() && it !in genericArtistTokens }
            .toSet()
        val tokensB = b.split(' ')
            .filter { it.isNotBlank() && it !in genericArtistTokens }
            .toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return dice
        val coverage = tokensA.intersect(tokensB).size.toDouble() / minOf(tokensA.size, tokensB.size)
        return maxOf(dice, coverage)
    }
}
