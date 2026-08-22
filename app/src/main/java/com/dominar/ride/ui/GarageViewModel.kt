package com.dominar.ride.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominar.ride.data.db.DocumentDao
import com.dominar.ride.data.db.DocumentEntity
import com.dominar.ride.data.db.FuelLogDao
import com.dominar.ride.data.db.FuelLogEntity
import com.dominar.ride.data.db.ParkingDao
import com.dominar.ride.data.db.ParkingEntity
import com.dominar.ride.data.db.ServiceIntervalDao
import com.dominar.ride.data.db.ServiceIntervalEntity
import com.dominar.ride.data.db.ServiceLogDao
import com.dominar.ride.data.db.ServiceLogEntity
import com.dominar.ride.garage.SERVICE_TYPES
import com.dominar.ride.garage.ServiceStatus
import com.dominar.ride.garage.computeServiceStatus
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FuelStats(
    val avgLitersPer100Km: Double?,
    val costLast30Days: Long,
    val lastOdometerKm: Int?
)

@HiltViewModel
class GarageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceLogDao: ServiceLogDao,
    private val serviceIntervalDao: ServiceIntervalDao,
    private val fuelLogDao: FuelLogDao,
    private val documentDao: DocumentDao,
    private val parkingDao: ParkingDao
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val serviceLogs: StateFlow<List<ServiceLogEntity>> =
        serviceLogDao.observeAll().state(emptyList())

    val fuelLogs: StateFlow<List<FuelLogEntity>> =
        fuelLogDao.observeAll().state(emptyList())

    val documents: StateFlow<Map<String, DocumentEntity>> =
        documentDao.observeAll().map { docs -> docs.associateBy { it.type } }.state(emptyMap())

    val parking: StateFlow<ParkingEntity?> = parkingDao.observe().state(null)

    val serviceStatuses: StateFlow<List<ServiceStatus>> = combine(
        serviceLogDao.observeAll(),
        fuelLogDao.observeAll(),
        serviceIntervalDao.observeAll()
    ) { logs, fuel, intervalList ->
        val intervals = intervalList.associateBy { it.type }
        val currentOdo = maxOf(
            logs.maxOfOrNull { it.odometerKm } ?: 0,
            fuel.maxOfOrNull { it.odometerKm } ?: 0
        )
        val now = System.currentTimeMillis()
        SERVICE_TYPES.map { type ->
            val last = logs.filter { it.type == type.key }.maxByOrNull { it.timestamp }
            computeServiceStatus(type, last, intervals[type.key], currentOdo, now)
        }
    }.state(emptyList())

    /** The most urgent logged service (highest fraction of its interval used up). */
    val nextService: StateFlow<ServiceStatus?> = serviceStatuses.map { list ->
        list.filter { it.lastLog != null && it.progress != null }
            .maxByOrNull { it.progress ?: 0f }
    }.state(null)

    val fuelStats: StateFlow<FuelStats> = fuelLogs.map { logs ->
        val fulls = logs.filter { it.fullTank }.sortedBy { it.odometerKm }
        val consumptions = mutableListOf<Double>()
        for (i in 1 until fulls.size) {
            val km = fulls[i].odometerKm - fulls[i - 1].odometerKm
            if (km in 1..2000) consumptions += fulls[i].liters * 100.0 / km
        }
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        FuelStats(
            avgLitersPer100Km = if (consumptions.isEmpty()) null else consumptions.average(),
            costLast30Days = logs.filter { it.timestamp >= monthAgo }.sumOf { it.totalCost },
            lastOdometerKm = logs.maxOfOrNull { it.odometerKm }
        )
    }.state(FuelStats(null, 0, null))

    // ---------- Mutations ----------

    fun addServiceLog(type: String, odometerKm: Int, timestamp: Long, note: String?) {
        viewModelScope.launch {
            serviceLogDao.insert(
                ServiceLogEntity(
                    type = type,
                    odometerKm = odometerKm,
                    timestamp = timestamp,
                    note = note?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    fun deleteServiceLog(entry: ServiceLogEntity) {
        viewModelScope.launch { serviceLogDao.delete(entry) }
    }

    fun setServiceInterval(type: String, intervalKm: Int?, intervalMonths: Int?) {
        viewModelScope.launch {
            serviceIntervalDao.upsert(ServiceIntervalEntity(type, intervalKm, intervalMonths))
        }
    }

    fun addFuelLog(liters: Double, totalCost: Long, odometerKm: Int, fullTank: Boolean) {
        viewModelScope.launch {
            fuelLogDao.insert(
                FuelLogEntity(
                    liters = liters,
                    totalCost = totalCost,
                    odometerKm = odometerKm,
                    timestamp = System.currentTimeMillis(),
                    fullTank = fullTank
                )
            )
        }
    }

    fun deleteFuelLog(entry: FuelLogEntity) {
        viewModelScope.launch { fuelLogDao.delete(entry) }
    }

    fun setDocumentExpiry(type: String, expiryTimestamp: Long) {
        viewModelScope.launch { documentDao.upsert(DocumentEntity(type, expiryTimestamp)) }
    }

    fun clearParking() {
        viewModelScope.launch { parkingDao.clear() }
    }

    @SuppressLint("MissingPermission")
    fun saveParkingHere(onResult: (Boolean) -> Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            onResult(false)
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    persistParking(location.latitude, location.longitude)
                    onResult(true)
                } else {
                    client.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) {
                                persistParking(last.latitude, last.longitude)
                                onResult(true)
                            } else {
                                onResult(false)
                            }
                        }
                        .addOnFailureListener { onResult(false) }
                }
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun persistParking(lat: Double, lng: Double) {
        viewModelScope.launch {
            parkingDao.upsert(
                ParkingEntity(0, lat, lng, System.currentTimeMillis(), auto = false)
            )
        }
    }
}
