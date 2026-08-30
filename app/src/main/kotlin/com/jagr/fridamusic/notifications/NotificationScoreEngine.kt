package com.jagr.fridamusic.notifications

import androidx.datastore.preferences.core.Preferences
import com.jagr.fridamusic.constants.NotificationCategoryLastSentKey
import com.jagr.fridamusic.constants.RecentNotificationContentHistoryKey
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal enum class NotificationSlot(
    val startHour: Int,
    val endHourExclusive: Int,
) {
    MORNING(8, 12),
    AFTERNOON(13, 18),
    EVENING(19, 23),
    ;

    fun contains(now: Long): Boolean {
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        return hour in startHour until endHourExclusive
    }

    companion object {
        fun fromInput(value: String?): NotificationSlot? = value?.let { name ->
            runCatching { valueOf(name) }.getOrNull()
        }

        fun forTime(now: Long): NotificationSlot? = entries.firstOrNull { it.contains(now) }
    }
}

internal object NotificationScoreEngine {
    fun score(
        candidate: NotificationCandidate,
        slot: NotificationSlot,
        preferences: Preferences,
        now: Long,
    ): Int {
        val categoryHistory = parseCategoryHistory(preferences[NotificationCategoryLastSentKey])
        val contentHistory = parseContentHistory(preferences[RecentNotificationContentHistoryKey])
        var score = candidate.priority
        score += timeOfDayAffinity(candidate.type, slot)
        score += relevance(candidate)
        score += freshness(candidate, now)

        val categoryLastSent = categoryHistory[candidate.type] ?: 0L
        if (categoryLastSent > now - TimeUnit.DAYS.toMillis(7)) score -= 18
        if (contentHistory.any { it.contentType == candidate.contentType && it.contentId == candidate.contentId }) {
            score -= 120
        }
        val artistKey = normalize(candidate.artistName)
        if (artistKey.isNotBlank() && contentHistory.take(8).any { it.artistKey == artistKey }) {
            score -= 24
        }
        score += Math.floorMod(candidate.id.hashCode(), 7)
        return score
    }

    private fun timeOfDayAffinity(type: NotificationCandidateType, slot: NotificationSlot): Int = when (slot) {
        NotificationSlot.MORNING -> when (type) {
            NotificationCandidateType.DAILY_DISCOVER -> 28
            NotificationCandidateType.NEW_RELEASE -> 25
            NotificationCandidateType.RECOMMENDED_SONG -> 20
            else -> 0
        }
        NotificationSlot.AFTERNOON -> when (type) {
            NotificationCandidateType.PLAYLIST -> 27
            NotificationCandidateType.ARTIST -> 24
            NotificationCandidateType.ALBUM -> 22
            NotificationCandidateType.RECOMMENDED_SONG -> 18
            else -> 0
        }
        NotificationSlot.EVENING -> when (type) {
            NotificationCandidateType.KEEP_LISTENING -> 28
            NotificationCandidateType.FORGOTTEN_FAVORITE -> 25
            NotificationCandidateType.RETENTION -> 18
            else -> 0
        }
    }

    private fun relevance(candidate: NotificationCandidate): Int = when {
        candidate.source.contains("followed_artist") -> 22
        candidate.source.contains("liked_") -> 18
        candidate.source.contains("local_recent_history") -> 14
        candidate.source.contains("forgotten") -> 12
        candidate.type == NotificationCandidateType.RECAP_AVAILABLE -> 26
        else -> 6
    }

    private fun freshness(candidate: NotificationCandidate, now: Long): Int {
        if (candidate.timestamp <= 0L || candidate.timestamp > now) return 0
        val age = now - candidate.timestamp
        return when {
            age <= TimeUnit.DAYS.toMillis(7) -> 16
            age <= TimeUnit.DAYS.toMillis(30) -> 10
            age <= TimeUnit.DAYS.toMillis(90) -> 4
            else -> 0
        }
    }

    private fun parseCategoryHistory(value: String?): Map<NotificationCandidateType, Long> =
        value.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            val type = parts.getOrNull(0)?.let { runCatching { NotificationCandidateType.valueOf(it) }.getOrNull() }
            val timestamp = parts.getOrNull(1)?.toLongOrNull()
            if (type == null || timestamp == null) null else type to timestamp
        }.toMap()

    private fun parseContentHistory(value: String?): List<ScoreHistoryEntry> =
        value.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 4)
            val type = parts.getOrNull(0)?.let { runCatching { NotificationContentType.valueOf(it) }.getOrNull() }
            val contentId = parts.getOrNull(1)
            val timestamp = parts.getOrNull(2)?.toLongOrNull()
            if (type == null || contentId.isNullOrBlank() || timestamp == null) null
            else ScoreHistoryEntry(type, contentId, normalize(parts.getOrNull(3).orEmpty()))
        }.toList()

    private fun normalize(value: String): String = value.trim().lowercase().replace('|', '_').replace('\n', '_')

    private data class ScoreHistoryEntry(
        val contentType: NotificationContentType,
        val contentId: String,
        val artistKey: String,
    )
}
