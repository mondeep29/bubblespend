package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** SPEC-009-AC-01/04: completion cannot report success before confirmation is readable. */
class SetupCurrencyDurabilityAcceptanceTest {
    @Test
    fun confirmationReportsSuccessOnlyAfterDurableCurrencyCanBeRead() =
        runBlocking {
            val currency = CurrencyCode.of("USD")
            val repository = WriteAcknowledgedBeforeReadableRepository()
            val controller = CurrencyConfirmationController(repository, Locale.US)

            val result = controller.confirm(currency)

            assertTrue("An unreadable confirmation must remain recoverable", result is StoreResult.StorageFailure)
            val loaded = controller.load() as StoreResult.Success<CurrencyConfirmationState>
            assertNull(loaded.value.confirmedCurrency)
        }

    private class WriteAcknowledgedBeforeReadableRepository : BaseCurrencyRepository {
        override suspend fun loadConfirmedCurrency(): StoreResult<CurrencyCode?> = StoreResult.Success(null)

        override suspend fun confirmCurrency(currency: CurrencyCode): StoreResult<CurrencyCode> = StoreResult.Success(currency)
    }
}
