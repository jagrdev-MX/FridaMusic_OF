package com.jagr.fridamusic.notifications

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.jagr.fridamusic.constants.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NotificationPolicyTest {
    private val now = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val candidate = NotificationCandidate("song:x", NotificationCandidateType.RECOMMENDED_SONG, 80,
        "Song", "x", NotificationContentType.SONG, deepLink = "fridamusic://history", source = "test", reason = "test", timestamp = now)
    @Test fun deliveryBlocksSlotAndContent() {
        val preferences = mutablePreferencesOf(MusicRecommendationNotificationsKey to true)
        assertNull(NotificationPolicy.preflightSkipReason(preferences, now, NotificationSlot.MORNING))
        NotificationPolicy.recordDelivery(preferences, candidate, "template", now, NotificationSlot.MORNING)
        assertEquals("slot_limit:MORNING", NotificationPolicy.preflightSkipReason(preferences, now, NotificationSlot.MORNING))
        assertNull(NotificationPolicy.select(listOf(candidate), preferences, now + 1000, NotificationSlot.AFTERNOON).candidate)
        assertEquals(3, NotificationPolicy.MAX_NOTIFICATIONS_PER_DAY)
    }
    @Test fun graceExpiresWithoutRecordingDelivery() {
        val preferences = mutablePreferencesOf(MusicRecommendationNotificationsKey to true, LastAppOpenAtKey to now)
        assertEquals("recent_use_grace_period", NotificationPolicy.select(listOf(candidate), preferences, now, NotificationSlot.MORNING).skipReason)
        assertEquals(candidate, NotificationPolicy.select(listOf(candidate), preferences,
            now + NotificationPolicy.RECENT_USE_GRACE_PERIOD_MILLIS, NotificationSlot.MORNING).candidate)
        assertNull(preferences[NotificationSlotLastDeliveredKey])
    }
}
