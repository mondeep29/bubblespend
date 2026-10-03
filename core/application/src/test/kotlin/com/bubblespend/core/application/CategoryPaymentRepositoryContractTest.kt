package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.Money
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CategoryPaymentRepositoryContractTest {
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun seedingIsIdempotentAndPreservesUserChanges() =
        runBlocking {
            val store = store()
            store.seedDefaults()
            assertEquals(
                listOf("Food", "Transport", "Housing", "Utilities", "Health", "Other"),
                store.activeCategories().map { it.name },
            )
            assertEquals(listOf("Cash", "Card"), store.activePaymentMethods().map { it.name })
            assertEquals(listOf("Cash"), store.activePaymentMethods().filter { it.isDefault }.map { it.name })

            val food = store.activeCategories().first()
            val renamed =
                (
                    store.renameCategory(food.id, food.version, "Groceries", uuid(1))
                        as StoreResult.Success<Category>
                ).value
            store.seedDefaults()
            assertEquals(6, store.activeCategories().size)
            assertEquals("Groceries", store.getCategory(renamed.id)?.name)
            assertFalse(store.activeCategories().any { it.name == "Food" })
        }

    @Test
    fun normalizedDuplicateAndStaleRenameNeverOverwrite() =
        runBlocking {
            val store = store()
            store.seedDefaults()
            val food = store.activeCategories().first()
            assertTrue(
                store.createCategory("  food  ", "food", "red", uuid(2)) is StoreResult.ValidationFailure,
            )
            val renamed =
                (
                    store.renameCategory(food.id, food.version, "Groceries", uuid(3))
                        as StoreResult.Success<Category>
                ).value
            assertTrue(store.renameCategory(food.id, food.version, "Other name", uuid(4)) is StoreResult.Conflict)
            assertEquals("Groceries", store.getCategory(renamed.id)?.name)
        }

    @Test
    fun archivePreservesHistoricalReferenceAndBlocksNewExpense() =
        runBlocking {
            val store = store()
            store.seedDefaults()
            val food = store.activeCategories().first()
            val cash = store.activePaymentMethods().first()
            val created =
                (
                    store.createExpense(
                        NewExpense(Money(123L, CurrencyCode.of("USD")), now, food.id, cash.id, "", ""),
                        uuid(5),
                    ) as StoreResult.Success<Expense>
                ).value
            store.archiveCategory(food.id, food.version, uuid(6))
            assertNotNull(store.getCategory(food.id))
            assertEquals(created.id, store.getExpense(created.id)?.id)
            assertEquals(123L, store.getExpense(created.id)?.amount?.minorUnits)
            assertFalse(store.activeCategories().any { it.id == food.id })
            assertTrue(
                store.createExpense(
                    NewExpense(Money(1L, CurrencyCode.of("USD")), now, food.id, cash.id, "", ""),
                    uuid(7),
                ) is StoreResult.InactiveReference,
            )
        }

    @Test
    fun defaultChangeAndArchiveAreAtomic() =
        runBlocking {
            val store = store()
            store.seedDefaults()
            val cash = store.activePaymentMethods().first { it.name == "Cash" }
            val card = store.activePaymentMethods().first { it.name == "Card" }
            assertTrue(store.setDefaultPaymentMethod(card.id, card.version, uuid(8)) is StoreResult.Success)
            assertEquals(listOf("Card"), store.activePaymentMethods().filter { it.isDefault }.map { it.name })
            val updatedCard = store.getPaymentMethod(card.id)!!
            store.archivePaymentMethod(updatedCard.id, updatedCard.version, uuid(9))
            assertTrue(store.activePaymentMethods().none { it.isDefault })
            assertFalse(store.getPaymentMethod(cash.id)!!.isDefault)
            assertNotNull(store.getPaymentMethod(card.id))
        }

    @Test
    fun mutationTokenRetriesOriginalOperationAndRejectsDifferentOperationOrType() =
        runBlocking {
            val store = store()
            store.seedDefaults()
            val categoryToken = uuid(20)
            val firstCategory = store.createCategory("Crafts", "other", "neutral", categoryToken)
            assertTrue(firstCategory is StoreResult.Success)
            assertEquals(firstCategory, store.createCategory("Crafts", "other", "neutral", categoryToken))
            val category = (firstCategory as StoreResult.Success<Category>).value
            assertTrue(store.archiveCategory(category.id, category.version, categoryToken) is StoreResult.Conflict)
            assertEquals(null, store.getCategory(category.id)?.archivedAt)
            assertTrue(store.createPaymentMethod("Voucher", categoryToken) is StoreResult.Conflict)
            assertFalse(store.activePaymentMethods().any { it.name == "Voucher" })

            val methodToken = uuid(21)
            val firstMethod = store.createPaymentMethod("Voucher", methodToken)
            assertTrue(firstMethod is StoreResult.Success)
            assertEquals(firstMethod, store.createPaymentMethod("Voucher", methodToken))
            val method = (firstMethod as StoreResult.Success<PaymentMethod>).value
            assertTrue(store.renamePaymentMethod(method.id, method.version, "Gift", methodToken) is StoreResult.Conflict)
            assertEquals("Voucher", store.getPaymentMethod(method.id)?.name)
        }

    private fun store(): InMemoryExpenseStore {
        var next = 500L
        return InMemoryExpenseStore(clock, UuidGenerator { uuid(next++) })
    }

    private fun uuid(last: Long): UUID = UUID.fromString("00000000-0000-0000-0000-%012d".format(last))
}
