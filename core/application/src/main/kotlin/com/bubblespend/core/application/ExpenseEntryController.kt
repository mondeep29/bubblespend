package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.Currency
import java.util.Locale
import java.util.UUID

interface BaseCurrencyRepository {
    suspend fun loadConfirmedCurrency(): StoreResult<CurrencyCode?>

    suspend fun confirmCurrency(currency: CurrencyCode): StoreResult<CurrencyCode>
}

data class CurrencyConfirmationState(
    val suggestedCurrency: CurrencyCode?,
    val confirmedCurrency: CurrencyCode?,
)

class CurrencyConfirmationController(
    private val repository: BaseCurrencyRepository,
    private val locale: Locale,
) {
    suspend fun load(): StoreResult<CurrencyConfirmationState> {
        val suggested = runCatching { CurrencyCode.of(Currency.getInstance(locale).currencyCode) }.getOrNull()
        return when (val result = repository.loadConfirmedCurrency()) {
            is StoreResult.Success -> StoreResult.Success(CurrencyConfirmationState(suggested, result.value))
            is StoreResult.StorageFailure -> result
            else -> StoreResult.StorageFailure()
        }
    }

    suspend fun confirm(currency: CurrencyCode): StoreResult<CurrencyConfirmationState> =
        when (val result = repository.confirmCurrency(currency)) {
            is StoreResult.Success -> {
                val suggested = runCatching { CurrencyCode.of(Currency.getInstance(locale).currencyCode) }.getOrNull()
                when (val readable = repository.loadConfirmedCurrency()) {
                    is StoreResult.Success ->
                        if (readable.value == currency) {
                            StoreResult.Success(CurrencyConfirmationState(suggested, readable.value))
                        } else {
                            StoreResult.StorageFailure()
                        }
                    else -> StoreResult.StorageFailure()
                }
            }
            is StoreResult.StorageFailure -> result
            else -> StoreResult.StorageFailure()
        }
}

sealed interface ExpenseEntrySubmission {
    data class Saved(
        val expense: Expense,
    ) : ExpenseEntrySubmission

    data class Invalid(
        val fields: Set<ExpenseField>,
        val draft: ExpenseDraft,
    ) : ExpenseEntrySubmission

    data class RecoverableFailure(
        val draft: ExpenseDraft,
    ) : ExpenseEntrySubmission

    data object CurrencyConfirmationRequired : ExpenseEntrySubmission

    data object Conflict : ExpenseEntrySubmission
}

class ExpenseEntryController(
    private val expenseRepository: ExpenseRepository,
    private val baseCurrencyRepository: BaseCurrencyRepository,
    private val clock: Clock,
    private val locale: Locale,
    private val uuidGenerator: UuidGenerator,
) {
    private val mutex = Mutex()
    private var pendingDraft: ExpenseDraft? = null
    private var pendingToken: UUID? = null
    private var lastSavedDraft: ExpenseDraft? = null
    private var lastSavedExpense: Expense? = null

    suspend fun submit(draft: ExpenseDraft): ExpenseEntrySubmission =
        mutex.withLock {
            if (draft === lastSavedDraft) {
                lastSavedExpense?.let { return@withLock ExpenseEntrySubmission.Saved(it) }
            }
            val currency =
                when (val result = baseCurrencyRepository.loadConfirmedCurrency()) {
                    is StoreResult.Success -> result.value ?: return@withLock ExpenseEntrySubmission.CurrencyConfirmationRequired
                    else -> return@withLock ExpenseEntrySubmission.RecoverableFailure(draft)
                }
            val validated = ExpenseEntryValidator.validate(draft, currency, locale, clock)
            if (validated is ExpenseValidation.Invalid) return@withLock ExpenseEntrySubmission.Invalid(validated.fields, draft)
            val token = if (pendingDraft === draft) pendingToken ?: uuidGenerator.next() else uuidGenerator.next()
            pendingDraft = draft
            pendingToken = token
            return@withLock when (val result = expenseRepository.createExpense((validated as ExpenseValidation.Valid).expense, token)) {
                is StoreResult.Success -> {
                    lastSavedDraft = draft
                    lastSavedExpense = result.value
                    pendingDraft = null
                    pendingToken = null
                    ExpenseEntrySubmission.Saved(result.value)
                }
                is StoreResult.Conflict -> ExpenseEntrySubmission.Conflict
                is StoreResult.InactiveReference ->
                    ExpenseEntrySubmission.Invalid(setOf(ExpenseField.CATEGORY, ExpenseField.PAYMENT_METHOD), draft)
                else -> ExpenseEntrySubmission.RecoverableFailure(draft)
            }
        }
}
