package com.bubblespend.app.expenses

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.bubblespend.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** User journeys through the launched app; assertions use visible control labels. */
class ExpenseManagementJourneyTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun finishSetupIfNeeded() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home_surface").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
                composeRule.onAllNodesWithText("Loading").fetchSemanticsNodes().isEmpty()
        }
        if (composeRule.onAllNodesWithText("Complete setup").fetchSemanticsNodes().isNotEmpty()) {
            if (composeRule.onAllNodesWithText("Currency code").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText("Currency code").performTextClearance()
                composeRule.onNodeWithText("Currency code").performTextInput("USD")
            }
            composeRule.onNodeWithText("Complete setup").performClick()
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun entryOffersLabeledFieldsAndKeepsAnUnsavedDraftUntilDiscardIsConfirmed() {
        openEntry()

        composeRule.onNodeWithText("Amount").assertIsDisplayed()
        composeRule.onNodeWithText("Category").assertIsDisplayed()
        composeRule.onNodeWithText("Payment method").assertIsDisplayed()
        composeRule.onNodeWithText("Occurred at").assertIsDisplayed()
        composeRule.onNodeWithText("Merchant").assertIsDisplayed()
        composeRule.onNodeWithText("Note").assertIsDisplayed()
        composeRule.onNodeWithText("Amount").performTextInput("12.34")
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.onNodeWithText("12.34").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.onNodeWithText("12.34").assertDoesNotExist()
    }

    @Test
    fun invalidEntryKeepsFieldsAndShowsAnAccessibleError() {
        openEntry()

        composeRule.onNodeWithText("Amount").performTextInput("0")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("0").assertIsDisplayed()
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .assertIsDisplayed()
    }

    @Test
    fun deleteOffersOneVisibleUndoAndActivityInterruptionEndsThatAction() {
        createExpense("12.34", "Undo journey")
        composeRule.onNodeWithText("Undo journey").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Undo").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule.onNodeWithText("Undo journey").assertDoesNotExist()
    }

    @Test
    fun savedExpenseSurvivesActivityRestartAndEditUpdatesVisibleFields() {
        createExpense("12.34", "Original edit")
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Original edit").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Merchant").performTextClearance()
        composeRule.onNodeWithText("Merchant").performTextInput("Edited expense")
        composeRule.onNodeWithText("Save").performClick()

        awaitSavedExpenseOnHome("Edited expense")
        composeRule.onNodeWithText("Original edit").assertDoesNotExist()
    }

    @Test
    fun duplicateOpensEditableCopyAndKeepsOriginalExpense() {
        createExpense("12.34", "Original duplicate")
        composeRule.onNodeWithText("Original duplicate").performClick()
        composeRule.onNodeWithText("Duplicate").performClick()
        composeRule.onNodeWithText("Merchant").performTextClearance()
        composeRule.onNodeWithText("Merchant").performTextInput("Copied expense")
        composeRule.onNodeWithText("Save").performClick()

        awaitSavedExpenseOnHome("Copied expense")
        composeRule.onNodeWithText("Original duplicate").assertIsDisplayed()
    }

    @Test
    fun undoDuringVisibleSnackbarRestoresExpenseAcrossActivityRestart() {
        createExpense("12.34", "Undo restored")
        composeRule.onNodeWithText("Undo restored").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Undo").performClick()

        composeRule.onNodeWithText("Undo restored").performScrollTo().assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Undo restored").assertIsDisplayed()
    }

    @Test
    fun categoryManagementExposesCreateRenameAndArchiveActions() {
        composeRule.onNodeWithText("Manage categories").performClick()
        composeRule.onNodeWithText("Create category").assertIsDisplayed()
        composeRule.onNodeWithText("Food").performClick()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Archive").assertIsDisplayed()
    }

    @Test
    fun paymentManagementExposesCreateAndDefaultActions() {
        composeRule.onNodeWithText("Manage payment methods").performClick()
        composeRule.onNodeWithText("Create payment method").assertIsDisplayed()
        composeRule.onNodeWithText("Cash").assertIsDisplayed()
        composeRule.onNodeWithText("Default").assertIsDisplayed()
        composeRule.onNodeWithText("Card").performClick()
        composeRule.onNodeWithText("Set default").performClick()
        composeRule.onNodeWithText("Card").assertIsDisplayed()
        composeRule.onNodeWithText("Default").assertIsDisplayed()
    }

    private fun openEntry() {
        composeRule.onNodeWithText("Add expense").performClick()
    }

    private fun createExpense(
        amount: String,
        merchant: String,
    ) {
        openEntry()
        composeRule.onNodeWithText("Amount").performTextInput(amount)
        composeRule.onNodeWithText("Category").performClick()
        composeRule.onNode(hasText("Food") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Payment method").performClick()
        composeRule.onNode(hasText("Cash") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Merchant").performTextInput(merchant)
        composeRule.onNodeWithText("Save").performClick()
        awaitSavedExpenseOnHome(merchant)
    }

    private fun awaitSavedExpenseOnHome(merchant: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(merchant).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(merchant).assertIsDisplayed()
    }
}
