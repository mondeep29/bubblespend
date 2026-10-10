package com.bubblespend.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "categories",
    indices = [Index(value = ["activeNameKey"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeNameKey: String?,
    val iconKey: String,
    val colorKey: String,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val archivedAtUtc: String?,
    val version: Long,
    val syncStatus: String,
)

@Entity(
    tableName = "payment_methods",
    indices = [Index(value = ["activeNameKey"], unique = true)],
)
data class PaymentMethodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeNameKey: String?,
    val isDefault: Boolean,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val archivedAtUtc: String?,
    val version: Long,
    val syncStatus: String,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["occurredAtUtc"]),
        Index(value = ["categoryId", "occurredAtUtc"]),
        Index(value = ["paymentMethodId", "occurredAtUtc"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val occurredAtUtc: String,
    val categoryId: String,
    val paymentMethodId: String,
    val merchant: String,
    val note: String,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val deletedAtUtc: String?,
    val version: Long,
    val syncStatus: String,
)

@Entity(tableName = "mutation_receipts")
data class MutationReceiptEntity(
    @PrimaryKey val token: String,
    val payloadType: String,
    val payload: String,
    val recordedAtUtc: String,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val id: Int,
    val seededDefaults: Boolean,
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE deletedAtUtc IS NULL ORDER BY occurredAtUtc DESC, id")
    fun observeAllActive(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExpenseEntity)

    @Update
    suspend fun update(entity: ExpenseEntity): Int

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Query(
        "SELECT * FROM expenses WHERE deletedAtUtc IS NULL " +
            "AND occurredAtUtc >= :startInclusive AND occurredAtUtc < :endExclusive " +
            "ORDER BY occurredAtUtc, id",
    )
    fun observeActive(
        startInclusive: String,
        endExclusive: String,
    ): Flow<List<ExpenseEntity>>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity)

    @Update
    suspend fun update(entity: CategoryEntity): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE archivedAtUtc IS NULL ORDER BY id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE archivedAtUtc IS NULL ORDER BY id")
    suspend fun active(): List<CategoryEntity>
}

@Dao
interface PaymentMethodDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PaymentMethodEntity)

    @Update
    suspend fun update(entity: PaymentMethodEntity): Int

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun get(id: String): PaymentMethodEntity?

    @Query("SELECT * FROM payment_methods WHERE archivedAtUtc IS NULL ORDER BY id")
    fun observeActive(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE archivedAtUtc IS NULL ORDER BY id")
    suspend fun active(): List<PaymentMethodEntity>
}

@Dao
interface MutationReceiptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MutationReceiptEntity)

    @Query("SELECT * FROM mutation_receipts WHERE token = :token")
    suspend fun get(token: String): MutationReceiptEntity?
}

@Dao
interface AppMetadataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AppMetadataEntity)

    @Query("SELECT * FROM app_metadata WHERE id = 1")
    suspend fun get(): AppMetadataEntity?
}

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        MutationReceiptEntity::class,
        AppMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao

    abstract fun categories(): CategoryDao

    abstract fun paymentMethods(): PaymentMethodDao

    abstract fun mutationReceipts(): MutationReceiptDao

    abstract fun metadata(): AppMetadataDao
}
