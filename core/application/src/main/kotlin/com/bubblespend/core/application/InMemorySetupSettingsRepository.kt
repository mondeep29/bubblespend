package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek

class InMemorySetupSettingsRepository(
    initial: SetupSettings = SetupSettings(),
) : SetupSettingsRepository,
    BaseCurrencyRepository {
    private val mutex = Mutex()
    private var settings = initial

    override suspend fun loadSettings(): StoreResult<SetupSettings> = mutex.withLock { StoreResult.Success(settings) }

    override suspend fun loadConfirmedCurrency(): StoreResult<CurrencyCode?> =
        mutex.withLock {
            StoreResult.Success(settings.confirmedCurrency)
        }

    override suspend fun saveSetupDraft(
        currency: String,
        weekStart: Int,
    ): StoreResult<Unit> =
        mutex.withLock {
            if (!settings.completed) settings = settings.copy(draftCurrency = currency, draftWeekStart = weekStart)
            StoreResult.Success(Unit)
        }

    override suspend fun confirmCurrency(currency: CurrencyCode): StoreResult<CurrencyCode> =
        mutex.withLock {
            if (settings.confirmedCurrency != null && settings.confirmedCurrency != currency) {
                StoreResult.StorageFailure()
            } else {
                settings = settings.copy(confirmedCurrency = currency)
                StoreResult.Success(currency)
            }
        }

    override suspend fun completeSetup(
        currency: CurrencyCode,
        weekStart: DayOfWeek,
    ): StoreResult<SetupSettings> =
        mutex.withLock {
            if (currency.fractionDigits < 0 || settings.confirmedCurrency != null && settings.confirmedCurrency != currency) {
                StoreResult.StorageFailure()
            } else {
                if (!settings.completed) {
                    settings =
                        settings.copy(
                            confirmedCurrency = currency,
                            weekStart = weekStart,
                            completed = true,
                            draftCurrency = null,
                            draftWeekStart = null,
                        )
                }
                StoreResult.Success(settings)
            }
        }

    override suspend fun changeWeekStart(weekStart: DayOfWeek): StoreResult<SetupSettings> =
        mutex.withLock {
            if (!settings.completed) {
                StoreResult.StorageFailure()
            } else {
                settings = settings.copy(weekStart = weekStart)
                StoreResult.Success(settings)
            }
        }
}
