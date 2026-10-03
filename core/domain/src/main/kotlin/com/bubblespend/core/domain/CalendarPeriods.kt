package com.bubblespend.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** UTC interval with an inclusive start and exclusive end. */
data class UtcInterval(
    val start: Instant,
    val endExclusive: Instant,
) {
    init {
        require(start < endExclusive) { "Interval end must be after its start" }
    }

    fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(endExclusive)
}

/** Calendar interval factories that resolve each local boundary independently. */
object CalendarPeriods {
    fun day(
        date: LocalDate,
        zone: ZoneId,
    ): UtcInterval = interval(date, date.plusDays(1), zone)

    fun priorDay(
        date: LocalDate,
        zone: ZoneId,
    ): UtcInterval = day(date.minusDays(1), zone)

    fun week(
        date: LocalDate,
        zone: ZoneId,
        weekStart: DayOfWeek,
    ): UtcInterval {
        val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
        return interval(start, start.plusWeeks(1), zone)
    }

    fun priorWeek(
        date: LocalDate,
        zone: ZoneId,
        weekStart: DayOfWeek,
    ): UtcInterval = week(date.with(TemporalAdjusters.previousOrSame(weekStart)).minusWeeks(1), zone, weekStart)

    fun month(
        month: YearMonth,
        zone: ZoneId,
    ): UtcInterval = interval(month.atDay(1), month.plusMonths(1).atDay(1), zone)

    fun priorMonth(
        month: YearMonth,
        zone: ZoneId,
    ): UtcInterval = month(month.minusMonths(1), zone)

    fun year(
        year: Year,
        zone: ZoneId,
    ): UtcInterval = interval(year.atDay(1), year.plusYears(1).atDay(1), zone)

    fun priorYear(
        year: Year,
        zone: ZoneId,
    ): UtcInterval = year(year.minusYears(1), zone)

    private fun interval(
        start: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId,
    ): UtcInterval = UtcInterval(start.atStartOfDay(zone).toInstant(), endExclusive.atStartOfDay(zone).toInstant())
}
