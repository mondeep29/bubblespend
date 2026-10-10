package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class ExpenseEntryControllerAcceptanceTest {
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val usd = CurrencyCode.of("USD")
    private val categoryId = uuid(1)
    private val paymentMethodId = uuid(2)

    @Test
    fun saveRequiresExplicitCurrencyConfirmationAndParsesExactMinorUnits() =
        runBlocking {
            val currencyRepository = FakeBaseCurrencyRepository()
            val expenseRepository = RecordingExpenseRepository(now)
            val controller = controller(expenseRepository, currencyRepository)

            val blocked = controller.submit(draft("12.34"))

            assertTrue(blocked is ExpenseEntrySubmission.CurrencyConfirmationRequired)
            assertEquals(0, expenseRepository.createCalls)

            currencyRepository.confirmCurrency(usd)
            val saved = controller.submit(draft("12.34")) as ExpenseEntrySubmission.Saved

            assertEquals(1234L, saved.expense.amount.minorUnits)
            assertEquals(usd, saved.expense.amount.currency)
            assertEquals(1, expenseRepository.records.size)
        }

    @Test
    fun rapidRepeatedSubmitPersistsAtMostOnceForTheLogicalSubmission() =
        runBlocking {
            val expenseRepository = RecordingExpenseRepository(now).apply { pauseCreates = true }
            val controller = controller(expenseRepository, FakeBaseCurrencyRepository(usd))
            val input = draft("12.34")

            val first = async { controller.submit(input) }
            expenseRepository.createStarted.await()
            val repeated = async { controller.submit(input) }
            yield()

            assertEquals(1, expenseRepository.createCalls)
            expenseRepository.allowCreate.complete(Unit)
            assertTrue(first.await() is ExpenseEntrySubmission.Saved)
            repeated.await()

            assertEquals(1, expenseRepository.createCalls)
            assertEquals(1, expenseRepository.records.size)
        }

    @Test
    fun storageFailurePreservesDraftAndRetryUsesTheSameSubmissionToken() =
        runBlocking {
            val expenseRepository = RecordingExpenseRepository(now).apply { failNextCreate = true }
            val controller = controller(expenseRepository, FakeBaseCurrencyRepository(usd))
            val input = draft("12.34", merchant = "  Corner shop  ", note = "  Lunch  ")

            val failed = controller.submit(input) as ExpenseEntrySubmission.RecoverableFailure

            assertSame(input, failed.draft)
            assertEquals(0, expenseRepository.records.size)

            val retried = controller.submit(input)

            assertTrue(retried is ExpenseEntrySubmission.Saved)
            assertEquals(2, expenseRepository.receivedTokens.size)
            assertEquals(expenseRepository.receivedTokens.first(), expenseRepository.receivedTokens.last())
            assertEquals(1, expenseRepository.records.size)
        }

    private fun controller(
        expenseRepository: ExpenseRepository,
        currencyRepository: BaseCurrencyRepository,
    ): ExpenseEntryController =
        ExpenseEntryController(
            expenseRepository = expenseRepository,
            baseCurrencyRepository = currencyRepository,
            clock = clock,
            locale = Locale.US,
            uuidGenerator = UuidGenerator { uuid(500) },
        )

    private fun draft(
        amount: String,
        merchant: String = "",
        note: String = "",
    ): ExpenseDraft =
        ExpenseDraft(
            amountText = amount,
            categoryId = categoryId,
            paymentMethodId = paymentMethodId,
            occurredAt = now,
            merchant = merchant,
            note = note,
        )

    private class FakeBaseCurrencyRepository(
        private var confirmed: CurrencyCode? = null,
    ) : BaseCurrencyRepository {
        override suspend fun loadConfirmedCurrency(): StoreResult<CurrencyCode?> = StoreResult.Success(confirmed)

        override suspend fun confirmCurrency(currency: CurrencyCode): StoreResult<CurrencyCode> {
            confirmed = currency
            return StoreResult.Success(currency)
        }
    }

    private class RecordingExpenseRepository(
        private val now: Instant,
    ) : ExpenseRepository {
        val records = mutableListOf<Expense>()
        val receivedTokens = mutableListOf<UUID>()
        val createStarted = CompletableDeferred<Unit>()
        val allowCreate = CompletableDeferred<Unit>()
        var pauseCreates: Boolean = false
        var failNextCreate: Boolean = false
        var createCalls: Int = 0
            private set

        override suspend fun createExpense(
            expense: NewExpense,
            token: UUID,
        ): StoreResult<Expense> {
            createCalls += 1
            receivedTokens += token
            createStarted.complete(Unit)
            if (pauseCreates) allowCreate.await()
            if (failNextCreate) {
                failNextCreate = false
                return StoreResult.StorageFailure()
            }
            records.singleOrNull()?.let { existing -> return StoreResult.Success(existing) }
            val saved =
                Expense(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000100"),
                    amount = expense.amount,
                    occurredAt = expense.occurredAt,
                    categoryId = expense.categoryId,
                    paymentMethodId = expense.paymentMethodId,
                    merchant = expense.merchant,
                    note = expense.note,
                    createdAt = now,
                    updatedAt = now,
                )
            records += saved
            return StoreResult.Success(saved)
        }

        override suspend fun editExpense(
            id: UUID,
            expectedVersion: Long,
            expense: NewExpense,
            token: UUID,
        ): StoreResult<Expense> = error("Edit is outside this acceptance slice")

        override suspend fun deleteExpense(
            id: UUID,
            expectedVersion: Long,
            token: UUID,
        ): StoreResult<Expense> = error("Delete is outside this acceptance slice")

        override suspend fun undoDelete(
            id: UUID,
            expectedVersion: Long,
            token: UUID,
        ): StoreResult<Expense> = error("Undo is outside this acceptance slice")

        override suspend fun getExpense(id: UUID): Expense? = records.singleOrNull { it.id == id }

        override fun observeExpenses(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Expense>> = emptyFlow()
    }

    private fun uuid(last: Long): UUID = UUID.fromString("00000000-0000-0000-0000-%012d".format(last))
}
