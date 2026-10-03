package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BaseCurrencyConfirmationTest {
    private val usd = CurrencyCode.of("USD")
    private val cad = CurrencyCode.of("CAD")

    @Test
    fun localeCurrencyRemainsUnconfirmedUntilExplicitChoiceAndConfirmationSurvivesRestart() =
        runBlocking {
            val repository = FakeBaseCurrencyRepository()
            val firstStartup = CurrencyConfirmationController(repository, Locale.US)

            val suggested = firstStartup.load() as StoreResult.Success<CurrencyConfirmationState>

            assertEquals(usd, suggested.value.suggestedCurrency)
            assertNull(suggested.value.confirmedCurrency)

            val confirmed = firstStartup.confirm(usd) as StoreResult.Success<CurrencyConfirmationState>
            assertEquals(usd, confirmed.value.confirmedCurrency)

            val restarted = CurrencyConfirmationController(repository, Locale.CANADA)
            val restored = restarted.load() as StoreResult.Success<CurrencyConfirmationState>
            assertEquals(cad, restored.value.suggestedCurrency)
            assertEquals(usd, restored.value.confirmedCurrency)
            assertEquals(1, repository.confirmationWrites)
        }

    private class FakeBaseCurrencyRepository : BaseCurrencyRepository {
        private var confirmed: CurrencyCode? = null
        var confirmationWrites: Int = 0
            private set

        override suspend fun loadConfirmedCurrency(): StoreResult<CurrencyCode?> = StoreResult.Success(confirmed)

        override suspend fun confirmCurrency(currency: CurrencyCode): StoreResult<CurrencyCode> {
            confirmationWrites += 1
            confirmed = currency
            return StoreResult.Success(currency)
        }
    }
}
