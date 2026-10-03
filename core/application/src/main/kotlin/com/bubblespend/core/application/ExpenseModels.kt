package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.LocalizedMoneyParser
import com.bubblespend.core.domain.Money
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

enum class ExpenseField {
    AMOUNT,
    CATEGORY,
    PAYMENT_METHOD,
    OCCURRED_AT,
    MERCHANT,
    NOTE,
}

data class ExpenseDraft(
    val amountText: String,
    val categoryId: UUID?,
    val paymentMethodId: UUID?,
    val occurredAt: Instant,
    val merchant: String,
    val note: String,
)

data class NewExpense(
    val amount: Money,
    val occurredAt: Instant,
    val categoryId: UUID,
    val paymentMethodId: UUID,
    val merchant: String,
    val note: String,
)

sealed interface ExpenseValidation {
    data class Valid(
        val expense: NewExpense,
    ) : ExpenseValidation

    data class Invalid(
        val fields: Set<ExpenseField>,
    ) : ExpenseValidation
}

object ExpenseEntryValidator {
    fun validate(
        draft: ExpenseDraft,
        currency: CurrencyCode,
        locale: Locale,
        clock: Clock,
    ): ExpenseValidation {
        val errors = mutableSetOf<ExpenseField>()
        val amount =
            try {
                LocalizedMoneyParser.parse(draft.amountText, currency, locale)
            } catch (_: RuntimeException) {
                null
            }
        if (amount == null || amount.minorUnits <= 0L) errors += ExpenseField.AMOUNT
        if (draft.categoryId == null) errors += ExpenseField.CATEGORY
        if (draft.paymentMethodId == null) errors += ExpenseField.PAYMENT_METHOD
        if (draft.occurredAt.isAfter(clock.instant())) errors += ExpenseField.OCCURRED_AT
        val merchant = draft.merchant.trim()
        val note = draft.note.trim()
        if (merchant.length > 100) errors += ExpenseField.MERCHANT
        if (note.length > 500) errors += ExpenseField.NOTE
        if (errors.isNotEmpty()) return ExpenseValidation.Invalid(errors)
        return ExpenseValidation.Valid(
            NewExpense(
                amount!!,
                draft.occurredAt,
                draft.categoryId!!,
                draft.paymentMethodId!!,
                merchant,
                note,
            ),
        )
    }
}

enum class LocalSyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCED,
}

data class Expense(
    val id: UUID,
    val amount: Money,
    val occurredAt: Instant,
    val categoryId: UUID,
    val paymentMethodId: UUID,
    val merchant: String,
    val note: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val version: Long = 1,
    val syncStatus: LocalSyncStatus = LocalSyncStatus.LOCAL_ONLY,
)

data class Category(
    val id: UUID,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val version: Long = 1,
    val syncStatus: LocalSyncStatus = LocalSyncStatus.LOCAL_ONLY,
)

data class PaymentMethod(
    val id: UUID,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val version: Long = 1,
    val syncStatus: LocalSyncStatus = LocalSyncStatus.LOCAL_ONLY,
)

data class NormalizedName(
    val displayName: String,
    val uniquenessKey: String,
)

object NameValidation {
    fun validate(name: String): NormalizedName {
        val display = name.trim()
        require(display.length in 1..40) { "Name must contain 1 to 40 characters" }
        val uniquenessKey = display.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        return NormalizedName(display, uniquenessKey)
    }
}

object CategoryVisualValidation {
    private val icons =
        setOf(
            "food",
            "transport",
            "housing",
            "utilities",
            "health",
            "other",
        )
    private val colors =
        setOf(
            "red",
            "orange",
            "yellow",
            "green",
            "blue",
            "purple",
            "neutral",
        )

    fun validate(
        iconKey: String,
        colorKey: String,
    ) {
        require(iconKey in icons) { "Unsupported category icon" }
        require(colorKey in colors) { "Unsupported category color" }
    }
}
