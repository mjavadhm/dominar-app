package com.dominar.ride.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for the Garage & Performance features.
 * Phase 0 ships the schema for the service log; fuel, documents and
 * performance records will be added in later phases with migrations.
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

@Dao
interface ServiceLogDao {
    @Insert
    suspend fun insert(entry: ServiceLogEntity): Long

    @Delete
    suspend fun delete(entry: ServiceLogEntity)

    @Query("SELECT * FROM service_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ServiceLogEntity>>

    @Query("SELECT * FROM service_log WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestOfType(type: String): ServiceLogEntity?
}

@Database(
    entities = [ServiceLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceLogDao(): ServiceLogDao
}
