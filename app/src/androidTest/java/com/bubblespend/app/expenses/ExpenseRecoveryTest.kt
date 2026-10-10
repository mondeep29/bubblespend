package com.bubblespend.app.expenses

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.bubblespend.app.ui.BubbleSpendApp
import com.bubblespend.app.ui.ExpenseAppDependencies
import com.bubblespend.core.application.BaseCurrencyRepository
import com.bubblespend.core.application.Category
import com.bubblespend.core.application.CategoryRepository
import com.bubblespend.core.application.Expense
import com.bubblespend.core.application.ExpenseRepository
import com.bubblespend.core.application.InMemoryExpenseStore
import com.bubblespend.core.application.InMemorySetupSettingsRepository
import com.bubblespend.core.application.NewExpense
import com.bubblespend.core.application.PaymentMethodRepository
import com.bubblespend.core.application.SetupSettings
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Injects deterministic repository responses into the public app composition boundary. */
class ExpenseRecoveryTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.fixed(Instant.parse("2026-01-15T12:00:13Z"), ZoneOffset.UTC)
    private val sequence = AtomicLong(1)
    private val ids = UuidGenerator { UUID(0, sequence.getAndIncrement()) }
    private val stored = InMemoryExpenseStore(clock, ids)
    private val failures = FailureStore(stored)
    private val currency = CurrencyCode.of("USD")

    @Test
    fun failedSaveKeepsEnteredValuesAndRetryPersistsOnlyOneExpense() {
        launch()
        failures.failNextCreate = true
        fillEntry("12.34", "Recoverable create")

        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Recoverable create").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Recoverable create").assertIsDisplayed()
        // The visible success must correspond to the single persisted logical submission.
        assertEquals(1, failures.successfulCreates)
    }

    @Test
    fun committedCreateWithLostResponseRetryPreservesOneLogicalExpense() {
        launch()
        failures.commitThenFailCreate = true
        fillEntry("12.34", "Receipt retry")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_route").fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { stored.observeAllActiveExpenses().first() }
        assertEquals(1, saved.size)
        assertEquals("Receipt retry", saved.single().merchant)
        assertEquals(1L, saved.single().version)
    }

    @Test
    fun malformedOccurredTimeKeepsTextShowsFieldErrorAndCannotSave() {
        launch()
        fillEntry("12.34", "Invalid time draft")
        composeRule.onNodeWithText("Occurred at").performTextClearance()
        composeRule.onNodeWithText("Occurred at").performTextInput("not a date")

        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("not a date").assertIsDisplayed()
        composeRule.onNodeWithText("Invalid time draft").assertIsDisplayed()
        composeRule
            .onNodeWithText("Occurred at")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        val expenses =
            runBlocking {
                stored.observeExpenses(Instant.EPOCH, clock.instant().plusSeconds(1)).first()
            }
        assertEquals(0, expenses.size)
    }

    @Test
    fun failedDeleteDoesNotAnnounceSuccessAndOffersRetry() {
        launch()
        fillEntry("12.34", "Recoverable delete")
        composeRule.onNodeWithText("Save").performClick()
        failures.failNextDelete = true

        composeRule.onNodeWithText("Recoverable delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Recoverable delete").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun invalidCategoryNameKeepsDraftAndAnnouncesFieldError() {
        launch()
        composeRule.onNodeWithText("Manage categories").performClick()
        composeRule.onNodeWithText("Create category").performClick()
        composeRule.onNodeWithText("Name").performTextInput("   ")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Name").assertIsDisplayed()
        composeRule.onNodeWithText("   ").assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).assertIsDisplayed()
    }

    @Test
    fun failedCategoryRenameKeepsDraftAndHistoricalLabel() {
        launch()
        composeRule.onNodeWithText("Manage categories").performClick()
        composeRule.onNodeWithText("Food").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        failures.failNextCategoryRename = true
        composeRule.onNodeWithText("Name").performTextInput("New food label")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("New food label").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        // The underlying repository remains authoritative until a retry succeeds.
        composeRule.onNodeWithText("Food").assertIsDisplayed()
    }

    @Test
    fun archivedDraftCategoryRequiresFreshSelectionBeforeSaving() {
        launch()
        composeRule.onNodeWithText("Add expense").performClick()
        composeRule.onNodeWithText("Category").performClick()
        composeRule.onNode(hasText("Food") and hasClickAction()).performClick()

        runBlocking {
            val selected = stored.observeCategories().first().single { it.name == "Food" }
            stored.archiveCategory(selected.id, selected.version, ids.next())
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Food").assertDoesNotExist()
        composeRule.onNodeWithText("Manage categories").assertIsDisplayed()
    }

    @Test
    fun noActiveCategoriesOffersCreatePathAndCannotSaveExpense() {
        runBlocking {
            stored.seedDefaults()
            stored.observeCategories().first().forEach { category ->
                stored.archiveCategory(category.id, category.version, ids.next())
            }
        }
        launch()
        composeRule.onNodeWithText("Add expense").performClick()

        composeRule.onNodeWithText("Manage categories").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun staleCategoryRenameKeepsEditableDraftAndDoesNotOverwriteStoredName() {
        launch()
        composeRule.onNodeWithText("Manage categories").performClick()
        composeRule.onNodeWithText("Food").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        failures.conflictNextCategoryRename = true
        composeRule.onNodeWithText("Name").performTextInput("Stale food label")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Stale food label").assertIsDisplayed()
        composeRule.onNodeWithText("Conflict").assertIsDisplayed()
        val storedName =
            runBlocking {
                stored
                    .observeCategories()
                    .first()
                    .single { it.name == "Food" }
                    .name
            }
        assertEquals("Food", storedName)
    }

    @Test
    fun committedDeleteWithLostResponseResolvesTombstoneBeforeReportingOutcome() {
        launch()
        fillEntry("12.34", "Uncertain delete")
        composeRule.onNodeWithText("Save").performClick()
        failures.commitThenFailDelete = true
        composeRule.onNodeWithText("Uncertain delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Uncertain delete").assertDoesNotExist()
        assertEquals(1, failures.deleteCalls)
    }

    @Test
    fun staleExpenseEditPreservesDraftWithoutOverwritingNewerRecord() {
        launch()
        fillEntry("12.34", "Stale edit")
        composeRule.onNodeWithText("Save").performClick()
        failures.conflictNextEdit = true
        composeRule.onNodeWithText("Stale edit").performClick()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Amount").performTextClearance()
        composeRule.onNodeWithText("Amount").performTextInput("23.45")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("23.45").assertIsDisplayed()
        composeRule.onNodeWithText("Conflict").assertIsDisplayed()
        val storedAmount =
            runBlocking {
                stored
                    .observeExpenses(Instant.EPOCH, clock.instant().plusSeconds(1))
                    .first()
                    .single()
                    .amount.minorUnits
            }
        assertEquals(1234L, storedAmount)
    }

    @Test
    fun renamedThenArchivedCategoryKeepsHistoricalExpenseLabelReadable() {
        launch()
        fillEntry("12.34", "Historical label")
        composeRule.onNodeWithText("Save").performClick()
        runBlocking {
            val original = stored.observeCategories().first().single { it.name == "Food" }
            val renamed = stored.renameCategory(original.id, original.version, "Meals", ids.next()) as StoreResult.Success
            stored.archiveCategory(renamed.value.id, renamed.value.version, ids.next())
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Historical label").assertIsDisplayed()
        composeRule.onNodeWithText("Meals").assertIsDisplayed()
    }

    private fun launch() {
        runBlocking { stored.seedDefaults() }
        composeRule.setContent {
            BubbleSpendApp(
                dependencies =
                    ExpenseAppDependencies(
                        expenseRepository = failures,
                        categoryRepository = failures,
                        paymentMethodRepository = failures,
                        baseCurrencyRepository = ConfirmedCurrency(currency),
                        clock = clock,
                        uuidGenerator = ids,
                        locale = Locale.US,
                        dispatcher = Dispatchers.Default,
                        setupSettingsRepository =
                            InMemorySetupSettingsRepository(
                                SetupSettings(confirmedCurrency = currency, completed = true),
                            ),
                    ),
            )
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home_route").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun fillEntry(
        amount: String,
        merchant: String,
    ) {
        composeRule.onNodeWithText("Add expense").performClick()
        composeRule.onNodeWithText("Amount").performTextInput(amount)
        composeRule.onNodeWithText("Category").performClick()
        composeRule.onNode(hasText("Food") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Payment method").performClick()
        composeRule.onNode(hasText("Cash") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Merchant").performTextInput(merchant)
    }

    private class ConfirmedCurrency(
        private val currency: CurrencyCode,
    ) : BaseCurrencyRepository {
        override suspend fun loadConfirmedCurrency(): StoreResult<CurrencyCode?> = StoreResult.Success(currency)

        override suspend fun confirmCurrency(currency: CurrencyCode): StoreResult<CurrencyCode> = StoreResult.Success(currency)
    }

    private class FailureStore(
        private val delegate: InMemoryExpenseStore,
    ) : ExpenseRepository by delegate,
        CategoryRepository by delegate,
        PaymentMethodRepository by delegate {
        var failNextCreate = false
        var commitThenFailCreate = false
        var failNextDelete = false
        var failNextCategoryRename = false
        var conflictNextCategoryRename = false
        var commitThenFailDelete = false
        var conflictNextEdit = false
        var successfulCreates = 0
        var deleteCalls = 0

        override suspend fun createExpense(
            expense: NewExpense,
            token: UUID,
        ): StoreResult<Expense> {
            if (failNextCreate) {
                failNextCreate = false
                return StoreResult.StorageFailure()
            }
            if (commitThenFailCreate) {
                commitThenFailCreate = false
                delegate.createExpense(expense, token)
                return StoreResult.StorageFailure()
            }
            val result = delegate.createExpense(expense, token)
            if (result is StoreResult.Success) successfulCreates++
            return result
        }

        override suspend fun deleteExpense(
            id: UUID,
            expectedVersion: Long,
            token: UUID,
        ): StoreResult<Expense> {
            deleteCalls++
            if (failNextDelete) {
                failNextDelete = false
                return StoreResult.StorageFailure()
            }
            if (commitThenFailDelete) {
                commitThenFailDelete = false
                delegate.deleteExpense(id, expectedVersion, token)
                return StoreResult.StorageFailure()
            }
            return delegate.deleteExpense(id, expectedVersion, token)
        }

        override suspend fun editExpense(
            id: UUID,
            expectedVersion: Long,
            expense: NewExpense,
            token: UUID,
        ): StoreResult<Expense> {
            if (conflictNextEdit) {
                conflictNextEdit = false
                return StoreResult.Conflict(expectedVersion + 1)
            }
            return delegate.editExpense(id, expectedVersion, expense, token)
        }

        override suspend fun renameCategory(
            id: UUID,
            expectedVersion: Long,
            name: String,
            token: UUID,
        ): StoreResult<Category> {
            if (failNextCategoryRename) {
                failNextCategoryRename = false
                return StoreResult.StorageFailure()
            }
            if (conflictNextCategoryRename) {
                conflictNextCategoryRename = false
                return StoreResult.Conflict(expectedVersion + 1)
            }
            return delegate.renameCategory(id, expectedVersion, name, token)
        }
    }
}
