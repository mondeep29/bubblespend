package com.bubblespend.core.application

import com.bubblespend.core.domain.CalendarPeriods
import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.UtcInterval
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class SetupSettings(
    val confirmedCurrency: CurrencyCode? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val completed: Boolean = false,
    val draftCurrency: String? = null,
    val draftWeekStart: Int? = null,
)

/** Completion must atomically persist currency, week start, and the completion marker. */
interface SetupSettingsRepository {
    suspend fun loadSettings(): StoreResult<SetupSettings>

    suspend fun saveSetupDraft(
        currency: String,
        weekStart: Int,
    ): StoreResult<Unit>

    suspend fun completeSetup(
        currency: CurrencyCode,
        weekStart: DayOfWeek,
    ): StoreResult<SetupSettings>

    suspend fun changeWeekStart(weekStart: DayOfWeek): StoreResult<SetupSettings>
}

class SetupSettingsController(
    private val repository: SetupSettingsRepository,
) {
    suspend fun complete(
        currency: String,
        weekStart: Int,
    ): StoreResult<SetupSettings> {
        val code =
            try {
                CurrencyCode.of(currency.uppercase(Locale.ROOT)).also { require(it.fractionDigits >= 0) }
            } catch (_: IllegalArgumentException) {
                return StoreResult.ValidationFailure("currency")
            }
        val day = day(weekStart) ?: return StoreResult.ValidationFailure("weekStart")
        val result = repository.completeSetup(code, day)
        if (result !is StoreResult.Success) return result
        val readable = repository.loadSettings()
        return if (readable is StoreResult.Success &&
            readable.value == result.value &&
            readable.value.completed &&
            readable.value.confirmedCurrency == code
        ) {
            readable
        } else {
            StoreResult.StorageFailure()
        }
    }

    suspend fun changeWeekStart(weekStart: Int): StoreResult<SetupSettings> {
        val day = day(weekStart) ?: return StoreResult.ValidationFailure("weekStart")
        val result = repository.changeWeekStart(day)
        if (result !is StoreResult.Success) return result
        val readable = repository.loadSettings()
        return if (readable is StoreResult.Success && readable.value == result.value && readable.value.weekStart == day) {
            readable
        } else {
            StoreResult.StorageFailure()
        }
    }

    private fun day(value: Int): DayOfWeek? = if (value in 1..7) DayOfWeek.of(value) else null
}

/** Reads the effective preference for each query; the caller supplies the current device timezone. */
class WeeklyReportingPeriod(
    private val repository: SetupSettingsRepository,
) {
    suspend fun interval(
        date: LocalDate,
        zone: ZoneId,
    ): StoreResult<UtcInterval> =
        when (val settings = repository.loadSettings()) {
            is StoreResult.Success -> StoreResult.Success(CalendarPeriods.week(date, zone, settings.value.weekStart))
            else -> StoreResult.StorageFailure()
        }
}
