package com.music.innertube.models

// Accept exact integers only. Compact values (1.2K), durations and unrelated
// subtitle numbers are not reliable song totals.
fun parsePlaylistSongCount(text: String?): Int? {
    val value = text?.trim() ?: return null
    val match = Regex("""^([0-9]+|[0-9]{1,3}(?:[, .\u00a0\u202f][0-9]{3})+)(?:\s+(?:songs?|tracks?|videos?|canciones|canción|temas?))?$""", RegexOption.IGNORE_CASE)
        .matchEntire(value) ?: return null
    return match.groupValues[1].filter(Char::isDigit).toIntOrNull()
}

fun Runs?.playlistSongCountText(): String? = this?.runs
    ?.filter { it.navigationEndpoint == null }
    ?.map { it.text.trim() }
    ?.firstOrNull { parsePlaylistSongCount(it) != null }
