package com.jagr.fridamusic.notifications

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.jagr.fridamusic.constants.FridaReminderNotificationsKey
import com.jagr.fridamusic.constants.LastAppOpenAtKey
import com.jagr.fridamusic.constants.LastRecommendationNotificationAlbumIdKey
import com.jagr.fridamusic.constants.LastRecommendationNotificationAtKey
import com.jagr.fridamusic.constants.MusicRecommendationNotificationsKey
import com.jagr.fridamusic.constants.NewReleaseNotificationsKey
import com.jagr.fridamusic.constants.NotificationCategoryLastSentKey
import com.jagr.fridamusic.constants.RecentNotificationContentHistoryKey
import com.jagr.fridamusic.constants.RecentNotificationTemplateIdsKey
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal data class NotificationSelection(
    val candidate: NotificationCandidate? = null,
    val skipReason: String? = null,
)

internal object NotificationPolicy {
    const val MAX_NOTIFICATIONS_PER_DAY = 1
    const val QUIET_HOUR_START = 2
    const val QUIET_HOUR_END = 7
    const val RECENT_TEMPLATE_HISTORY_SIZE = 8
    const val RECENT_CONTENT_HISTORY_SIZE = 24

    val RECENT_USE_GRACE_PERIOD_MILLIS: Long = TimeUnit.MINUTES.toMillis(30)
    val RETENTION_MIN_INACTIVITY_MILLIS: Long = TimeUnit.DAYS.toMillis(3)

    object Priority {
        const val NEW_RELEASE = 100
        const val ARTIST = 90
        const val RECOMMENDATION = 85
        const val DAILY_DISCOVER = 80
        const val FORGOTTEN_FAVORITE = 75
        const val KEEP_LISTENING = 70
        const val ALBUM = 65
        const val PLAYLIST = 60
        const val RETENTION = 30
    }

    private val categoryCooldowns = mapOf(
        NotificationCandidateType.NEW_RELEASE to TimeUnit.DAYS.toMillis(1),
        NotificationCandidateType.RECOMMENDED_SONG to TimeUnit.DAYS.toMillis(2),
        NotificationCandidateType.DAILY_DISCOVER to TimeUnit.DAYS.toMillis(1),
        NotificationCandidateType.FORGOTTEN_FAVORITE to TimeUnit.DAYS.toMillis(4),
        NotificationCandidateType.KEEP_LISTENING to TimeUnit.DAYS.toMillis(2),
        NotificationCandidateType.ALBUM to TimeUnit.DAYS.toMillis(3),
        NotificationCandidateType.ARTIST to TimeUnit.DAYS.toMillis(3),
        NotificationCandidateType.PLAYLIST to TimeUnit.DAYS.toMillis(3),
        NotificationCandidateType.RETENTION to TimeUnit.DAYS.toMillis(3),
    )

    private val contentCooldowns = mapOf(
        NotificationContentType.SONG to TimeUnit.DAYS.toMillis(7),
        NotificationContentType.ALBUM to TimeUnit.DAYS.toMillis(6),
        NotificationContentType.ARTIST to TimeUnit.DAYS.toMillis(4),
        NotificationContentType.PLAYLIST to TimeUnit.DAYS.toMillis(5),
        NotificationContentType.HOME to TimeUnit.DAYS.toMillis(3),
    )

    fun preflightSkipReason(preferences: Preferences, now: Long): String? {
        if (preferences[MusicRecommendationNotificationsKey] != true) return "setting_disabled"

        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        if (hour in QUIET_HOUR_START until QUIET_HOUR_END) return "quiet_hours"

        val lastNotificationAt = preferences[LastRecommendationNotificationAtKey] ?: 0L
        if (lastNotificationAt > 0L && isSameLocalDay(lastNotificationAt, now)) return "daily_limit"

        return null
    }

    fun select(
        candidates: List<NotificationCandidate>,
        preferences: Preferences,
        now: Long,
        bypassTimingRules: Boolean = false,
    ): NotificationSelection {
        if (candidates.isEmpty()) return NotificationSelection(skipReason = "no_local_candidates")

        val categoryHistory = parseCategoryHistory(preferences[NotificationCategoryLastSentKey])
        val contentHistory = parseContentHistory(preferences[RecentNotificationContentHistoryKey])
        val lastOpenAt = preferences[LastAppOpenAtKey] ?: 0L
        var lastSkipReason = "all_candidates_filtered"

        candidates.sortedByDescending(NotificationCandidate::priority).forEach { candidate ->
            val skipReason = candidateSkipReason(
                candidate = candidate,
                preferences = preferences,
                categoryHistory = categoryHistory,
                contentHistory = contentHistory,
                lastOpenAt = lastOpenAt,
                now = now,
                bypassTimingRules = bypassTimingRules,
            )
            if (skipReason == null) return NotificationSelection(candidate = candidate)
            lastSkipReason = skipReason
        }

        return NotificationSelection(skipReason = lastSkipReason)
    }

    fun recentTemplateIds(preferences: Preferences): Set<String> =
        preferences[RecentNotificationTemplateIdsKey]
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .take(RECENT_TEMPLATE_HISTORY_SIZE)
            .toSet()

