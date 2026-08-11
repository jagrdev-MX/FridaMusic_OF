package com.music.jiosaavn

object SaavnMatcher {

    private const val VARIANT_PENALTY = -40

    private val nonAlphanumeric = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    private val variantMarkerPhrases = listOf(
        "karaoke",
        "instrumental",
        "minus one",
        "cover",
        "remix",
        "live",
        "acoustic",
        "remaster",
        "remastered",
        "lo-fi",
        "lofi",
        "sped up",
        "slowed",
        "reverb",
        "nightcore",
        "demo",
        "radio edit",
        "extended mix"
    )

    private val variantMarkers = variantMarkerPhrases.map { " ${normalize(it)} " }

    /**
     * Penalizes a version mismatch in either direction while preserving explicit variant searches.
     */
    fun variantPenalty(ytTitle: String, candidateName: String): Int {
        val normalizedYtTitle = searchable(ytTitle)
        val normalizedCandidateName = searchable(candidateName)
        var penalty = 0

        for (marker in variantMarkers) {
            if (normalizedYtTitle.contains(marker) != normalizedCandidateName.contains(marker)) {
                penalty += VARIANT_PENALTY
            }
        }

        return penalty
    }

    private fun searchable(value: String): String = " ${normalize(value)} "

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(nonAlphanumeric, " ")
            .trim()
            .replace(whitespace, " ")
}
