package com.jagr.fridamusic.notifications

internal enum class NotificationCandidateType {
    NEW_RELEASE,
    RECOMMENDED_SONG,
    DAILY_DISCOVER,
    FORGOTTEN_FAVORITE,
    KEEP_LISTENING,
    ALBUM,
    ARTIST,
    PLAYLIST,
    RETENTION,
}

internal enum class NotificationContentType {
    SONG,
    ARTIST,
    ALBUM,
    PLAYLIST,
    HOME,
}

internal data class NotificationCandidate(
    val id: String,
    val type: NotificationCandidateType,
    val priority: Int,
    val title: String,
    val contentId: String,
    val contentType: NotificationContentType,
    val artistName: String = "",
    val artworkUrl: String? = null,
    val deepLink: String,
    val source: String,
    val reason: String,
    val timestamp: Long,
) {
    val isValid: Boolean
        get() = id.isNotBlank() &&
            title.isNotBlank() &&
            contentId.isNotBlank() &&
            deepLink.startsWith("fridamusic://")
}
