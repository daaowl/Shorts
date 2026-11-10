package com.goat.kharcha.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A budget cycle: from the start day of one month up to (not including) the same day next month. */
data class CycleRange(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt()
    operator fun contains(d: LocalDate): Boolean = !d.isBefore(start) && d.isBefore(end)
    fun previous(): CycleRange = CycleRange(start.minusMonths(1), start)

    fun label(): String {
        val m = DateTimeFormatter.ofPattern("MMMM", Locale.US)
        if (start.dayOfMonth == 1) return start.format(m)
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.US)
        return "${start.format(f)} – ${end.minusDays(1).format(f)}"
    }

    fun shortLabel(): String {
        val m = DateTimeFormatter.ofPattern("MMM", Locale.US)
        return if (start.dayOfMonth == 1) start.format(m) else end.minusDays(1).format(m)
    }
}

object Cycle {
    fun of(date: LocalDate, startDay: Int): CycleRange {
        val sd = startDay.coerceIn(1, 28)
        val start = if (date.dayOfMonth >= sd) date.withDayOfMonth(sd) else date.minusMonths(1).withDayOfMonth(sd)
        return CycleRange(start, start.plusMonths(1))
    }

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startMillis(d: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, d MMM" else "d MMM yyyy", Locale.US))
    }
}
