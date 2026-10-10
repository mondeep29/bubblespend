package com.bubblespend.core.application

import com.bubblespend.core.domain.CalendarPeriods
import com.bubblespend.core.domain.CurrencyCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class SetupSettingsAcceptanceTest {
    @Test fun freshSettingsHaveMondayAndNoConfirmation() =
        runBlocking {
            val repository = InMemorySetupSettingsRepository()
            val settings = (repository.loadSettings() as StoreResult.Success).value
            assertEquals(DayOfWeek.MONDAY, settings.weekStart)
            assertNull(settings.confirmedCurrency)
            assertFalse(settings.completed)
        }

    @Test fun draftChoicesDoNotConfirmCurrencyAndCompletionClearsDraft() =
        runBlocking {
            val repository = InMemorySetupSettingsRepository()
            repository.saveSetupDraft("INR", 7)
            val draft = (repository.loadSettings() as StoreResult.Success).value
            assertEquals("INR", draft.draftCurrency)
            assertEquals(7, draft.draftWeekStart)
            assertNull((repository.loadConfirmedCurrency() as StoreResult.Success).value)
            val saved = (SetupSettingsController(repository).complete("inr", 7) as StoreResult.Success).value
            assertTrue(saved.completed)
            assertEquals(CurrencyCode.of("INR"), saved.confirmedCurrency)
            assertEquals(DayOfWeek.SUNDAY, saved.weekStart)
            assertNull(saved.draftCurrency)
        }

    @Test fun invalidCurrencyOrWeekCannotCommitOrDiscardChoices() =
        runBlocking {
            val repository = InMemorySetupSettingsRepository()
            val controller = SetupSettingsController(repository)
            for (code in listOf("", "US", "ZZZ", "XXX", " USD ")) {
                repository.saveSetupDraft(code, 7)
                assertTrue(controller.complete(code, 7) is StoreResult.ValidationFailure)
                assertEquals(code, (repository.loadSettings() as StoreResult.Success).value.draftCurrency)
                assertNull((repository.loadConfirmedCurrency() as StoreResult.Success).value)
            }
            for (week in listOf(0, 8, -1)) {
                assertTrue(controller.complete("USD", week) is StoreResult.ValidationFailure)
            }
        }

    @Test fun currencyCannotChangeAndRepeatedCompletionCannotReapplyOldWeek() =
        runBlocking {
            val repository = InMemorySetupSettingsRepository()
            val controller = SetupSettingsController(repository)
            controller.complete("USD", 7)
            controller.changeWeekStart(3)
            assertTrue(controller.complete("INR", 1) is StoreResult.StorageFailure)
            assertTrue(repository.confirmCurrency(CurrencyCode.of("INR")) is StoreResult.StorageFailure)
            val repeated = (controller.complete("USD", 7) as StoreResult.Success).value
            assertEquals(DayOfWeek.WEDNESDAY, repeated.weekStart)
            assertEquals(CurrencyCode.of("USD"), repeated.confirmedCurrency)
        }

    @Test fun confirmedLegacyCurrencyResumesWithoutReplacement() =
        runBlocking {
            val repository = InMemorySetupSettingsRepository()
            repository.confirmCurrency(CurrencyCode.of("USD"))
            assertFalse((repository.loadSettings() as StoreResult.Success).value.completed)
            assertTrue(SetupSettingsController(repository).complete("INR", 1) is StoreResult.StorageFailure)
            assertTrue(SetupSettingsController(repository).complete("USD", 6) is StoreResult.Success)
        }

    @Test fun weeklyQueriesReadAllPersistedWeekdaysInTheSuppliedCurrentTimezone() =
        runBlocking {
            val repository = InMemorySetupSettingsRepository()
            val controller = SetupSettingsController(repository)
            controller.complete("USD", 1)
            val periods = WeeklyReportingPeriod(repository)
            val date = LocalDate.of(2026, 3, 8)
            for (day in DayOfWeek.entries) {
                controller.changeWeekStart(day.value)
                for (zone in listOf(ZoneId.of("America/New_York"), ZoneId.of("Asia/Kolkata"))) {
                    val interval = (periods.interval(date, zone) as StoreResult.Success).value
                    assertEquals(CalendarPeriods.week(date, zone, day), interval)
                    assertEquals(day, interval.start.atZone(zone).dayOfWeek)
                }
            }
            assertTrue(controller.changeWeekStart(9) is StoreResult.ValidationFailure)
        }

    @Test fun acknowledgedButUnreadableCompletionCannotReportSuccess() =
        runBlocking {
            val stored = InMemorySetupSettingsRepository()
            val earlyAcknowledgement =
                object : SetupSettingsRepository by stored {
                    override suspend fun completeSetup(
                        currency: CurrencyCode,
                        weekStart: DayOfWeek,
                    ): StoreResult<SetupSettings> =
                        StoreResult.Success(SetupSettings(confirmedCurrency = currency, weekStart = weekStart, completed = true))
                }
            assertTrue(SetupSettingsController(earlyAcknowledgement).complete("USD", 7) is StoreResult.StorageFailure)
            assertNull((stored.loadConfirmedCurrency() as StoreResult.Success).value)
        }

    @Test fun failureDoesNotBecomeSuccessAndWeeklyReadFailurePropagates() =
        runBlocking {
            val stored = InMemorySetupSettingsRepository()
            stored.saveSetupDraft("USD", 7)
            val failing =
                object : SetupSettingsRepository by stored {
                    override suspend fun completeSetup(
                        currency: CurrencyCode,
                        weekStart: DayOfWeek,
                    ): StoreResult<SetupSettings> = StoreResult.StorageFailure()

                    override suspend fun loadSettings(): StoreResult<SetupSettings> = StoreResult.StorageFailure()
                }
            assertTrue(SetupSettingsController(failing).complete("USD", 7) is StoreResult.StorageFailure)
            assertNull((stored.loadConfirmedCurrency() as StoreResult.Success).value)
            assertTrue(WeeklyReportingPeriod(failing).interval(LocalDate.of(2026, 1, 1), ZoneId.of("UTC")) is StoreResult.StorageFailure)
            assertTrue(SetupSettingsController(stored).complete("USD", 7) is StoreResult.Success)
        }
}
