package com.bubblespend.app.setup

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.bubblespend.app.data.PreferenceCurrencyRepository
import com.bubblespend.app.data.RoomExpenseStore
import com.bubblespend.app.ui.BubbleSpendApp
import com.bubblespend.app.ui.ExpenseAppDependencies
import com.bubblespend.app.ui.theme.BubbleSpendTheme
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Clock
import java.util.Locale
import java.util.UUID

/** Real Room and DataStore adapters, with isolated files so personal device data is preserved. */
class SetupPersistentJourneyTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun firstLaunchUnfinishedSetupReopenCompletionExpenseAndSettingsSurviveStorageReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val databaseName = "setup-" + id + ".db"
        val preferencesFile = File(context.cacheDir, id + ".preferences_pb")
        val clock = Clock.systemUTC()
        val ids = UuidGenerator { UUID.randomUUID() }
        var job = SupervisorJob()
        var room = runBlocking { (RoomExpenseStore.open(context, databaseName, clock, ids) as StoreResult.Success).value }
        var preferences =
            PreferenceCurrencyRepository(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = {
                    preferencesFile
                }),
            )
        runBlocking { room.seedDefaults() }
        val owner = mutableStateOf(newOwner())
        val dependencies =
            mutableStateOf(ExpenseAppDependencies(room, room, room, preferences, clock, ids, Locale.US, Dispatchers.IO, preferences))

        fun reopen() {
            composeRule.runOnUiThread { owner.value.viewModelStore.clear() }
            runBlocking { job.cancelAndJoin() }
            room.close()
            job = SupervisorJob()
            room = runBlocking { (RoomExpenseStore.open(context, databaseName, clock, ids) as StoreResult.Success).value }
            preferences =
                PreferenceCurrencyRepository(
                    PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = {
                        preferencesFile
                    }),
                )
            composeRule.runOnUiThread {
                dependencies.value =
                    ExpenseAppDependencies(room, room, room, preferences, clock, ids, Locale.JAPAN, Dispatchers.IO, preferences)
                owner.value = newOwner()
            }
            await("Loading", absent = true)
            composeRule.waitForIdle()
        }
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner.value) {
                    BubbleSpendTheme { BubbleSpendApp(dependencies.value) }
                }
            }
            await("Set up BubbleSpend")
            composeRule.onNodeWithText("Currency code").performTextClearance()
            composeRule.onNodeWithText("Currency code").performTextInput("INR")
            chooseWeek(7)
            click("Browse app")
            composeRule.waitUntil(
                10_000,
            ) { runBlocking { (preferences.loadSettings() as StoreResult.Success).value.draftCurrency == "INR" } }
            reopen()
            await("Set up BubbleSpend")
            composeRule.onNodeWithText("INR").assertIsDisplayed()
            composeRule.onNodeWithText("Sunday").assertIsDisplayed()
            click("Browse app")
            click("Add expense")
            composeRule.onNodeWithText("Amount").performTextInput("12.34")
            composeRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
            click("Return to setup")
            click("Complete setup")
            await("Amount")
            composeRule.onNodeWithText("12.34").assertIsDisplayed()
            click("Save")
            await("Home")
            assertEquals(1, runBlocking { room.observeAllActiveExpenses().first().size })
            click("Settings")
            chooseWeek(3)
            click("Save week start")
            await("Week start saved")
            reopen()
            await("Home")
            click("Settings")
            composeRule.onNodeWithText("Base currency: INR").assertIsDisplayed()
            composeRule.onNodeWithText("Wednesday").assertIsDisplayed()
            assertEquals(1, runBlocking { room.observeAllActiveExpenses().first().size })
            assertEquals(
                "INR",
                runBlocking {
                    room
                        .observeAllActiveExpenses()
                        .first()
                        .single()
                        .amount.currency.value
                },
            )
        } finally {
            composeRule.runOnUiThread { owner.value.viewModelStore.clear() }
            runBlocking { job.cancelAndJoin() }
            room.close()
            context.deleteDatabase(databaseName)
            preferencesFile.delete()
        }
    }

    private fun newOwner(): ViewModelStoreOwner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    private fun await(
        text: String,
        absent: Boolean = false,
    ) {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() == absent }
    }

    private fun click(text: String) {
        await(text)
        composeRule.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun chooseWeek(day: Int) {
        composeRule.onNodeWithTag("week_start_choice").performScrollTo().performClick()
        composeRule.onNodeWithTag("week_day_" + day).performScrollTo().performClick()
    }
}
