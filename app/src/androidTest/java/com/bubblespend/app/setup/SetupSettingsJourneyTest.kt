package com.bubblespend.app.setup

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.bubblespend.app.ui.BubbleSpendApp
import com.bubblespend.app.ui.ExpenseAppDependencies
import com.bubblespend.app.ui.theme.BubbleSpendTheme
import com.bubblespend.core.application.InMemoryExpenseStore
import com.bubblespend.core.application.InMemorySetupSettingsRepository
import com.bubblespend.core.application.SetupSettings
import com.bubblespend.core.application.SetupSettingsRepository
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class SetupSettingsJourneyTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC)
    private val ids = UuidGenerator { UUID.randomUUID() }
    private val expenses = InMemoryExpenseStore(clock, ids)
    private val stored = InMemorySetupSettingsRepository()
    private val faults = FaultSettings(stored)
    private val owner = mutableStateOf(newOwner())
    private val locale = mutableStateOf(Locale.US)

    @After fun clearViewModels() {
        composeRule.runOnUiThread { owner.value.viewModelStore.clear() }
    }

    @Test fun freshInstallSuggestionsBrowseDraftAndExplicitCompletion() {
        launch()
        composeRule.onNodeWithText("USD").assertIsDisplayed()
        composeRule.onNodeWithText("Monday").assertIsDisplayed()
        assertNull(runBlocking { (stored.loadConfirmedCurrency() as StoreResult.Success).value })
        click("Browse app")
        click("Add expense")
        composeRule.onNodeWithText("Amount").performTextInput("12.34")
        composeRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        click("Return to setup")
        selectWeek(7)
        click("Complete setup")
        composeRule.onNodeWithText("12.34").assertIsDisplayed()
        click("Save")
        awaitText("Home")
        assertEquals(1, runBlocking { expenses.observeAllActiveExpenses().first().size })
        assertEquals(
            "Cash",
            runBlocking {
                expenses
                    .observePaymentMethods()
                    .first()
                    .single { it.isDefault }
                    .name
            },
        )
    }

    @Test fun unfinishedChoicesResumeInANewViewModelAndLocaleCannotReplaceConfirmedCurrency() {
        launch()
        composeRule.onNodeWithText("Currency code").performTextClearance()
        composeRule.onNodeWithText("Currency code").performTextInput("INR")
        selectWeek(6)
        awaitStoredDraft("INR", 6)
        reload(Locale.JAPAN)
        composeRule.onNodeWithText("INR").assertIsDisplayed()
        composeRule.onNodeWithText("Saturday").assertIsDisplayed()
        click("Complete setup")
        awaitText("Home")
        reload(Locale.UK)
        click("Settings")
        composeRule.onNodeWithText("Base currency: INR").assertIsDisplayed()
        composeRule.onNodeWithText("Saturday").assertIsDisplayed()
        composeRule.onNodeWithText("Currency code").assertDoesNotExist()
    }

    @Test fun invalidCurrencyKeepsInputWithoutConfirmingAndCanBeCorrected() {
        launch()
        composeRule.onNodeWithText("Currency code").performTextClearance()
        composeRule.onNodeWithText("Currency code").performTextInput("ZZZ")
        click("Complete setup")
        awaitText("Select a valid currency")
        composeRule.onNodeWithText("Select a valid currency").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ZZZ").performScrollTo().assertIsDisplayed()
        assertNull(runBlocking { (stored.loadConfirmedCurrency() as StoreResult.Success).value })
        composeRule.onNodeWithText("Currency code").performTextClearance()
        composeRule.onNodeWithText("Currency code").performTextInput("USD")
        click("Complete setup")
        awaitText("Home")
    }

    @Test fun completionFailureKeepsChoicesAndRetryCompletes() {
        launch()
        selectWeek(7)
        faults.failComplete = true
        click("Complete setup")
        composeRule.onNodeWithText("Sunday").assertIsDisplayed()
        assertNull(runBlocking { (stored.loadConfirmedCurrency() as StoreResult.Success).value })
        click("Retry")
        awaitText("Home")
        assertEquals(DayOfWeek.SUNDAY, runBlocking { (stored.loadSettings() as StoreResult.Success).value.weekStart })
    }

    @Test fun settingsFailureRetainsAttemptedDayAndPreviousEffectiveDayUntilRetry() {
        runBlocking { stored.completeSetup(CurrencyCode.of("USD"), DayOfWeek.MONDAY) }
        launch()
        click("Settings")
        selectWeek(7)
        faults.failWeek = true
        click("Save week start")
        composeRule.onNodeWithText("Sunday").assertIsDisplayed()
        assertEquals(DayOfWeek.MONDAY, runBlocking { (stored.loadSettings() as StoreResult.Success).value.weekStart })
        click("Retry")
        awaitText("Week start saved")
        reload(Locale.JAPAN)
        click("Settings")
        composeRule.onNodeWithText("Sunday").assertIsDisplayed()
        composeRule.onNodeWithText("Base currency: USD").assertIsDisplayed()
    }

    @Test fun readFailureAllowsBrowsingAndDraftingButBlocksSaveUntilRetry() {
        faults.failRead = true
        launch()
        click("Add expense")
        composeRule.onNodeWithText("Amount").performTextInput("12.34")
        composeRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        click("Return to setup")
        composeRule.onNodeWithText("Complete setup").performScrollTo().assertIsNotEnabled()
        faults.failRead = false
        click("Retry")
        click("Complete setup")
        composeRule.onNodeWithText("12.34").assertIsDisplayed()
    }

    @Test fun failedDraftWriteKeepsEnteredChoiceForRetryAndReopen() {
        launch()
        faults.failDraft = true
        composeRule.onNodeWithText("Currency code").performTextClearance()
        awaitText("Retry")
        composeRule.onNodeWithText("Currency code").performTextInput("INR")
        awaitStoredDraft("INR", 1)
        reload(Locale.US)
        composeRule.onNodeWithText("INR").assertIsDisplayed()
        assertNull(runBlocking { (stored.loadConfirmedCurrency() as StoreResult.Success).value })
    }

    @Test fun localeWithoutCurrencyRequiresExplicitChoice() {
        locale.value = Locale.ROOT
        launch()
        click("Complete setup")
        composeRule.onNodeWithText("Select a valid currency").assertIsDisplayed()
        composeRule.onNodeWithText("Monday").assertIsDisplayed()
    }

    @Test fun largeTextKeepsSetupValidationRecoveryAndSettingsControlsReachable() {
        launch(fontScale = 2f)
        screenshot("setup-large")
        composeRule.onNodeWithText("Currency code").performScrollTo().performTextClearance()
        click("Complete setup")
        composeRule.onNodeWithText("Select a valid currency").performScrollTo().assertIsDisplayed()
        screenshot("setup-validation-large")
        composeRule.onNodeWithText("Currency code").performScrollTo().performTextInput("USD")
        selectWeek(7)
        faults.failComplete = true
        click("Complete setup")
        awaitText("Retry")
        screenshot("setup-retry-large")
        click("Retry")
        awaitText("Home")
        click("Settings")
        composeRule.onNodeWithText("Base currency: USD").performScrollTo().assertIsDisplayed()
        screenshot("settings-large")
        selectWeek(3)
        click("Save week start")
        composeRule.onNodeWithText("Week start saved").performScrollTo().assertIsDisplayed()
        click("Close")
        composeRule.onNodeWithText("No expenses yet").performScrollTo().assertIsDisplayed()
    }

    private fun launch(fontScale: Float? = null) {
        runBlocking { expenses.seedDefaults() }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner.value,
                LocalDensity provides Density(LocalDensity.current.density, fontScale ?: LocalDensity.current.fontScale),
            ) {
                BubbleSpendTheme {
                    BubbleSpendApp(
                        ExpenseAppDependencies(expenses, expenses, expenses, stored, clock, ids, locale.value, Dispatchers.Default, faults),
                    )
                }
            }
        }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Loading").fetchSemanticsNodes().isEmpty() }
        composeRule.waitForIdle()
    }

    private fun reload(newLocale: Locale) {
        composeRule.runOnUiThread {
            owner.value.viewModelStore.clear()
            locale.value = newLocale
            owner.value = newOwner()
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Loading").fetchSemanticsNodes().isEmpty() }
    }

    private fun click(text: String) {
        awaitText(text)
        val control = composeRule.onNodeWithText(text)
        if (!control.isDisplayed()) control.performScrollTo()
        control.assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun selectWeek(day: Int) {
        composeRule.onNodeWithTag("week_start_choice").performScrollTo().performClick()
        composeRule.onNodeWithTag("week_day_" + day).performScrollTo().performClick()
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitStoredDraft(
        currency: String,
        week: Int,
    ) {
        composeRule.waitUntil(10_000) {
            runBlocking {
                (stored.loadSettings() as StoreResult.Success).value.let {
                    it.draftCurrency == currency &&
                        it.draftWeekStart == week
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val directory = File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")))
        check(directory.exists() || directory.mkdirs())
        val file = File(directory, name + ".png")
        file.outputStream().use { output ->
            composeRule
                .onNodeWithTag("home_surface")
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun newOwner(): ViewModelStoreOwner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    private class FaultSettings(
        private val delegate: SetupSettingsRepository,
    ) : SetupSettingsRepository by delegate {
        @Volatile var failRead = false

        @Volatile var failComplete = false

        @Volatile var failWeek = false

        @Volatile var failDraft = false

        override suspend fun loadSettings(): StoreResult<SetupSettings> =
            if (failRead) StoreResult.StorageFailure() else delegate.loadSettings()

        override suspend fun completeSetup(
            currency: CurrencyCode,
            weekStart: DayOfWeek,
        ): StoreResult<SetupSettings> =
            if (failComplete) {
                failComplete = false
                StoreResult.StorageFailure()
            } else {
                delegate.completeSetup(currency, weekStart)
            }

        override suspend fun changeWeekStart(weekStart: DayOfWeek): StoreResult<SetupSettings> =
            if (failWeek) {
                failWeek = false
                StoreResult.StorageFailure()
            } else {
                delegate.changeWeekStart(weekStart)
            }

        override suspend fun saveSetupDraft(
            currency: String,
            weekStart: Int,
        ): StoreResult<Unit> =
            if (failDraft) {
                failDraft = false
                StoreResult.StorageFailure()
            } else {
                delegate.saveSetupDraft(currency, weekStart)
            }
    }
}
