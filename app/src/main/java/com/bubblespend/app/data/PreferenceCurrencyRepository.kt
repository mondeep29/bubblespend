package com.bubblespend.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bubblespend.core.application.BaseCurrencyRepository
import com.bubblespend.core.application.SetupSettings
import com.bubblespend.core.application.SetupSettingsRepository
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.CurrencyCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek

private val Context.currencyStore by preferencesDataStore(name = "currency_confirmation")

class PreferenceCurrencyRepository(
    private val store: DataStore<Preferences>,
) : BaseCurrencyRepository,
    SetupSettingsRepository {
    constructor(context: Context) : this(context.applicationContext.currencyStore)

    private val currencyKey = stringPreferencesKey("confirmed_base_currency")
    private val weekKey = intPreferencesKey("week_start")
    private val completedKey = booleanPreferencesKey("setup_completed")
    private val draftCurrencyKey = stringPreferencesKey("setup_currency_choice")
    private val draftWeekKey = intPreferencesKey("setup_week_choice")

    override suspend fun loadConfirmedCurrency(): StoreResult<CurrencyCode?> = guarded { decode(store.data.first()).confirmedCurrency }

    override suspend fun confirmCurrency(currency: CurrencyCode): StoreResult<CurrencyCode> =
        guarded {
            store.edit { preferences ->
                requireFixedCurrency(preferences, currency)
                preferences[currencyKey] = currency.value
            }
            currency
        }

    override suspend fun loadSettings(): StoreResult<SetupSettings> = guarded { decode(store.data.first()) }

    override suspend fun saveSetupDraft(
        currency: String,
        weekStart: Int,
    ): StoreResult<Unit> =
        guarded {
            store.edit { preferences ->
                if (preferences[completedKey] != true) {
                    preferences[draftCurrencyKey] = currency
                    preferences[draftWeekKey] = weekStart
                }
            }
            Unit
        }

    override suspend fun completeSetup(
        currency: CurrencyCode,
        weekStart: DayOfWeek,
    ): StoreResult<SetupSettings> =
        guarded {
            require(currency.fractionDigits >= 0)
            decode(
                store.edit { preferences ->
                    requireFixedCurrency(preferences, currency)
                    if (preferences[completedKey] != true) {
                        preferences[currencyKey] = currency.value
                        preferences[weekKey] = weekStart.value
                        preferences[completedKey] = true
                        preferences.remove(draftCurrencyKey)
                        preferences.remove(draftWeekKey)
                    }
                },
            )
        }

    override suspend fun changeWeekStart(weekStart: DayOfWeek): StoreResult<SetupSettings> =
        guarded {
            decode(
                store.edit { preferences ->
                    check(decode(preferences).completed) { "Complete setup first" }
                    preferences[weekKey] = weekStart.value
                },
            )
        }

    private fun requireFixedCurrency(
        preferences: Preferences,
        currency: CurrencyCode,
    ) {
        val existing = preferences[currencyKey]
        check(existing == null || existing == currency.value) { "Base currency already confirmed" }
    }

    private fun decode(preferences: Preferences): SetupSettings {
        val currency = preferences[currencyKey]?.let(CurrencyCode::of)
        val completed = preferences[completedKey] == true
        check(!completed || currency != null) { "Incomplete stored settings" }
        return SetupSettings(
            confirmedCurrency = currency,
            weekStart = DayOfWeek.of(preferences[weekKey] ?: DayOfWeek.MONDAY.value),
            completed = completed,
            draftCurrency = preferences[draftCurrencyKey],
            draftWeekStart = preferences[draftWeekKey],
        )
    }

    private suspend fun <T> guarded(block: suspend () -> T): StoreResult<T> =
        try {
            StoreResult.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            StoreResult.StorageFailure(failure)
        }
}
