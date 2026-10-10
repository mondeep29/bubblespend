package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

class ExpenseSnapshotTest {
    private val usd = CurrencyCode.of("USD")
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Test
    fun totalsAndCategoryDrillDownExcludeDeletedRecordsAndReconcile() {
        val first = expense(1, 105, 10)
        val second = expense(2, 205, 10)
        val third = expense(3, 500, 20)
        val deleted = expense(4, 999, 10).copy(deletedAt = now)
        val snapshot = ExpenseSnapshot.from(listOf(first, deleted, second, third), usd)

        assertEquals(BigInteger.valueOf(810), snapshot.totalMinorUnits)
        assertEquals(3, snapshot.expenses.size)
        assertEquals(listOf(uuid(20), uuid(10)), snapshot.categories.map { it.categoryId })
        assertEquals(listOf(first, second), snapshot.categories.last().expenses)
        assertEquals(snapshot.totalMinorUnits, snapshot.categories.fold(BigInteger.ZERO) { total, group -> total + group.totalMinorUnits })
    }

    @Test
    fun totalsStayExactBeyondLongRange() {
        val snapshot = ExpenseSnapshot.from(listOf(expense(1, Long.MAX_VALUE, 10), expense(2, Long.MAX_VALUE, 10)), usd)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), snapshot.totalMinorUnits)
    }

    @Test
    fun emptyDataHasZeroTotalAndNoCategoryBubbles() {
        val snapshot = ExpenseSnapshot.from(emptyList(), usd)
        assertEquals(BigInteger.ZERO, snapshot.totalMinorUnits)
        assertTrue(snapshot.categories.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompatibleCurrenciesCannotBeCombined() {
        ExpenseSnapshot.from(listOf(expense(1, 100, 10).copy(amount = Money(100, CurrencyCode.of("EUR")))), usd)
    }

    private fun expense(
        id: Long,
        amount: Long,
        category: Long,
    ) = Expense(uuid(id), Money(amount, usd), now, uuid(category), uuid(99), "", "", now, now)

    private fun uuid(value: Long) = UUID(0, value)
}
