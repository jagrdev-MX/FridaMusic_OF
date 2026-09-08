package com.jagr.fridamusic.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class NotificationTimingTest {
    @Test fun preservesCurrentSlot() {
        val now = ZonedDateTime.parse("2026-09-07T10:15:00-06:00[America/Mexico_City]")
        assertEquals(now, NotificationTiming.next(NotificationSlot.MORNING, now))
        assertEquals(now.withHour(8).withMinute(0), NotificationTiming.next(NotificationSlot.MORNING, now.withHour(7)))
    }
    @Test fun graceIsReevaluatedToday() {
        val now = ZonedDateTime.parse("2026-09-07T08:00:00-06:00[America/Mexico_City]")
        assertEquals(now.plusMinutes(30), NotificationTiming.retry(NotificationSlot.MORNING, now, now.plusMinutes(20)))
        assertEquals(now.plusMinutes(50), NotificationTiming.retry(NotificationSlot.MORNING, now, now.plusMinutes(50)))
    }
    @Test fun expiredSlotAndDstUseNextLocalMorning() {
        val now = ZonedDateTime.parse("2026-03-07T12:00:00-05:00[America/New_York]")
        val expected = ZonedDateTime.parse("2026-03-08T08:00:00-04:00[America/New_York]")
        assertEquals(expected, NotificationTiming.next(NotificationSlot.MORNING, now))
        assertEquals(expected, NotificationTiming.retry(NotificationSlot.MORNING, now.minusMinutes(15)))
    }
}
