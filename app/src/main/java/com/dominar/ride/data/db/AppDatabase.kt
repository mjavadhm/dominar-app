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
    /** True when the t