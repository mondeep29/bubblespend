package com.bubblespend.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.withTransaction
import com.bubblespend.core.application.Category
import com.bubblespend.core.application.CategoryRepository
import com.bubblespend.core.application.CategoryVisualValidation
import com.bubblespend.core.application.Expense
import com.bubblespend.core.application.ExpenseRepository
import com.bubblespend.core.application.LocalSyncStatus
import com.bubblespend.core.application.NameValidation
import com.bubblespend.core.application.NewExpense
import com.bubblespend.core.application.PaymentMethod
import com.bubblespend.core.application.PaymentMethodRepository
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.Money
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.Base64
import java.util.UUID

class RoomExpenseStore private constructor(
    private val database: ExpenseDatabase,
    private val clock: Clock,
    private val uuidGenerator: UuidGenerator,
) : ExpenseRepository,
    CategoryRepository,
    PaymentMethodRepository,
    AutoCloseable {
    override suspend fun createExpense(
        expense: NewExpense,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:create", RecordSnapshot::decodeExpense, RecordSnapshot::encodeExpense) {
            validateExpense(expense)?.let { return@mutate it }
            val now = clock.instant()
            val record =
                Expense(
                    id = uuidGenerator.next(),
                    amount = expense.amount,
                    occurredAt = expense.occurredAt,
                    categoryId = expense.categoryId,
                    paymentMethodId = expense.paymentMethodId,
                    merchant = expense.merchant.trim(),
                    note = expense.note.trim(),
                    createdAt = now,
                    updatedAt = now,
                )
            database.expenses().insert(record.toEntity())
            StoreResult.Success(record)
        }

    override suspend fun editExpense(
        id: UUID,
        expectedVersion: Long,
        expense: NewExpense,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:edit", RecordSnapshot::decodeExpense, RecordSnapshot::encodeExpense) {
            validateExpense(expense)?.let { return@mutate it }
            val old = database.expenses().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (old.deletedAtUtc != null) return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val updated =
                old.toModel().copy(
                    amount = expense.amount,
                    occurredAt = expense.occurredAt,
                    categoryId = expense.categoryId,
                    paymentMethodId = expense.paymentMethodId,
                    merchant = expense.merchant.trim(),
                    note = expense.note.trim(),
                    updatedAt = clock.instant(),
                    version = old.version + 1,
                )
            database.expenses().update(updated.toEntity())
            StoreResult.Success(updated)
        }

    override suspend fun deleteExpense(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:delete", RecordSnapshot::decodeExpense, RecordSnapshot::encodeExpense) {
            val old = database.expenses().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (old.deletedAtUtc != null) return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val now = clock.instant()
            val updated = old.toModel().copy(deletedAt = now, updatedAt = now, version = old.version + 1)
            database.expenses().update(updated.toEntity())
            StoreResult.Success(updated)
        }

    override suspend fun undoDelete(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Expense> =
        mutate(token, "expense:undo", RecordSnapshot::decodeExpense, RecordSnapshot::encodeExpense) {
            val old = database.expenses().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (old.deletedAtUtc == null || old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val updated = old.toModel().copy(deletedAt = null, updatedAt = clock.instant(), version = old.version + 1)
            database.expenses().update(updated.toEntity())
            StoreResult.Success(updated)
        }

    override suspend fun getExpense(id: UUID): Expense? = database.expenses().get(id.toString())?.toModel()

    override fun observeAllActiveExpenses(): Flow<List<Expense>> =
        database.expenses().observeAllActive().map { rows -> rows.map(ExpenseEntity::toModel) }

    override fun observeExpenses(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Expense>> =
        if (startInclusive >= endExclusive) {
            flowOf(emptyList<Expense>())
        } else {
            database.expenses().observeActive(startInclusive.toStorageInstant(), endExclusive.toStorageInstant()).map { rows ->
                rows.map(ExpenseEntity::toModel)
            }
        }

    override suspend fun createCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        token: UUID,
    ): StoreResult<Category> =
        mutate(token, "category:create", RecordSnapshot::decodeCategory, RecordSnapshot::encodeCategory) {
            val normalized = validateName(name) ?: return@mutate StoreResult.ValidationFailure("Invalid category name")
            try {
                CategoryVisualValidation.validate(iconKey, colorKey)
            } catch (e: IllegalArgumentException) {
                return@mutate StoreResult.ValidationFailure(e.message.orEmpty())
            }
            val now = clock.instant()
            val record = Category(uuidGenerator.next(), normalized.displayName, iconKey, colorKey, now, now)
            database.categories().insert(record.toEntity(normalized.uniquenessKey))
            StoreResult.Success(record)
        }

    override suspend fun renameCategory(
        id: UUID,
        expectedVersion: Long,
        name: String,
        token: UUID,
    ): StoreResult<Category> =
        mutate(token, "category:rename", RecordSnapshot::decodeCategory, RecordSnapshot::encodeCategory) {
            val old = database.categories().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val normalized = validateName(name) ?: return@mutate StoreResult.ValidationFailure("Invalid category name")
            val updated = old.toModel().copy(name = normalized.displayName, updatedAt = clock.instant(), version = old.version + 1)
            database.categories().update(updated.toEntity(normalized.uniquenessKey.takeIf { old.archivedAtUtc == null }))
            StoreResult.Success(updated)
        }

    override suspend fun archiveCategory(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<Category> =
        mutate(token, "category:archive", RecordSnapshot::decodeCategory, RecordSnapshot::encodeCategory) {
            val old = database.categories().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            if (old.archivedAtUtc != null) return@mutate StoreResult.Success(old.toModel())
            val now = clock.instant()
            val updated = old.toModel().copy(archivedAt = now, updatedAt = now, version = old.version + 1)
            database.categories().update(updated.toEntity(activeNameKey = null))
            StoreResult.Success(updated)
        }

    override suspend fun getCategory(id: UUID): Category? = database.categories().get(id.toString())?.toModel()

    override fun observeCategories(): Flow<List<Category>> =
        database.categories().observeActive().map { rows -> rows.map(CategoryEntity::toModel) }

    override suspend fun createPaymentMethod(
        name: String,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:create", RecordSnapshot::decodePaymentMethod, RecordSnapshot::encodePaymentMethod) {
            val normalized = validateName(name) ?: return@mutate StoreResult.ValidationFailure("Invalid payment method name")
            val now = clock.instant()
            val record = PaymentMethod(uuidGenerator.next(), normalized.displayName, false, now, now)
            database.paymentMethods().insert(record.toEntity(normalized.uniquenessKey))
            StoreResult.Success(record)
        }

    override suspend fun renamePaymentMethod(
        id: UUID,
        expectedVersion: Long,
        name: String,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:rename", RecordSnapshot::decodePaymentMethod, RecordSnapshot::encodePaymentMethod) {
            val old = database.paymentMethods().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            val normalized = validateName(name) ?: return@mutate StoreResult.ValidationFailure("Invalid payment method name")
            val updated = old.toModel().copy(name = normalized.displayName, updatedAt = clock.instant(), version = old.version + 1)
            database.paymentMethods().update(updated.toEntity(normalized.uniquenessKey.takeIf { old.archivedAtUtc == null }))
            StoreResult.Success(updated)
        }

    override suspend fun archivePaymentMethod(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:archive", RecordSnapshot::decodePaymentMethod, RecordSnapshot::encodePaymentMethod) {
            val old = database.paymentMethods().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (old.version != expectedVersion) return@mutate StoreResult.Conflict(old.version)
            if (old.archivedAtUtc != null) return@mutate StoreResult.Success(old.toModel())
            val now = clock.instant()
            val updated = old.toModel().copy(isDefault = false, archivedAt = now, updatedAt = now, version = old.version + 1)
            database.paymentMethods().update(updated.toEntity(activeNameKey = null))
            StoreResult.Success(updated)
        }

    override suspend fun setDefaultPaymentMethod(
        id: UUID,
        expectedVersion: Long,
        token: UUID,
    ): StoreResult<PaymentMethod> =
        mutate(token, "payment_method:set_default", RecordSnapshot::decodePaymentMethod, RecordSnapshot::encodePaymentMethod) {
            val selected = database.paymentMethods().get(id.toString()) ?: return@mutate StoreResult.NotFound
            if (selected.archivedAtUtc != null) return@mutate StoreResult.InactiveReference
            if (selected.version != expectedVersion) return@mutate StoreResult.Conflict(selected.version)
            val now = clock.instant()
            database.paymentMethods().active().filter { it.isDefault && it.id != id.toString() }.forEach { old ->
                database.paymentMethods().update(
                    old.copy(isDefault = false, updatedAtUtc = now.toStorageInstant(), version = old.version + 1),
                )
            }
            if (!selected.isDefault) {
                database.paymentMethods().update(
                    selected.copy(isDefault = true, updatedAtUtc = now.toStorageInstant(), version = selected.version + 1),
                )
            }
            StoreResult.Success(database.paymentMethods().get(id.toString())!!.toModel())
        }

    override suspend fun getPaymentMethod(id: UUID): PaymentMethod? = database.paymentMethods().get(id.toString())?.toModel()

    override fun observePaymentMethods(): Flow<List<PaymentMethod>> =
        database.paymentMethods().observeActive().map { rows -> rows.map(PaymentMethodEntity::toModel) }

    suspend fun seedDefaults(): StoreResult<Unit> =
        database.safeTransaction {
            try {
                database.withTransaction {
                    if (database.metadata().get()?.seededDefaults == true) return@withTransaction StoreResult.Success(Unit)
                    val categories =
                        listOf(
                            Triple("Food", "food", "red"),
                            Triple("Transport", "transport", "orange"),
                            Triple("Housing", "housing", "yellow"),
                            Triple("Utilities", "utilities", "green"),
                            Triple("Health", "health", "blue"),
                            Triple("Other", "other", "purple"),
                        )
                    categories.forEach { (name, icon, color) ->
                        val id = stableId("category:$name")
                        if (database.categories().get(id.toString()) == null) {
                            val normalized = NameValidation.validate(name)
                            val now = clock.instant()
                            database.categories().insert(
                                Category(id, name, icon, color, now, now).toEntity(normalized.uniquenessKey),
                            )
                        }
                    }
                    listOf("Cash" to true, "Card" to false).forEach { (name, shouldDefault) ->
                        val id = stableId("payment:$name")
                        if (database.paymentMethods().get(id.toString()) == null) {
                            val normalized = NameValidation.validate(name)
                            val now = clock.instant()
                            val isDefault = shouldDefault && database.paymentMethods().active().none { it.isDefault }
                            database.paymentMethods().insert(
                                PaymentMethod(id, name, isDefault, now, now).toEntity(normalized.uniquenessKey),
                            )
                        }
                    }
                    database.metadata().insert(AppMetadataEntity(id = 1, seededDefaults = true))
                    StoreResult.Success(Unit)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SQLiteConstraintException) {
                StoreResult.ValidationFailure("Default records conflict with existing active names")
            } catch (failure: Exception) {
                StoreResult.StorageFailure(failure)
            }
        }

    override fun close() = database.close()

    private suspend fun validateExpense(expense: NewExpense): StoreResult<Nothing>? {
        if (expense.amount.minorUnits <= 0) return StoreResult.ValidationFailure("Amount must be positive")
        if (expense.occurredAt.isAfter(clock.instant())) return StoreResult.ValidationFailure("Occurred time cannot be in the future")
        if (expense.merchant.trim().length > 100 || expense.note.trim().length > 500) {
            return StoreResult.ValidationFailure("Text exceeds the approved limit")
        }
        val category = database.categories().get(expense.categoryId.toString())
        val method = database.paymentMethods().get(expense.paymentMethodId.toString())
        if (category?.archivedAtUtc != null || method?.archivedAtUtc != null || category == null || method == null) {
            return StoreResult.InactiveReference
        }
        return null
    }

    private suspend fun <T : Any> mutate(
        token: UUID,
        operationKey: String,
        decode: (String) -> T,
        encode: (T) -> String,
        operation: suspend () -> StoreResult<T>,
    ): StoreResult<T> =
        try {
            database.withTransaction {
                val existing = database.mutationReceipts().get(token.toString())
                if (existing != null) {
                    if (existing.payloadType != operationKey) {
                        return@withTransaction StoreResult.Conflict()
                    }
                    return@withTransaction StoreResult.Success(decode(existing.payload))
                }
                val result = operation()
                if (result is StoreResult.Success) {
                    database.mutationReceipts().insert(
                        MutationReceiptEntity(
                            token.toString(),
                            operationKey,
                            encode(result.value),
                            clock.instant().toStorageInstant(),
                        ),
                    )
                }
                result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SQLiteConstraintException) {
            StoreResult.ValidationFailure("A conflicting record already exists")
        } catch (failure: Exception) {
            StoreResult.StorageFailure(failure)
        }

    private fun validateName(name: String) =
        try {
            NameValidation.validate(name)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun stableId(key: String): UUID = UUID.nameUUIDFromBytes("BubbleSpend:$key".toByteArray(Charsets.UTF_8))

    companion object {
        fun open(
            context: Context,
            name: String,
            clock: Clock,
            uuidGenerator: UuidGenerator,
        ): StoreResult<RoomExpenseStore> {
            val file = context.getDatabasePath(name)
            if (file.isFile && file.length() > 0L) {
                val header = ByteArray(SQLITE_HEADER.size)
                val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
                if (read != SQLITE_HEADER.size || !header.contentEquals(SQLITE_HEADER)) {
                    return StoreResult.StorageFailure(SQLiteException("Database file header is invalid"))
                }
            }
            return try {
                val database = Room.databaseBuilder(context, ExpenseDatabase::class.java, name).build()
                database.openHelper.writableDatabase
                StoreResult.Success(RoomExpenseStore(database, clock, uuidGenerator))
            } catch (failure: Exception) {
                StoreResult.StorageFailure(failure)
            }
        }

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}

private suspend fun ExpenseDatabase.safeTransaction(block: suspend () -> StoreResult<Unit>): StoreResult<Unit> =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        StoreResult.StorageFailure(failure)
    }

private fun Expense.toEntity() =
    ExpenseEntity(
        id.toString(),
        amount.minorUnits,
        amount.currency.value,
        occurredAt.toStorageInstant(),
        categoryId.toString(),
        paymentMethodId.toString(),
        merchant,
        note,
        createdAt.toStorageInstant(),
        updatedAt.toStorageInstant(),
        deletedAt?.toStorageInstant(),
        version,
        syncStatus.name,
    )

private fun ExpenseEntity.toModel() =
    Expense(
        UUID.fromString(id),
        Money(amountMinorUnits, CurrencyCode.of(currencyCode)),
        Instant.parse(occurredAtUtc),
        UUID.fromString(categoryId),
        UUID.fromString(paymentMethodId),
        merchant,
        note,
        Instant.parse(createdAtUtc),
        Instant.parse(updatedAtUtc),
        deletedAtUtc?.let(Instant::parse),
        version,
        LocalSyncStatus.valueOf(syncStatus),
    )

private fun Category.toEntity(activeNameKey: String? = null) =
    CategoryEntity(
        id.toString(),
        name,
        activeNameKey,
        iconKey,
        colorKey,
        createdAt.toStorageInstant(),
        updatedAt.toStorageInstant(),
        archivedAt?.toStorageInstant(),
        version,
        syncStatus.name,
    )

private fun CategoryEntity.toModel() =
    Category(
        UUID.fromString(id),
        name,
        iconKey,
        colorKey,
        Instant.parse(createdAtUtc),
        Instant.parse(updatedAtUtc),
        archivedAtUtc?.let(Instant::parse),
        version,
        LocalSyncStatus.valueOf(syncStatus),
    )

private fun PaymentMethod.toEntity(activeNameKey: String? = null) =
    PaymentMethodEntity(
        id.toString(),
        name,
        activeNameKey,
        isDefault,
        createdAt.toStorageInstant(),
        updatedAt.toStorageInstant(),
        archivedAt?.toStorageInstant(),
        version,
        syncStatus.name,
    )

private fun PaymentMethodEntity.toModel() =
    PaymentMethod(
        UUID.fromString(id),
        name,
        isDefault,
        Instant.parse(createdAtUtc),
        Instant.parse(updatedAtUtc),
        archivedAtUtc?.let(Instant::parse),
        version,
        LocalSyncStatus.valueOf(syncStatus),
    )

private object RecordSnapshot {
    fun encodeExpense(record: Expense) =
        encode { output ->
            output.writeUTF(record.id.toString())
            output.writeLong(record.amount.minorUnits)
            output.writeUTF(record.amount.currency.value)
            output.writeInstant(record.occurredAt)
            output.writeUTF(record.categoryId.toString())
            output.writeUTF(record.paymentMethodId.toString())
            output.writeUTF(record.merchant)
            output.writeUTF(record.note)
            output.writeInstant(record.createdAt)
            output.writeInstant(record.updatedAt)
            output.writeNullableInstant(record.deletedAt)
            output.writeLong(record.version)
            output.writeUTF(record.syncStatus.name)
        }

    fun decodeExpense(payload: String) =
        decode(payload) { input ->
            Expense(
                UUID.fromString(input.readUTF()),
                Money(input.readLong(), CurrencyCode.of(input.readUTF())),
                input.readInstant(),
                UUID.fromString(input.readUTF()),
                UUID.fromString(input.readUTF()),
                input.readUTF(),
                input.readUTF(),
                input.readInstant(),
                input.readInstant(),
                input.readNullableInstant(),
                input.readLong(),
                LocalSyncStatus.valueOf(input.readUTF()),
            )
        }

    fun encodeCategory(record: Category) =
        encode { output ->
            output.writeUTF(record.id.toString())
            output.writeUTF(record.name)
            output.writeUTF(record.iconKey)
            output.writeUTF(record.colorKey)
            output.writeInstant(record.createdAt)
            output.writeInstant(record.updatedAt)
            output.writeNullableInstant(record.archivedAt)
            output.writeLong(record.version)
            output.writeUTF(record.syncStatus.name)
        }

    fun decodeCategory(payload: String) =
        decode(payload) { input ->
            Category(
                UUID.fromString(input.readUTF()),
                input.readUTF(),
                input.readUTF(),
                input.readUTF(),
                input.readInstant(),
                input.readInstant(),
                input.readNullableInstant(),
                input.readLong(),
                LocalSyncStatus.valueOf(input.readUTF()),
            )
        }

    fun encodePaymentMethod(record: PaymentMethod) =
        encode { output ->
            output.writeUTF(record.id.toString())
            output.writeUTF(record.name)
            output.writeBoolean(record.isDefault)
            output.writeInstant(record.createdAt)
            output.writeInstant(record.updatedAt)
            output.writeNullableInstant(record.archivedAt)
            output.writeLong(record.version)
            output.writeUTF(record.syncStatus.name)
        }

    fun decodePaymentMethod(payload: String) =
        decode(payload) { input ->
            PaymentMethod(
                UUID.fromString(input.readUTF()),
                input.readUTF(),
                input.readBoolean(),
                input.readInstant(),
                input.readInstant(),
                input.readNullableInstant(),
                input.readLong(),
                LocalSyncStatus.valueOf(input.readUTF()),
            )
        }

    private fun encode(write: (DataOutputStream) -> Unit): String {
        val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use(write) }.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun <T> decode(
        payload: String,
        read: (DataInputStream) -> T,
    ): T = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(payload))).use(read)

    private fun DataOutputStream.writeInstant(value: Instant) {
        writeLong(value.epochSecond)
        writeInt(value.nano)
    }

    private fun DataInputStream.readInstant() = Instant.ofEpochSecond(readLong(), readInt().toLong())

    private fun DataOutputStream.writeNullableInstant(value: Instant?) {
        writeBoolean(value != null)
        if (value != null) writeInstant(value)
    }

    private fun DataInputStream.readNullableInstant(): Instant? = if (readBoolean()) readInstant() else null
}

private val utcInstantFormatter = DateTimeFormatterBuilder().appendInstant(9).toFormatter()

private fun Instant.toStorageInstant(): String = utcInstantFormatter.format(this)
