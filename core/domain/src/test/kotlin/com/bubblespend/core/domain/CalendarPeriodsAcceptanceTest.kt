package com.bubblespend.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

class CalendarPeriodsAcceptanceTest {
    private val utc = ZoneId.of("UTC")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `day interval includes start and excludes independently resolved next midnight`() {
        val date = LocalDate.of(2025, 1, 1)
        val interval = CalendarPeriods.day(date, utc)

        assertEquals(date.atStartOfDay(utc).toInstant(), interval.start)
        assertEquals(date.plusDays(1).atStartOfDay(utc).toInstant(), interval.endExclusive)
        assertTrue(interval.contains(interval.start))
        assertFalse(interval.contains(interval.endExclusive))
    }

    @Test
    fun `month and year intervals honor calendar turnover and leap day`() {
        val february = CalendarPeriods.month(YearMonth.of(2024, 2), utc)
        val january = CalendarPeriods.month(YearMonth.of(2025, 1), utc)
        val leapDay = CalendarPeriods.day(LocalDate.of(2024, 2, 29), utc)
        val year = CalendarPeriods.year(Year.of(2024), utc)

        assertEquals(Instant.parse("2024-02-01T00:00:00Z"), february.start)
        assertEquals(Instant.parse("2024-03-01T00:00:00Z"), february.endExclusive)
        assertEquals(Instant.parse("2024-12-31T00:00:00Z"), january.start.minus(Duration.ofDays(1)))
        assertEquals(Instant.parse("2024-03-01T00:00:00Z"), leapDay.endExclusive)
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), year.endExclusive)
    }

    @Test
    fun `day intervals resolve both local boundaries across daylight saving changes`() {
        val spring = CalendarPeriods.day(LocalDate.of(2024, 3, 10), newYork)
        val fall = CalendarPeriods.day(LocalDate.of(2024, 11, 3), newYork)

        assertEquals(23L, Duration.between(spring.start, spring.endExclusive).toHours())
        assertEquals(25L, Duration.between(fall.start, fall.endExclusive).toHours())
        assertEquals(
            LocalDate.of(2024, 3, 11).atStartOfDay(newYork).toInstant(),
            spring.endExclusive,
        )
        assertEquals(
            LocalDate.of(2024, 11, 4).atStartOfDay(newYork).toInstant(),
            fall.endExclusive,
        )
    }

    @Test
    fun `prior day week month and year are adjacent calendar intervals`() {
        val mondayStart = DayOfWeek.MONDAY
        val currentDay = CalendarPeriods.day(LocalDate.of(2024, 3, 1), utc)
        val priorDay = CalendarPeriods.priorDay(LocalDate.of(2024, 3, 1), utc)
        val currentWeek = CalendarPeriods.week(LocalDate.of(2025, 1, 1), utc, mondayStart)
        val priorWeek = CalendarPeriods.priorWeek(LocalDate.of(2025, 1, 1), utc, mondayStart)
        val currentMonth = CalendarPeriods.month(YearMonth.of(2024, 3), utc)
        val priorMonth = CalendarPeriods.priorMonth(YearMonth.of(2024, 3), utc)
        val currentYear = CalendarPeriods.year(Year.of(2025), utc)
        val priorYear = CalendarPeriods.priorYear(Year.of(2025), utc)

        assertEquals(priorDay.endExclusive, currentDay.start)
        assertEquals(priorWeek.endExclusive, currentWeek.start)
        assertEquals(29L, Duration.between(priorMonth.start, priorMonth.endExclusive).toDays())
        assertEquals(priorMonth.endExclusive, currentMonth.start)
        assertEquals(priorYear.endExclusive, currentYear.start)
        assertEquals(Instant.parse("2024-12-30T00:00:00Z"), currentWeek.start)
    }
}
