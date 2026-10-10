package com.bubblespend.core.application

import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

sealed interface StoreResult<out T> {
    data class Success<T>(
        val value: T,
    ) : StoreResult<T>

    data class ValidationFailure(
        val message: String,
    ) : StoreResult<Nothing>

    data class Conflict(
        val currentVersion: Long? = null,
    ) : StoreResult<Nothing>

    data object NotFound : StoreResult<Nothing>

    data object InactiveReference : StoreResult<Nothing>

    data class StorageFailure(
        val cause: Throwable? = null,
    ) : StoreResult<Nothing>
}

interface ExpenseRepository {
    fun observeAllActiveExpenses(): Flow<List<Expense>> =
        observeExpenses(java.time.Instant.parse("0001-01-01T00:00:00Z"), java.time.Instant.parse("9999-12-31T23:59:59Z"))

    suspend fun createExpense(
        expense: NewExpense,
        token: UUID,
    ): StoreResult<Expense>

    suspend fun editExpense(
        id: UUID,
        expectedVersion: Long,
        expense: NewExpense,
        token: UUID,
    ): StoreResult<Expense>

    suspend fun deleteExpense(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Expense>

    suspend fun undoDelete(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Expense>

    suspend fun getExpense(id: UUID): Expense?

    fun observeExpenses(
        startInclusive: java.time.Instant,
        endExclusive: java.time.Instant,
    ): Flow<List<Expense>>
}

interface CategoryRepository {
    suspend fun createCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        token: UUID,
    ): StoreResult<Category>

    suspend fun renameCategory(
        id: UUID,
        expectedVersion: Long,
        name: String,
        token: UUID,
    ): StoreResult<Category>

    suspend fun archiveCategory(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Category>

    suspend fun getCategory(id: UUID): Category?

    fun observeCategories(): Flow<List<Category>>
}

interface PaymentMethodRepository {
    suspend fun createPaymentMethod(
        name: String,
        token: UUID,
    ): StoreResult<PaymentMethod>

    suspend fun renamePaymentMethod(
        id: UUID,
        expectedVersion: Long,
        name: String,
        token: UUID,
    ): StoreResult<PaymentMethod>

    suspend fun archivePaymentMethod(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<PaymentMethod>

    suspend fun setDefaultPaymentMethod(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<PaymentMethod>

    suspend fun getPaymentMethod(id: UUID): PaymentMethod?

    fun observePaymentMethods(): Flow<List<PaymentMethod>>
}

/** In-memory repository fake with the same validation and version rules expected of Room. */
class InMemoryExpenseStore(
    private val clock: Clock,
    private val uuidGenerator: UuidGenerator,
) : ExpenseRepository,
    CategoryRepository,
    PaymentMethodRepository {
    private val lock = Any()
    private val expenses = linkedMapOf<UUID, Expense>()
    private val categories = linkedMapOf<UUID, Category>()
    private val methods = linkedMapOf<UUID, PaymentMethod>()
    private val receipts = mutableMapOf<UUID, MutationReceipt>()
    private val expenseState = MutableStateFlow<List<Expense>>(emptyList())
    private val categoryState = MutableStateFlow<List<Category>>(emptyList())
    private val methodState = MutableStateFlow<List<PaymentMethod>>(emptyList())

    override suspend fun createExpense(
        expense: NewExpense,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:create") {
            validateExpense(expense)?.let { return@mutate it }
            val now = clock.instant()
            val record =
                Expense(
                    uuidGenerator.next(),
                    expense.amount,
                    expense.occurredAt,
                    expense.categoryId,
                    expense.paymentMethodId,
                    expense.merchant.trim(),
                    expense.note.trim(),
                    now,
                    now,
                )
            expenses[record.id] = record
            publishExpenses()
            StoreResult.Success(record)
        }

    override suspend fun editExpense(
        id: UUID,
        expectedVersion: Long,
        expense: NewExpense,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:edit") {
            validateExpense(expense)?.let { return@mutate it }
            val old = expenses[id] ?: return@mutate StoreResult.NotFound
            if (old.deletedAt != null) return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val updated =
                old.copy(
                    amount = expense.amount,
                    occurredAt = expense.occurredAt,
                    categoryId = expense.categoryId,
                    paymentMethodId = expense.paymentMethodId,
                    merchant = expense.merchant.trim(),
                    note = expense.note.trim(),
                    updatedAt = clock.instant(),
                    version = old.version + 1,
                )
            expenses[id] = updated
            publishExpenses()
            StoreResult.Success(updated)
        }

    override suspend fun deleteExpense(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:delete") {
            val old = expenses[id] ?: return@mutate StoreResult.NotFound
            if (old.deletedAt != null) return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val updated = old.copy(deletedAt = clock.instant(), updatedAt = clock.instant(), version = old.version + 1)
            expenses[id] = updated
            publishExpenses()
            StoreResult.Success(updated)
        }

    override suspend fun undoDelete(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:undo") {
            val old = expenses[id] ?: return@mutate StoreResult.NotFound
            if (old.deletedAt == null) return@mutate StoreResult.Conflict(old.version)
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val updated = old.copy(deletedAt = null, updatedAt = clock.instant(), version = old.version + 1)
            expenses[id] = updated
            publishExpenses()
            StoreResult.Success(updated)
        }

    override suspend fun getExpense(id: UUID): Expense? = synchronized(lock) { expenses[id] }

    override fun observeAllActiveExpenses(): Flow<List<Expense>> =
        expenseState.asStateFlow().map { records ->
            records.filter { it.deletedAt == null }.sortedByDescending { it.occurredAt }
        }

    suspend fun activeExpenses(
        startInclusive: java.time.Instant,
        endExclusive: java.time.Instant,
    ): List<Expense> =
        synchronized(lock) {
            if (startInclusive >= endExclusive) {
                emptyList()
            } else {
                expenses.values
                    .filter { it.deletedAt == null && !it.occurredAt.isBefore(startInclusive) && it.occurredAt.isBefore(endExclusive) }
                    .sortedBy { it.occurredAt }
            }
        }

    override fun observeExpenses(
        startInclusive: java.time.Instant,
        endExclusive: java.time.Instant,
    ): Flow<List<Expense>> =
        expenseState.asStateFlow().map { expenses ->
            if (startInclusive >= endExclusive) {
                emptyList()
            } else {
                expenses
                    .filter { it.deletedAt == null && !it.occurredAt.isBefore(startInclusive) && it.occurredAt.isBefore(endExclusive) }
                    .sortedBy { it.occurredAt }
            }
        }

    override suspend fun createCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        token: UUID,
    ): StoreResult<Category> =
        mutate(token, "category:create") {
            val normalized =
                try {
                    NameValidation.validate(name)
                } catch (e: IllegalArgumentException) {
                    return@mutate StoreResult.ValidationFailure(e.message.orEmpty())
                }
            try {
                CategoryVisualValidation.validate(iconKey, colorKey)
            } catch (e: IllegalArgumentException) {
                return@mutate StoreResult.ValidationFailure(e.message.orEmpty())
            }
            if (
                categories.values.any {
                    it.archivedAt == null && NameValidation.validate(it.name).uniquenessKey == normalized.uniquenessKey
                }
            ) {
                return@mutate StoreResult.ValidationFailure("An active category already uses this name")
            }
            val now = clock.instant()
            val value = Category(uuidGenerator.next(), normalized.displayName, iconKey, colorKey, now, now)
            categories[value.id] = value
            publishCategories()
            StoreResult.Success(value)
        }

    fun createCategoryWithId(
        id: UUID,
        name: String,
        iconKey: String,
        colorKey: String,
    ): StoreResult<Category> =
        synchronized(lock) {
            categories[id]?.let { return@synchronized StoreResult.Success(it) }
            val normalized =
                try {
                    NameValidation.validate(name)
                } catch (e: IllegalArgumentException) {
                    return@synchronized StoreResult.ValidationFailure(e.message.orEmpty())
                }
            try {
                CategoryVisualValidation.validate(iconKey, colorKey)
            } catch (e: IllegalArgumentException) {
                return@synchronized StoreResult.ValidationFailure(e.message.orEmpty())
            }
            if (
                categories.values.any {
                    it.archivedAt == null && NameValidation.validate(it.name).uniquenessKey == normalized.uniquenessKey
                }
            ) {
                return@synchronized StoreResult.ValidationFailure("Duplicate category")
            }
            val now = clock.instant()
            val value = Category(id, normalized.displayName, iconKey, colorKey, now, now)
            categories[id] = value
            publishCategories()
            StoreResult.Success(value)
        }

    override suspend fun renameCategory(
        id: UUID,
        expectedVersion: Long,
        name: String,
        token: UUID,
    ): StoreResult<Category> =
        mutate(token, "category:rename") {
            val value = categories[id] ?: return@mutate StoreResult.NotFound
            if (value.version != expectedVersion) return@mutate StoreResult.Conflict(value.version)
            val normalized =
                try {
                    NameValidation.validate(name)
                } catch (e: IllegalArgumentException) {
                    return@mutate StoreResult.ValidationFailure(e.message.orEmpty())
                }
            if (
                categories.values.any {
                    it.id != id &&
                        it.archivedAt == null &&
                        NameValidation.validate(it.name).uniquenessKey == normalized.uniquenessKey
                }
            ) {
                return@mutate StoreResult.ValidationFailure("An active category already uses this name")
            }
            val updated = value.copy(name = normalized.displayName, updatedAt = clock.instant(), version = value.version + 1)
            categories[id] = updated
            publishCategories()
            StoreResult.Success(updated)
        }

    override suspend fun archiveCategory(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Category> =
        mutate(token, "category:archive") {
            val value = categories[id] ?: return@mutate StoreResult.NotFound
            if (value.version != expectedVersion) return@mutate StoreResult.Conflict(value.version)
            if (value.archivedAt != null) return@mutate StoreResult.Success(value)
            val updated = value.copy(archivedAt = clock.instant(), updatedAt = clock.instant(), version = value.version + 1)
            categories[id] = updated
            publishCategories()
            StoreResult.Success(updated)
        }

    override suspend fun getCategory(id: UUID): Category? = synchronized(lock) { categories[id] }

    suspend fun activeCategories(): List<Category> = synchronized(lock) { categories.values.filter { it.archivedAt == null } }

    override fun observeCategories(): Flow<List<Category>> = categoryState.asStateFlow()

    override suspend fun createPaymentMethod(
        name: String,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:create") {
            val normalized =
                try {
                    NameValidation.validate(name)
                } catch (e: IllegalArgumentException) {
                    return@mutate StoreResult.ValidationFailure(e.message.orEmpty())
                }
            if (
                methods.values.any {
                    it.archivedAt == null && NameValidation.validate(it.name).uniquenessKey == normalized.uniquenessKey
                }
            ) {
                return@mutate StoreResult.ValidationFailure("An active payment method already uses this name")
            }
            val now = clock.instant()
            val value = PaymentMethod(uuidGenerator.next(), normalized.displayName, false, now, now)
            methods[value.id] = value
            publishMethods()
            StoreResult.Success(value)
        }

    fun createPaymentMethodWithId(
        id: UUID,
        name: String,
        isDefault: Boolean,
    ): StoreResult<PaymentMethod> =
        synchronized(lock) {
            methods[id]?.let { return@synchronized StoreResult.Success(it) }
            val normalized =
                try {
                    NameValidation.validate(name)
                } catch (e: IllegalArgumentException) {
                    return@synchronized StoreResult.ValidationFailure(e.message.orEmpty())
                }
            if (
                methods.values.any {
                    it.archivedAt == null && NameValidation.validate(it.name).uniquenessKey == normalized.uniquenessKey
                }
            ) {
                return@synchronized StoreResult.ValidationFailure("Duplicate payment method")
            }
            if (isDefault) {
                methods.replaceAll { _, old ->
                    if (old.isDefault) old.copy(isDefault = false, version = old.version + 1, updatedAt = clock.instant()) else old
                }
            }
            val now = clock.instant()
            val value = PaymentMethod(id, normalized.displayName, isDefault, now, now)
            methods[id] = value
            publishMethods()
            StoreResult.Success(value)
        }

    override suspend fun renamePaymentMethod(
        id: UUID,
        expectedVersion: Long,
        name: String,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:rename") {
            val value = methods[id] ?: return@mutate StoreResult.NotFound
            if (value.version != expectedVersion) return@mutate StoreResult.Conflict(value.version)
            val normalized =
                try {
                    NameValidation.validate(name)
                } catch (e: IllegalArgumentException) {
                    return@mutate StoreResult.ValidationFailure(e.message.orEmpty())
                }
            if (
                methods.values.any {
                    it.id != id &&
                        it.archivedAt == null &&
                        NameValidation.validate(it.name).uniquenessKey == normalized.uniquenessKey
                }
            ) {
                return@mutate StoreResult.ValidationFailure("An active payment method already uses this name")
            }
            val updated = value.copy(name = normalized.displayName, updatedAt = clock.instant(), version = value.version + 1)
            methods[id] = updated
            publishMethods()
            StoreResult.Success(updated)
        }

    override suspend fun archivePaymentMethod(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:archive") {
            val value = methods[id] ?: return@mutate StoreResult.NotFound
            if (value.version != expectedVersion) return@mutate StoreResult.Conflict(value.version)
            if (value.archivedAt != null) return@mutate StoreResult.Success(value)
            val updated =
                value.copy(
                    archivedAt = clock.instant(),
                    isDefault = false,
                    updatedAt = clock.instant(),
                    version =
                        value.version + 1,
                )
            methods[id] = updated
            publishMethods()
            StoreResult.Success(updated)
        }

    override suspend fun setDefaultPaymentMethod(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:set_default") {
            val value = methods[id] ?: return@mutate StoreResult.NotFound
            if (value.archivedAt != null) return@mutate StoreResult.InactiveReference
            if (value.version != expectedVersion) return@mutate StoreResult.Conflict(value.version)
            methods.replaceAll { key, old ->
                when {
                    key == id && !old.isDefault -> old.copy(isDefault = true, updatedAt = clock.instant(), version = old.version + 1)
                    key != id && old.isDefault -> old.copy(isDefault = false, updatedAt = clock.instant(), version = old.version + 1)
                    else -> old
                }
            }
            publishMethods()
            StoreResult.Success(methods.getValue(id))
        }

    override suspend fun getPaymentMethod(id: UUID): PaymentMethod? = synchronized(lock) { methods[id] }

    suspend fun activePaymentMethods(): List<PaymentMethod> = synchronized(lock) { methods.values.filter { it.archivedAt == null } }

    override fun observePaymentMethods(): Flow<List<PaymentMethod>> = methodState.asStateFlow()

    suspend fun seedDefaults() =
        synchronized(lock) {
            val specs =
                listOf(
                    "Food" to "food",
                    "Transport" to "transport",
                    "Housing" to "housing",
                    "Utilities" to "utilities",
                    "Health" to "health",
                    "Other" to "other",
                )
            val colors = listOf("red", "orange", "yellow", "green", "blue", "purple")
            specs.forEachIndexed { index, (name, icon) ->
                createCategoryWithId(defaultId("category:$name"), name, icon, colors[index])
            }
            createPaymentMethodWithId(defaultId("payment:Cash"), "Cash", true)
            createPaymentMethodWithId(defaultId("payment:Card"), "Card", false)
        }

    private fun validateExpense(expense: NewExpense): StoreResult<Nothing>? {
        if (expense.amount.minorUnits <= 0L) return invalidAmountFailure()
        if (expense.occurredAt.isAfter(clock.instant())) return futureTimeFailure()
        if (!hasValidTextLengths(expense)) return textLengthFailure()
        if (!hasActiveReferences(expense)) return StoreResult.InactiveReference
        return null
    }

    private fun hasValidTextLengths(expense: NewExpense): Boolean =
        expense.merchant.trim().length <= 100 && expense.note.trim().length <= 500

    private fun hasActiveReferences(expense: NewExpense): Boolean {
        val category = categories[expense.categoryId] ?: return false
        val method = methods[expense.paymentMethodId] ?: return false
        return category.archivedAt == null && method.archivedAt == null
    }

    private fun invalidAmountFailure() = StoreResult.ValidationFailure("Amount must be positive")

    private fun futureTimeFailure() = StoreResult.ValidationFailure("Occurred time cannot be in the future")

    private fun textLengthFailure() = StoreResult.ValidationFailure("Text exceeds the approved limit")

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> mutate(
        token: UUID,
        operationKey: String,
        operation: () -> StoreResult<T>,
    ): StoreResult<T> =
        synchronized(lock) {
            receipts[token]?.let { receipt ->
                if (receipt.operationKey != operationKey) return@synchronized StoreResult.Conflict()
                return@synchronized receipt.result as StoreResult<T>
            }
            val result = operation()
            if (result is StoreResult.Success) {
                receipts[token] = MutationReceipt(operationKey, result)
            }
            result
        }

    private data class MutationReceipt(
        val operationKey: String,
        val result: StoreResult<*>,
    )

    private fun publishExpenses() {
        expenseState.value = expenses.values.toList()
    }

    private fun publishCategories() {
        categoryState.value = categories.values.filter { it.archivedAt == null }.toList()
    }

    private fun publishMethods() {
        methodState.value = methods.values.filter { it.archivedAt == null }.toList()
    }

    private fun defaultId(value: String): UUID = UUID.nameUUIDFromBytes("BubbleSpend:$value".toByteArray(Charsets.UTF_8))
}
