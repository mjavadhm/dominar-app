package com.dominar.ride.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for the Garage & Performance features.
 */
@Entity(tableName = "service_log")
data class ServiceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** e.g. ENGINE_OIL, COOLANT, AIR_FILTER, OIL_FILTER, CHAIN, BRAKE_PADS */
    val type: String,
    val odometerKm: Int,
    /** Epoch millis of when the service was done. */
    val timestamp: Long,
    val note: String? = null
)

/** Per-type reminder interval override (falls back to defaults in ServiceTypes.kt). */
@Entity(tableName = "service_interval")
data class ServiceIntervalEntity(
    @PrimaryKey val type: String,
    val intervalKm: Int?,
    val intervalMonths: Int?
)

@Entity(tableName = "fuel_log")
data class FuelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val liters: Double,
    /** Total cost in Toman. */
    val totalCost: Long,
    val odometerKm: Int,
    val timestamp: Long,
    /** True when the tank was filled to the top (used for consumption math). */
    val fullTank: Boolean = true
)

/** Expiry dates for paperwork. type = INSURANCE | INSPECTION */
@Entity(tableName = "document")
data class DocumentEntity(
    @PrimaryKey val type: String,
    val expiryTimestamp: Long
)

/** Single-row table holding the last parked location. */
@Entity(tableName = "parking")
data class ParkingEntity(
    @PrimaryKey val id: Int = 0,
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    /** True when saved automatically on BLE disconnect. */
    val auto: Boolean
)

@Dao
interface ServiceLogDao {
    @Insert
    suspend fun insert(entry: ServiceLogEntity): Long

    @Delete
    suspend fun delete(entry: ServiceLogEntity)

    @Query("SELECT * FROM service_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ServiceLogEntity>>

    @Query("SELECT * FROM service_log ORDER BY timestamp DESC")
    suspend fun listAll(): List<ServiceLogEntity>

    @Query("SELECT * FROM service_log WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestOfType(type: String): ServiceLogEntity?
}

@Dao
interface ServiceIntervalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(interval: ServiceIntervalEntity)

    @Query("SELECT * FROM service_interval")
    fun observeAll(): Flow<List<ServiceIntervalEntity>>

    @Query("SELECT * FROM service_interval")
    suspend fun listAll(): List<ServiceIntervalEntity>
}

@Dao
interface FuelLogDao {
    @Insert
    suspend fun insert(entry: FuelLogEntity): Long

    @Delete
    suspend fun delete(entry: FuelLogEntity)

    @Query("SELECT * FROM fuel_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<FuelLogEntity>>

    @Query("SELECT * FROM fuel_log ORDER BY timestamp DESC")
    suspend fun listAll(): List<FuelLogEntity>
}

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("SELECT * FROM document")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document")
    suspend fun listAll(): List<DocumentEntity>
}

@Dao
interface ParkingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(parking: ParkingEntity)

    @Query("SELECT * FROM parking WHERE id = 0")
    fun observe(): Flow<ParkingEntity?>

    @Query("DELETE FROM parking")
    suspend fun clear()
}

@Database(
    entities = [
        ServiceLogEntity::class,
        ServiceIntervalEntity::class,
        FuelLogEntity::class,
        DocumentEntity::class,
        ParkingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceLogDao(): ServiceLogDao
    abstract fun serviceIntervalDao(): ServiceIntervalDao
    abstract fun fuelLogDao(): FuelLogDao
    abstract fun documentDao(): DocumentDao
    abstract fun parkingDao(): ParkingDao
}
