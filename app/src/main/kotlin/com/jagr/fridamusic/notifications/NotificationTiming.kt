package com.jagr.fridamusic.notifications

import java.time.ZonedDateTime

internal object NotificationTiming {
    fun next(slot: NotificationSlot, earliest: ZonedDateTime): ZonedDateTime {
        val date = earliest.toLocalDate()
        val start = date.atTime(slot.startHour, 0).atZone(earliest.zone)
        val end = date.atTime(slot.endHourExclusive, 0).atZone(earliest.zone)
        return when {
            earliest.isBefore(start) -> start
            earliest.isBefore(end) -> earliest
            else -> date.plusDays(1).atTime(slot.startHour, 0).atZone(earliest.zone)
        }
    }

    fun retry(slot: NotificationSlot, now: ZonedDateTime, graceEnds: ZonedDateTime? = null): ZonedDateTime {
        val earliest = maxOf(now.plusMinutes(30), graceEnds ?: now)
        val end = now.toLocalDate().atTime(slot.endHourExclusive, 0).atZone(now.zone)
        return if (earliest.isBefore(end.minusMinutes(2))) next(slot, earliest)
        else now.toLocalDate().plusDays(1).atTime(slot.startHour, 0).atZone(now.zone)
    }
}
