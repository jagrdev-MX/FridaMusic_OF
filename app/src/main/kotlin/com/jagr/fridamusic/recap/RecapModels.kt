package com.jagr.fridamusic.recap

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class RecapPeriodType {
    WEEK,
    MONTH,
    YEAR,
}

data class RecapRange(
    val type: RecapPeriodType,
    val offset: Int,
    val startMillis: Long,
    val endMillis: Long,
    val label: String,
)

object RecapPeriodResolver {
    fun resolve(
        type: RecapPeriodType,
        offset: Int,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): RecapRange {
        val safeOffset = offset.coerceAtLeast(0)
        val today = now.atZone(zoneId).toLocalDate()
        val start = when (type) {
            RecapPeriodType.WEEK -> today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(safeOffset.toLong())
            RecapPeriodType.MONTH -> today.withDayOfMonth(1).minusMonths(safeOffset.toLong())
            RecapPeriodType.YEAR -> today.withDayOfYear(1).minusYears(safeOffset.toLong())
        }
        val nextStart = when (type) {
            RecapPeriodType.WEEK -> start.plusWeeks(1)
            RecapPeriodType.MONTH -> start.plusMonths(1)
            RecapPeriodType.YEAR -> start.plusYears(1)
        }
        return RecapRange(
            type = type,
            offset = safeOffset,
            startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMillis = nextStart.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L,
            label = label(type, start, nextStart.minusDays(1), locale),
        )
    }

    private fun label(
        type: RecapPeriodType,
        start: LocalDate,
        end: LocalDate,
        locale: Locale,
    ): String = when (type) {
        RecapPeriodType.WEEK -> {
            val day = DateTimeFormatter.ofPattern("d", locale)
            val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
            if (start.month == end.month && start.year == end.year) {
                "${start.format(day)}–${end.format(day)} ${end.format(monthYear)}"
            } else {
                val shortDate = DateTimeFormatter.ofPattern("d MMM", locale)
                "${start.format(shortDate)}–${end.format(shortDate)} ${end.year}"
            }
        }
        RecapPeriodType.MONTH -> start.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        RecapPeriodType.YEAR -> start.year.toString()
    }
}

data class RecapPersonality(
    val name: String,
    val description: String,
)

object RecapPersonalityEngine {
    fun evaluate(
        totalPlayTimeMs: Long,
        totalPlays: Int,
        uniqueSongs: Int,
        uniqueArtists: Int,
        uniqueAlbums: Int,
        topSongPlays: Int,
    ): RecapPersonality? {
        if (totalPlays <= 0) return null

        val topRatio = topSongPlays.toDouble() / totalPlays.toDouble()
        val repeatRatio = 1.0 - (uniqueSongs.toDouble() / totalPlays.toDouble()).coerceIn(0.0, 1.0)
        val hours = totalPlayTimeMs / 3_600_000.0
        return when {
            topRatio >= 0.30 || repeatRatio >= 0.62 -> RecapPersonality(
                name = "Fiel a sus favoritos",
                description = "Tus canciones esenciales volvieron una y otra vez.",
            )
            hours >= 20.0 -> RecapPersonality(
                name = "Maratonista musical",
                description = "La música fue tu compañía constante durante este periodo.",
            )
            uniqueArtists >= 12 && uniqueArtists * 2 >= totalPlays -> RecapPersonality(
                name = "Explorador sonoro",
                description = "Recorriste muchas voces sin quedarte demasiado tiempo en un solo lugar.",
            )
            uniqueAlbums >= 10 && uniqueAlbums * 2 >= uniqueSongs -> RecapPersonality(
                name = "Coleccionista de álbumes",
                description = "Tu escucha recorrió una gran variedad de álbumes.",
            )
            else -> RecapPersonality(
                name = "Ritmo propio",
                description = "Equilibraste descubrimientos y favoritos a tu manera.",
            )
        }
    }
}
