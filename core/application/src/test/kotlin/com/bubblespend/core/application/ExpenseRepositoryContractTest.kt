package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.Money
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Behavioral contract shared with the Room adapter in the second BT-007 slice. */
class ExpenseRepositoryContractTest {
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val usd = CurrencyCode.of("USD")
    private val categoryId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val methodId = UUID.fromString("00000000-0000-0000-0000-000000000102")

    @Test
    fun repeatedSubmissionTokenCreatesOnce() =
        runBlocking {
            val store = store()
            val request = request()
            val token = uuid(201)
            val first = store.createExpense(request, token) as StoreResult.Success<Expense>
            val repeated = store.createExpense(request, token) as StoreResult.Success<Expense>
            assertEquals(first.value, repeated.value)
            assertEquals(1, store.activeExpenses(now.minusSeconds(1), now.plusSeconds(1)).size)
        }

    @Test
    fun expenseMutationTokenRejectsDifferentOperationWithoutFalseSuccess() =
        runBlocking {
            val store = store()
            val token = uuid(212)
            val created = (store.createExpense(request(), token) as StoreResult.Success<Expense>).value
            assertEquals(StoreResult.Success(created), store.createExpense(request(), token))
            assertTrue(store.deleteExpense(created.id, created.version, token) is StoreResult.Conflict)
            assertNull(store.getExpense(created.id)?.deletedAt)
            assertEquals(1, store.activeExpenses(now, now.plusSeconds(1)).size)
        }

    @Test
    fun rejectsArchivedSelectionsAtSave() =
        runBlocking {
            val store = store()
            val category = store.getCategory(categoryId)!!
            assertTrue(store.archiveCategory(category.id, category.version, uuid(211)) is StoreResult.Success)
            assertTrue(store.createExpense(request(), uuid(202)) is StoreResult.InactiveReference)
            assertTrue(store.activeExpenses(now.minusSeconds(1), now.plusSeconds(1)).isEmpty())
        }

    @Test
    fun editDeleteAndUndoPreserveIdentityAndGuardVersions() =
        runBlocking {
            val store = store()
            val original = (store.createExpense(request(), uuid(203)) as StoreResult.Success<Expense>).value
            val edited =
                (
                    store.editExpense(
                        original.id,
                        original.version,
                        request(amount = 999L),
                        uuid(204),
                    ) as StoreResult.Success<Expense>
                ).value
            assertEquals(original.id, edited.id)
            assertEquals(original.createdAt, edited.createdAt)
            assertEquals(original.version + 1, edited.version)
            assertEquals(999L, edited.amount.minorUnits)
            assertTrue(
                store.editExpense(original.id, original.version, request(amount = 1L), uuid(205))
                    is StoreResult.Conflict,
            )
            assertEquals(999L, store.getExpense(original.id)!!.amount.minorUnits)

            val deleted = (store.deleteExpense(edited.id, edited.version, uuid(206)) as StoreResult.Success<Expense>).value
            assertEquals(original.id, deleted.id)
            assertEquals(now, deleted.deletedAt)
            assertTrue(store.activeExpenses(now.minusSeconds(1), now.plusSeconds(1)).isEmpty())
            val restored = (store.undoDelete(deleted.id, deleted.version, uuid(207)) as StoreResult.Success<Expense>).value
            assertNull(restored.deletedAt)
            assertEquals(original.id, restored.id)
            assertEquals(listOf(original.id), store.activeExpenses(now.minusSeconds(1), now.plusSeconds(1)).map { it.id })
        }

    @Test
    fun duplicateCreatesNewIdentityAndHalfOpenQueryFiltersTombstones() =
        runBlocking {
            val store = store()
            val original = (store.createExpense(request(), uuid(208)) as StoreResult.Success<Expense>).value
            val duplicate = (store.createExpense(request(), uuid(209)) as StoreResult.Success<Expense>).value
            assertNotEquals(original.id, duplicate.id)
            assertEquals(Money(1234L, usd), original.amount)
            assertEquals(Money(1234L, usd), duplicate.amount)
            assertEquals(2, store.activeExpenses(now, now.plusSeconds(1)).size)
            assertTrue(store.activeExpenses(now.minusSeconds(1), now).isEmpty())
            store.deleteExpense(duplicate.id, duplicate.version, uuid(210))
            assertEquals(listOf(original.id), store.activeExpenses(now, now.plusSeconds(1)).map { it.id })
            assertFalse(store.getExpense(duplicate.id)!!.deletedAt == null)
        }

    private suspend fun store(): InMemoryExpenseStore {
        var next = 1000L
        val store = InMemoryExpenseStore(clock, UuidGenerator { uuid(next++) })
        store.createCategoryWithId(categoryId, "Food", "food", "red")
        store.createPaymentMethodWithId(methodId, "Cash", true)
        return store
    }

    private fun request(amount: Long = 1234L) =
        NewExpense(
            Money(amount, usd),
            now,
            categoryId,
            methodId,
            "Shop",
            "Memo",
        )

    private fun uuid(last: Long): UUID = UUID.fromString("00000000-0000-0000-0000-%012d".format(last))
}
