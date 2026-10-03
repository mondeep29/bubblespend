package com.bubblespend.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class DeterministicInputsAcceptanceTest {
    @Test
    fun `supplied clock and id generator return the caller selected values`() {
        val instant = Instant.parse("2024-03-10T12:34:56Z")
        val expectedId = UUID.fromString("14f3c5a2-f5d7-4a5b-9116-e2709c747142")
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        val ids = UuidGenerator { expectedId }

        assertEquals(instant, clock.instant())
        assertEquals(instant, clock.instant())
        assertEquals(expectedId, ids.next())
        assertEquals(expectedId, ids.next())
    }

    @Test
    fun `timezone changes only boundaries explicitly recalculated with that zone`() {
        val date = LocalDate.of(2024, 3, 10)
        val utcInterval = CalendarPeriods.day(date, ZoneId.of("UTC"))
        val newYorkInterval = CalendarPeriods.day(date, ZoneId.of("America/New_York"))
        val usd = CurrencyCode.of("USD")
        val localeMoney = LocalizedMoneyParser.parse("1,234.56", usd, Locale.US)

        assertNotEquals(utcInterval.start, newYorkInterval.start)
        assertEquals(Instant.parse("2024-03-10T00:00:00Z"), utcInterval.start)
        assertEquals(Instant.parse("2024-03-10T05:00:00Z"), newYorkInterval.start)
        assertEquals(Money(123456L, usd), localeMoney)
    }
}
