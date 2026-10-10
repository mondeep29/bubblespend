package com.bubblespend.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.bubblespend.app.ui.BubbleSpendApp
import com.bubblespend.app.ui.ExpenseAppDependencies
import com.bubblespend.app.ui.theme.BubbleSpendTheme
import com.bubblespend.core.application.InMemoryExpenseStore
import com.bubblespend.core.application.InMemorySetupSettingsRepository
import com.bubblespend.core.application.NewExpense
import com.bubblespend.core.application.SetupSettings
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.Money
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Synthetic in-memory data only: these journeys do not mutate the installed expense database. */
class ReferenceAppearanceTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val currency = CurrencyCode.of("INR")
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sequence = AtomicLong(100)
    private val ids = UuidGenerator { UUID(0, sequence.getAndIncrement()) }
    private val store = InMemoryExpenseStore(clock, ids)
    private val settings = InMemorySetupSettingsRepository(SetupSettings(confirmedCurrency = currency, completed = true))

    @Test
    fun categoryCardsShowRealTotalsAndOpenOnlyMatchingTransactions() {
        launch(populated = true)
        capture("reference-home")
        composeRule.onNodeWithText("Food").performClick()
        composeRule.onNodeWithText("Lunch at Bistro").assertIsDisplayed()
        composeRule.onNodeWithText("Metro card").assertDoesNotExist()
        composeRule.onNodeWithTag("overview_total").performClick()
        composeRule.onNodeWithText("Metro card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyHomeAndEntryPreserveSaveAndDetailActions() {
        launch(populated = false)
        composeRule.onNodeWithText("No expenses yet").assertIsDisplayed()
        capture("reference-empty")
        composeRule.onNodeWithText("Add expense").performClick()
        capture("reference-entry")
        composeRule.onNodeWithText("Amount").performTextInput("420")
        composeRule.onNodeWithText("Merchant").performTextInput("Lunch at Bistro")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        awaitHome()
        composeRule.onNodeWithText("Lunch at Bistro").performScrollTo().performClick()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        capture("reference-detail")
    }

    @Test
    fun largeTextKeepsCategoryValuesNavigationAndSettingsReachable() {
        launch(populated = true, fontScale = 2f)
        composeRule.onNodeWithText("Food").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Week starts on").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save week start").performScrollTo().assertIsDisplayed()
        capture("reference-settings-large")
    }

    private fun launch(
        populated: Boolean,
        fontScale: Float = 1f,
    ) {
        runBlocking {
            store.seedDefaults()
            if (populated) {
                val categories = store.observeCategories().first()
                val method = store.observePaymentMethods().first().first()
                listOf(
                    Triple("Food", 820000L, "Lunch at Bistro"),
                    Triple("Housing", 630000L, "Home essentials"),
                    Triple("Transport", 410000L, "Metro card"),
                    Triple("Utilities", 380000L, "Electricity"),
                ).forEachIndexed { index, (name, amount, merchant) ->
                    val category = categories.single { it.name == name }
                    val saved =
                        store.createExpense(
                            NewExpense(Money(amount, currency), now.minusSeconds(index.toLong()), category.id, method.id, merchant, ""),
                            ids.next(),
                        )
                    check(saved is StoreResult.Success)
                }
            }
        }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BubbleSpendTheme {
                    BubbleSpendApp(
                        ExpenseAppDependencies(store, store, store, settings, clock, ids, Locale.US, Dispatchers.Default, settings),
                    )
                }
            }
        }
        awaitHome()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("—").fetchSemanticsNodes().isEmpty() }
    }

    private fun awaitHome() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home_route").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun capture(name: String) {
        val directory = File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")))
        check(directory.exists() || directory.mkdirs())
        File(directory, name + ".png").outputStream().use { output ->
            composeRule
                .onNodeWithTag("home_surface")
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
