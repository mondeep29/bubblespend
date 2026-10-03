package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class ExpenseEntryValidationTest {
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val categoryId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val methodId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val usd = CurrencyCode.of("USD")

    @Test
    fun rejectsInvalidExactAmounts() {
        for (amount in listOf("0", "-1.00", "1.001", "92233720368547758.08")) {
            val result =
                ExpenseEntryValidator.validate(
                    draft(amount = amount),
                    usd,
                    Locale.US,
                    clock,
                )
            assertTrue("Expected amount error for $amount", result is ExpenseValidation.Invalid)
            assertTrue((result as ExpenseValidation.Invalid).fields.contains(ExpenseField.AMOUNT))
        }
        val valid = ExpenseEntryValidator.validate(draft(amount = "12.34"), usd, Locale.US, clock)
        assertTrue(valid is ExpenseValidation.Valid)
        assertEquals(Money(1234L, usd), (valid as ExpenseValidation.Valid).expense.amount)
    }

    @Test
    fun rejectsMissingFutureAndOverLimitFields() {
        val invalid =
            ExpenseEntryValidator.validate(
                draft(
                    category = null,
                    method = null,
                    occurredAt = now.plusSeconds(1),
                    merchant = "x".repeat(101),
                    note = "x".repeat(501),
                ),
                usd,
                Locale.US,
                clock,
            )
        assertTrue(invalid is ExpenseValidation.Invalid)
        assertEquals(
            setOf(
                ExpenseField.CATEGORY,
                ExpenseField.PAYMENT_METHOD,
                ExpenseField.OCCURRED_AT,
                ExpenseField.MERCHANT,
                ExpenseField.NOTE,
            ),
            (invalid as ExpenseValidation.Invalid).fields,
        )
        val valid =
            ExpenseEntryValidator.validate(
                draft(merchant = "  Shop  ", note = "  Memo  "),
                usd,
                Locale.US,
                clock,
            ) as ExpenseValidation.Valid
        assertEquals("Shop", valid.expense.merchant)
        assertEquals("Memo", valid.expense.note)
    }

    private fun draft(
        amount: String = "1.00",
        category: UUID? = categoryId,
        method: UUID? = methodId,
        occurredAt: Instant = now,
        merchant: String = "",
        note: String = "",
    ) = ExpenseDraft(amount, category, method, occurredAt, merchant, note)
}
