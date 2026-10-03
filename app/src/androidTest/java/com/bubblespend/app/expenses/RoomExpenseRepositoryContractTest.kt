package com.bubblespend.app.expenses

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.bubblespend.app.data.RoomExpenseStore
import com.bubblespend.core.application.Category
import com.bubblespend.core.application.CategoryRepository
import com.bubblespend.core.application.Expense
import com.bubblespend.core.application.ExpenseRepository
import com.bubblespend.core.application.InMemoryExpenseStore
import com.bubblespend.core.application.NewExpense
import com.bubblespend.core.application.PaymentMethod
import com.bubblespend.core.application.PaymentMethodRepository
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.Money
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RoomExpenseRepositoryContractTest {
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sharedExpenseContractRunsAgainstFakeAndRoom() =
        runBlocking {
            forEachStore { fixture ->
                val food =
                    fixture.categories
                        .observeCategories()
                        .first()
                        .first { it.name == "Food" }
                val cash =
                    fixture.methods
                        .observePaymentMethods()
                        .first()
                        .first { it.name == "Cash" }
                val request = NewExpense(Money(1234L, CurrencyCode.of("USD")), now, food.id, cash.id, " Shop ", " Memo ")
                val token = uuid(1)
                val created = (fixture.expenses.createExpense(request, token) as StoreResult.Success<Expense>).value
                val repeated = (fixture.expenses.createExpense(request, token) as StoreResult.Success<Expense>).value
                assertEquals(created, repeated)
                assertEquals("Shop", created.merchant)
                assertEquals("Memo", created.note)
                assertEquals(
                    listOf(created.id),
                    fixture.expenses
                        .observeExpenses(now, now.plusSeconds(1))
                        .first()
                        .map { it.id },
                )
                assertTrue(
                    fixture.expenses
                        .observeExpenses(now.minusSeconds(1), now)
                        .first()
                        .isEmpty(),
                )

                val edited =
                    (
                        fixture.expenses.editExpense(
                            created.id,
                            created.version,
                            request.copy(amount = Money(200L, CurrencyCode.of("USD"))),
                            uuid(2),
                        ) as StoreResult.Success<Expense>
                    ).value
                assertEquals(created.id, edited.id)
                assertEquals(created.createdAt, edited.createdAt)
                assertEquals(created.version + 1, edited.version)
                assertTrue(fixture.expenses.deleteExpense(created.id, created.version, uuid(3)) is StoreResult.Conflict)
                assertEquals(
                    200L,
                    fixture.expenses
                        .getExpense(created.id)
                        ?.amount
                        ?.minorUnits,
                )

                val deleted = (fixture.expenses.deleteExpense(edited.id, edited.version, uuid(4)) as StoreResult.Success<Expense>).value
                assertTrue(
                    fixture.expenses
                        .observeExpenses(now, now.plusSeconds(1))
                        .first()
                        .isEmpty(),
                )
                val restored = (fixture.expenses.undoDelete(deleted.id, deleted.version, uuid(5)) as StoreResult.Success<Expense>).value
                assertEquals(created.id, restored.id)
                assertEquals(
                    listOf(created.id),
                    fixture.expenses
                        .observeExpenses(now, now.plusSeconds(1))
                        .first()
                        .map { it.id },
                )
            }
        }

    @Test
    fun sharedCategoryAndPaymentContractRunsAgainstFakeAndRoom() =
        runBlocking {
            forEachStore { fixture ->
                val food =
                    fixture.categories
                        .observeCategories()
                        .first()
                        .first { it.name == "Food" }
                val cash =
                    fixture.methods
                        .observePaymentMethods()
                        .first()
                        .first { it.name == "Cash" }
                val card =
                    fixture.methods
                        .observePaymentMethods()
                        .first()
                        .first { it.name == "Card" }
                assertTrue(fixture.categories.createCategory(" food ", "food", "red", uuid(6)) is StoreResult.ValidationFailure)
                val renamed =
                    (
                        fixture.categories.renameCategory(
                            food.id,
                            food.version,
                            "Groceries",
                            uuid(7),
                        ) as StoreResult.Success<Category>
                    ).value
                assertEquals(food.id, renamed.id)
                assertTrue(fixture.categories.renameCategory(food.id, food.version, "Stale", uuid(8)) is StoreResult.Conflict)
                assertEquals("Groceries", fixture.categories.getCategory(food.id)?.name)

                assertTrue(fixture.methods.setDefaultPaymentMethod(card.id, card.version, uuid(9)) is StoreResult.Success)
                assertEquals(
                    listOf("Card"),
                    fixture.methods
                        .observePaymentMethods()
                        .first()
                        .filter { it.isDefault }
                        .map { it.name },
                )
                assertFalse(fixture.methods.getPaymentMethod(cash.id)!!.isDefault)
                val currentCard = fixture.methods.getPaymentMethod(card.id)!!
                fixture.methods.archivePaymentMethod(currentCard.id, currentCard.version, uuid(10))
                assertTrue(
                    fixture.methods
                        .observePaymentMethods()
                        .first()
                        .none { it.isDefault },
                )
                assertTrue(fixture.methods.getPaymentMethod(card.id)!!.archivedAt != null)

                fixture.categories.archiveCategory(renamed.id, renamed.version, uuid(11))
                assertTrue(
                    fixture.categories
                        .observeCategories()
                        .first()
                        .none { it.id == food.id },
                )
                assertEquals("Groceries", fixture.categories.getCategory(food.id)?.name)
                assertTrue(
                    fixture.expenses.createExpense(
                        NewExpense(Money(1L, CurrencyCode.of("USD")), now, food.id, cash.id, "", ""),
                        uuid(12),
                    ) is StoreResult.InactiveReference,
                )
            }
        }

    @Test
    fun sharedMutationTokenRetryAndMisuseContractRunsAgainstFakeAndRoom() =
        runBlocking {
            forEachStore { fixture ->
                val food =
                    fixture.categories
                        .observeCategories()
                        .first()
                        .first { it.name == "Food" }
                val cash =
                    fixture.methods
                        .observePaymentMethods()
                        .first()
                        .first { it.name == "Cash" }
                val request = NewExpense(Money(450L, CurrencyCode.of("USD")), now, food.id, cash.id, "", "")
                val expenseToken = uuid(30)
                val created = (fixture.expenses.createExpense(request, expenseToken) as StoreResult.Success<Expense>).value
                assertEquals(StoreResult.Success(created), fixture.expenses.createExpense(request, expenseToken))
                assertTrue(fixture.expenses.deleteExpense(created.id, created.version, expenseToken) is StoreResult.Conflict)
                assertEquals(null, fixture.expenses.getExpense(created.id)?.deletedAt)

                val categoryToken = uuid(31)
                val firstCategory = fixture.categories.createCategory("Crafts", "other", "neutral", categoryToken)
                assertTrue(firstCategory is StoreResult.Success)
                assertEquals(firstCategory, fixture.categories.createCategory("Crafts", "other", "neutral", categoryToken))
                assertTrue(fixture.methods.createPaymentMethod("Voucher", categoryToken) is StoreResult.Conflict)
                assertFalse(
                    fixture.methods
                        .observePaymentMethods()
                        .first()
                        .any { it.name == "Voucher" },
                )

                val methodToken = uuid(32)
                val firstMethod = fixture.methods.createPaymentMethod("Voucher", methodToken)
                assertTrue(firstMethod is StoreResult.Success)
                assertEquals(firstMethod, fixture.methods.createPaymentMethod("Voucher", methodToken))
                val method = (firstMethod as StoreResult.Success<PaymentMethod>).value
                assertTrue(fixture.methods.renamePaymentMethod(method.id, method.version, "Gift", methodToken) is StoreResult.Conflict)
                assertEquals("Voucher", fixture.methods.getPaymentMethod(method.id)?.name)
            }
        }

    @Test
    fun freshSchemaAndReopenPreserveExpenseTombstoneAndReceipt() =
        runBlocking {
            val name = databaseName()
            val first = openRoom(name)
            val food = first.observeCategories().first().first { it.name == "Food" }
            val cash = first.observePaymentMethods().first().first { it.name == "Cash" }
            val request = NewExpense(Money(321L, CurrencyCode.of("USD")), now, food.id, cash.id, "", "")
            val token = uuid(13)
            val created = (first.createExpense(request, token) as StoreResult.Success<Expense>).value
            val deleted = (first.deleteExpense(created.id, created.version, uuid(14)) as StoreResult.Success<Expense>).value
            first.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                assertEquals(1, database.version)
            }

            val reopened = openRoom(name)
            try {
                assertEquals(deleted, reopened.getExpense(created.id))
                assertTrue(reopened.observeExpenses(now, now.plusSeconds(1)).first().isEmpty())
                assertEquals(created, (reopened.createExpense(request, token) as StoreResult.Success<Expense>).value)
                assertTrue(reopened.observeExpenses(now, now.plusSeconds(1)).first().isEmpty())
            } finally {
                reopened.close()
            }
        }

    @Test
    fun seedRepeatsAfterReopenWithoutRestoringRenamedOrArchivedRecords() =
        runBlocking {
            val name = databaseName()
            val first = openRoom(name)
            val food = first.observeCategories().first().first { it.name == "Food" }
            val card = first.observePaymentMethods().first().first { it.name == "Card" }
            first.renameCategory(food.id, food.version, "Groceries", uuid(15))
            first.archivePaymentMethod(card.id, card.version, uuid(16))
            first.close()

            val reopened = openRoom(name)
            try {
                assertTrue(reopened.seedDefaults() is StoreResult.Success)
                assertEquals(6, reopened.observeCategories().first().size)
                assertEquals("Groceries", reopened.getCategory(food.id)?.name)
                assertFalse(reopened.observeCategories().first().any { it.name == "Food" })
                assertEquals(1, reopened.observePaymentMethods().first().size)
                assertTrue(reopened.getPaymentMethod(card.id)!!.archivedAt != null)
            } finally {
                reopened.close()
            }
        }

    @Test
    fun customCategoryAndPaymentIdentitySurviveRenameReopenAndStaleFailure() =
        runBlocking {
            val name = databaseName()
            val first = openRoom(name)
            val category = (first.createCategory("Crafts", "other", "neutral", uuid(40)) as StoreResult.Success<Category>).value
            val method = (first.createPaymentMethod("Voucher", uuid(41)) as StoreResult.Success<PaymentMethod>).value
            val renamedCategory =
                (
                    first.renameCategory(
                        category.id,
                        category.version,
                        "Handmade",
                        uuid(42),
                    ) as StoreResult.Success<Category>
                ).value
            val renamedMethod =
                (
                    first.renamePaymentMethod(
                        method.id,
                        method.version,
                        "Gift Card",
                        uuid(43),
                    ) as StoreResult.Success<PaymentMethod>
                ).value
            assertTrue(first.renameCategory(category.id, category.version, "Stale", uuid(44)) is StoreResult.Conflict)
            assertTrue(first.renamePaymentMethod(method.id, method.version, "Stale", uuid(45)) is StoreResult.Conflict)
            first.close()

            val reopened = openRoom(name)
            try {
                assertEquals(category.id, renamedCategory.id)
                assertEquals(method.id, renamedMethod.id)
                assertTrue(renamedCategory.version > 0)
                assertTrue(renamedMethod.version > 0)
                assertEquals(renamedCategory, reopened.getCategory(category.id))
                assertEquals(renamedMethod, reopened.getPaymentMethod(method.id))
                assertTrue(reopened.observeCategories().first().any { it.id == category.id && it.name == "Handmade" })
                assertTrue(reopened.observePaymentMethods().first().any { it.id == method.id && it.name == "Gift Card" })
            } finally {
                reopened.close()
            }
        }

    @Test
    fun failedDefaultSwitchLeavesExistingDefaultUnchanged() =
        runBlocking {
            val room = openRoom(databaseName())
            try {
                val card = room.observePaymentMethods().first().first { it.name == "Card" }
                val oldVersion = card.version
                room.renamePaymentMethod(card.id, oldVersion, "Debit", uuid(17))
                assertTrue(room.setDefaultPaymentMethod(card.id, oldVersion, uuid(18)) is StoreResult.Conflict)
                assertEquals(
                    listOf("Cash"),
                    room
                        .observePaymentMethods()
                        .first()
                        .filter { it.isDefault }
                        .map { it.name },
                )
            } finally {
                room.close()
            }
        }

    @Test
    fun corruptDisposableDatabaseReturnsFailureWithoutDeletingBytes() {
        val name = databaseName()
        val databaseFile = context.getDatabasePath(name)
        databaseFile.parentFile!!.mkdirs()
        val original = byteArrayOf(0x42, 0x54, 0x30, 0x30, 0x37)
        databaseFile.writeBytes(original)
        val result = RoomExpenseStore.open(context, name, clock, ids())
        assertTrue(result is StoreResult.StorageFailure)
        assertArrayEquals(original, databaseFile.readBytes())
    }

    private suspend fun forEachStore(assertions: suspend (Fixture) -> Unit) {
        val fake = InMemoryExpenseStore(clock, ids())
        fake.seedDefaults()
        assertions(Fixture(fake, fake, fake))
        val room = openRoom(databaseName())
        try {
            assertions(Fixture(room, room, room))
        } finally {
            room.close()
        }
    }

    private suspend fun openRoom(name: String): RoomExpenseStore {
        val result = RoomExpenseStore.open(context, name, clock, ids())
        val room = (result as StoreResult.Success<RoomExpenseStore>).value
        assertTrue(room.seedDefaults() is StoreResult.Success)
        return room
    }

    private fun ids(): UuidGenerator {
        var next = 1000L
        return UuidGenerator { uuid(next++) }
    }

    private fun databaseName(): String = "bt007-room-${UUID.randomUUID()}.db"

    private fun uuid(last: Long): UUID = UUID.fromString("00000000-0000-0000-0000-%012d".format(last))

    private data class Fixture(
        val expenses: ExpenseRepository,
        val categories: CategoryRepository,
        val methods: PaymentMethodRepository,
    )
}
