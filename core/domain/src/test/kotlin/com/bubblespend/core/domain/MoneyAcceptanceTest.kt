package com.bubblespend.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class MoneyAcceptanceTest {
    @Test
    fun `money retains supported currency and signed minor units`() {
        val usd = CurrencyCode.of("USD")

        assertEquals(Money(0L, usd), Money(0L, usd))
        assertEquals(-123L, Money(-123L, usd).minorUnits)
        assertEquals("USD", usd.value)
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode.of("US") }
    }

    @Test
    fun `addition and subtraction preserve currency and reject overflow`() {
        val usd = CurrencyCode.of("USD")

        assertEquals(Money(17L, usd), Money(10L, usd) + Money(7L, usd))
        assertEquals(Money(3L, usd), Money(10L, usd) - Money(7L, usd))
        assertThrows(ArithmeticException::class.java) {
            Money(Long.MAX_VALUE, usd) + Money(1L, usd)
        }
        assertThrows(ArithmeticException::class.java) {
            Money(Long.MIN_VALUE, usd) - Money(1L, usd)
        }
    }

    @Test
    fun `currency mismatch is rejected before arithmetic`() {
        val usd = Money(Long.MAX_VALUE, CurrencyCode.of("USD"))
        val eur = Money(1L, CurrencyCode.of("EUR"))

        assertThrows(IllegalArgumentException::class.java) { usd + eur }
        assertThrows(IllegalArgumentException::class.java) { usd - eur }
    }

    @Test
    fun `localized numeric parser uses supplied locale and currency exactly`() {
        val usd = CurrencyCode.of("USD")
        val eur = CurrencyCode.of("EUR")

        assertEquals(Money(123456L, usd), LocalizedMoneyParser.parse("1,234.56", usd, Locale.US))
        assertEquals(
            Money(123456L, eur),
            LocalizedMoneyParser.parse("1.234,56", eur, Locale.GERMANY),
        )
        assertEquals(Money(0L, usd), LocalizedMoneyParser.parse("0.00", usd, Locale.US))
        assertEquals(Money(-125L, usd), LocalizedMoneyParser.parse("-1.25", usd, Locale.US))
        assertEquals(Money(123L, usd), LocalizedMoneyParser.parse("1.230", usd, Locale.US))
    }

    @Test
    fun `localized numeric parser rejects rounding malformed grouping and overflow`() {
        val usd = CurrencyCode.of("USD")
        val jpy = CurrencyCode.of("JPY")

        listOf("1.234", "1,23.45", "1,234.56 trailing", "12x", "1,,234.00").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                LocalizedMoneyParser.parse(input, usd, Locale.US)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalizedMoneyParser.parse("1.5", jpy, Locale.US)
        }
        assertThrows(ArithmeticException::class.java) {
            LocalizedMoneyParser.parse("92233720368547758.08", usd, Locale.US)
        }
    }
}