    fun recordDelivery(
        settings: MutablePreferences,
        candidate: NotificationCandidate,
        templateId: String,
        now: Long,
    ) {
        settings[LastRecommendationNotificationAtKey] = now
        if (candidate.contentType == NotificationContentType.ALBUM) {
            settings[LastRecommendationNotificationAlbumIdKey] = candidate.contentId
        }

        val categoryHistory = parseCategoryHistory(settings[NotificationCategoryLastSentKey]).toMutableMap()
        categoryHistory[candidate.type] = now
        settings[NotificationCategoryLastSentKey] = categoryHistory.entries.joinToString("\n") { (type, time) ->
            "${type.name}|$time"
        }

        val contentKey = candidate.contentHistoryKey()
        val updatedContentHistory = buildList {
            add(ContentHistoryEntry(candidate.contentType, sanitize(candidate.contentId), now))
            addAll(
                parseContentHistory(settings[RecentNotificationContentHistoryKey])
                    .filterNot { it.key == contentKey },
            )
        }.take(RECENT_CONTENT_HISTORY_SIZE)
        settings[RecentNotificationContentHistoryKey] = updatedContentHistory.joinToString("\n") { entry ->
            "${entry.contentType.name}|${entry.contentId}|${entry.timestamp}"
        }

        val updatedTemplates = buildList {
            add(templateId)
            addAll(
                settings[RecentNotificationTemplateIdsKey]
                    .orEmpty()
                    .lineSequence()
                    .filter { it.isNotBlank() && it != templateId },
            )
        }.take(RECENT_TEMPLATE_HISTORY_SIZE)
        settings[RecentNotificationTemplateIdsKey] = updatedTemplates.joinToString("\n")
    }

    private fun candidateSkipReason(
        candidate: NotificationCandidate,
        preferences: Preferences,
        categoryHistory: Map<NotificationCandidateType, Long>,
        contentHistory: List<ContentHistoryEntry>,
        lastOpenAt: Long,
        now: Long,
        bypassTimingRules: Boolean,
    ): String? {
        if (!candidate.isValid) return "invalid_candidate"
        if (
            candidate.type == NotificationCandidateType.NEW_RELEASE &&
            preferences[NewReleaseNotificationsKey] == false
        ) return "release_setting_disabled"
        if (
            candidate.type == NotificationCandidateType.RETENTION &&
            preferences[FridaReminderNotificationsKey] == false
        ) return "reminder_setting_disabled"

        if (!bypassTimingRules) {
            val usedRecently = lastOpenAt > 0L && lastOpenAt <= now &&
                now - lastOpenAt < RECENT_USE_GRACE_PERIOD_MILLIS
            if (usedRecently && candidate.type != NotificationCandidateType.NEW_RELEASE) {
                return "recent_use_grace_period"
            }
            if (
                candidate.type == NotificationCandidateType.RETENTION &&
                (lastOpenAt <= 0L || now - lastOpenAt < RETENTION_MIN_INACTIVITY_MILLIS)
            ) return "retention_not_due"

            val categoryLastSent = categoryHistory[candidate.type] ?: 0L
            val categoryCooldown = categoryCooldowns.getValue(candidate.type)
            if (categoryLastSent > now - categoryCooldown) return "category_cooldown:${candidate.type.name}"

            val contentLastSent = contentHistory
                .firstOrNull { entry -> entry.key == candidate.contentHistoryKey() }
                ?.timestamp ?: 0L
            val contentCooldown = contentCooldowns.getValue(candidate.contentType)
            if (contentLastSent > now - contentCooldown) {
                return "content_cooldown:${candidate.contentType.name}"
            }

            val legacyAlbumId = preferences[LastRecommendationNotificationAlbumIdKey]
            if (candidate.contentType == NotificationContentType.ALBUM && candidate.contentId == legacyAlbumId) {
                return "legacy_album_dedup"
            }
        }

        return null
    }

    private fun parseCategoryHistory(value: String?): Map<NotificationCandidateType, Long> =
        value.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            val type = parts.getOrNull(0)?.let { name ->
                runCatching { NotificationCandidateType.valueOf(name) }.getOrNull()
            }
            val timestamp = parts.getOrNull(1)?.toLongOrNull()
            if (type == null || timestamp == null) null else type to timestamp
        }.toMap()

    private fun parseContentHistory(value: String?): List<ContentHistoryEntry> =
        value.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 3)
            val type = parts.getOrNull(0)?.let { name ->
                runCatching { NotificationContentType.valueOf(name) }.getOrNull()
            }
            val contentId = parts.getOrNull(1)
            val timestamp = parts.getOrNull(2)?.toLongOrNull()
            if (type == null || contentId.isNullOrBlank() || timestamp == null) {
                null
            } else {
                ContentHistoryEntry(type, contentId, timestamp)
            }
        }.toList()

    private fun NotificationCandidate.contentHistoryKey(): String =
        "${contentType.name}|${sanitize(contentId)}"

    private fun isSameLocalDay(first: Long, second: Long): Boolean {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(first).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(second).atZone(zone).toLocalDate()
    }

    private fun sanitize(value: String): String = value.replace('|', '_').replace('\n', '_')

    private data class ContentHistoryEntry(
        val contentType: NotificationContentType,
        val contentId: String,
        val timestamp: Long,
    ) {
        val key: String
            get() = "${contentType.name}|$contentId"
    }
}
