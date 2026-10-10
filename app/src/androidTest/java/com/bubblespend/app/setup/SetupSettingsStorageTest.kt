package com.bubblespend.app.setup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.bubblespend.app.data.PreferenceCurrencyRepository
import com.bubblespend.core.application.SetupSettingsController
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.application.WeeklyReportingPeriod
import com.bubblespend.core.domain.CalendarPeriods
import com.bubblespend.core.domain.CurrencyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class SetupSettingsStorageTest {
    @Test fun draftAndCompletedSettingsSurviveStoreCloseAndReopen() =
        runBlocking {
            val file = newFile()
            var session = Session(file)
            try {
                val fresh = (session.repository.loadSettings() as StoreResult.Success).value
                assertNull(fresh.confirmedCurrency)
                assertEquals(DayOfWeek.MONDAY, fresh.weekStart)
                session.repository.saveSetupDraft("INR", 7)
                session.close()
                session = Session(file)
                val draft = (session.repository.loadSettings() as StoreResult.Success).value
                assertEquals("INR", draft.draftCurrency)
                assertEquals(7, draft.draftWeekStart)
                assertNull(draft.confirmedCurrency)
                assertFalse(draft.completed)
                SetupSettingsController(session.repository).complete("inr", 7)
                session.close()
                session = Session(file)
                val completed = (session.repository.loadSettings() as StoreResult.Success).value
                assertTrue(completed.completed)
                assertEquals(CurrencyCode.of("INR"), completed.confirmedCurrency)
                assertEquals(DayOfWeek.SUNDAY, completed.weekStart)
                assertNull(completed.draftCurrency)
                assertNull(completed.draftWeekStart)
            } finally {
                session.close()
                file.delete()
            }
        }

    @Test fun failedWritesKeepConfirmationAbsentAndPreviousWeekEffectiveUntilRetry() =
        runBlocking {
            val session = Session(newFile())
            try {
                val faults = FaultStore(session.store)
                val repository = PreferenceCurrencyRepository(faults)
                repository.saveSetupDraft("USD", 7)
                faults.failWrite = true
                assertTrue(SetupSettingsController(repository).complete("USD", 7) is StoreResult.StorageFailure)
                assertNull((repository.loadConfirmedCurrency() as StoreResult.Success).value)
                assertEquals("USD", (repository.loadSettings() as StoreResult.Success).value.draftCurrency)
                SetupSettingsController(repository).complete("USD", 7)
                faults.failWrite = true
                assertTrue(repository.changeWeekStart(DayOfWeek.WEDNESDAY) is StoreResult.StorageFailure)
                assertEquals(DayOfWeek.SUNDAY, (repository.loadSettings() as StoreResult.Success).value.weekStart)
                assertTrue(repository.changeWeekStart(DayOfWeek.WEDNESDAY) is StoreResult.Success)
                faults.failRead = true
                assertTrue(repository.loadSettings() is StoreResult.StorageFailure)
                assertTrue(repository.loadConfirmedCurrency() is StoreResult.StorageFailure)
                faults.failRead = false
                assertEquals(CurrencyCode.of("USD"), (repository.loadSettings() as StoreResult.Success).value.confirmedCurrency)
            } finally {
                session.close()
                session.file.delete()
            }
        }

    @Test fun lostCompletionResponseReconcilesAtomicallyAndRetryCannotResetLaterWeek() =
        runBlocking {
            val file = newFile()
            var session = Session(file)
            try {
                val faults = FaultStore(session.store)
                faults.loseResponse = true
                val repository = PreferenceCurrencyRepository(faults)
                assertTrue(SetupSettingsController(repository).complete("USD", 7) is StoreResult.StorageFailure)
                session.close()
                session = Session(file)
                val saved = (session.repository.loadSettings() as StoreResult.Success).value
                assertTrue(saved.completed)
                assertEquals(CurrencyCode.of("USD"), saved.confirmedCurrency)
                assertEquals(DayOfWeek.SUNDAY, saved.weekStart)
                session.repository.changeWeekStart(DayOfWeek.FRIDAY)
                SetupSettingsController(session.repository).complete("USD", 7)
                assertEquals(DayOfWeek.FRIDAY, (session.repository.loadSettings() as StoreResult.Success).value.weekStart)
                assertTrue(SetupSettingsController(session.repository).complete("INR", 1) is StoreResult.StorageFailure)
                assertTrue(session.repository.confirmCurrency(CurrencyCode.of("INR")) is StoreResult.StorageFailure)
            } finally {
                session.close()
                file.delete()
            }
        }

    @Test fun legacyCurrencyIsPreservedAndAllWeekdaysSurviveReopenAndDriveQueries() =
        runBlocking {
            val file = newFile()
            var session = Session(file)
            try {
                session.repository.confirmCurrency(CurrencyCode.of("USD"))
                session.repository.saveSetupDraft("INR", 6)
                session.close()
                session = Session(file)
                assertFalse((session.repository.loadSettings() as StoreResult.Success).value.completed)
                assertTrue(SetupSettingsController(session.repository).complete("INR", 6) is StoreResult.StorageFailure)
                SetupSettingsController(session.repository).complete("USD", 6)
                for (day in DayOfWeek.entries) {
                    session.repository.changeWeekStart(day)
                    session.close()
                    session = Session(file)
                    val periods = WeeklyReportingPeriod(session.repository)
                    val date = LocalDate.of(2026, 3, 8)
                    for (zone in listOf(ZoneId.of("America/New_York"), ZoneId.of("Asia/Kolkata"))) {
                        assertEquals(CalendarPeriods.week(date, zone, day), (periods.interval(date, zone) as StoreResult.Success).value)
                    }
                }
            } finally {
                session.close()
                file.delete()
            }
        }

    private fun newFile(): File =
        File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            UUID.randomUUID().toString() + ".preferences_pb",
        )

    private class Session(
        val file: File,
    ) {
        private val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        val repository = PreferenceCurrencyRepository(store)

        suspend fun close() {
            job.cancelAndJoin()
        }
    }

    private class FaultStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> {
        var failRead = false
        var failWrite = false
        var loseResponse = false
        override val data: Flow<Preferences> get() =
            flow {
                if (failRead) throw IOException("Injected read failure")
                emitAll(delegate.data)
            }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrite) {
                failWrite = false
                throw IOException("Injected write failure")
            }
            val saved = delegate.updateData(transform)
            if (loseResponse) {
                loseResponse = false
                throw IOException("Injected response failure")
            }
            return saved
        }
    }
}
