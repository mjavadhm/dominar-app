package com.dominar.ride.garage

import com.dominar.ride.data.db.ServiceIntervalEntity
import com.dominar.ride.data.db.ServiceLogEntity

/** A known maintenance item with optional default reminder intervals. */
data class ServiceType(
    val key: String,
    val label: String,
    val emoji: String,
    val defaultKm: Int?,
    val defaultMonths: Int?
)

val SERVICE_TYPES: List<ServiceType> = listOf(
    ServiceType("ENGINE_OIL", "Engine oil", "\uD83D\uDEE2\uFE0F", 3000, 6),
    ServiceType("OIL_FILTER", "Oil filter", "\uD83E\uDDF0", 6000, 12),
    ServiceType("AIR_FILTER", "Air filter", "\uD83C\uDF2C\uFE0F", 10000, 12),
    ServiceType("COOLANT", "Coolant", "\uD83E\uDDCA", 20000, 24),
    ServiceType("CHAIN", "Chain (lube/adjust)", "\u26D3\uFE0F", 5000, null),
    ServiceType("BRAKE_PADS", "Brake pads", "\uD83D\uDED1", 15000, null),
    ServiceType("SPARK_PLUG", "Spark plug", "\u26A1", 10000, null)
)

fun serviceTypeOf(key: String): ServiceType =
    SERVICE_TYPES.firstOrNull { it.key == key }
        ?: ServiceType(key, key, "\uD83D\uDD27", null, null)

/** Computed due-state of one service type. */
data class ServiceStatus(
    val type: ServiceType,
    val lastLog: ServiceLogEntity?,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    /** Km remaining until due (may be negative). Null when unknown. */
    val kmLeft: Int?,
    /** Days remaining until due (may be negative). Null when unknown. */
    val daysLeft: Int?,
    /** 0..1 fraction of the interval used up (max of km/time), null if never logged. */
    val progress: Float?
)

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val MONTH_MS = 30L * DAY_MS

fun computeServiceStatus(
    type: ServiceType,
    lastLog: ServiceLogEntity?,
    interval: ServiceIntervalEntity?,
    currentOdometerKm: Int,
    nowMillis: Long
): ServiceStatus {
    val intervalKm = interval?.intervalKm ?: type.defaultKm
    val intervalMonths = interval?.intervalMonths ?: type.defaultMonths
    if (lastLog == null) {
        return ServiceStatus(type, null, intervalKm, intervalMonths, null, null, null)
    }
    val kmSince = (currentOdometerKm - lastLog.odometerKm).coerceAtLeast(0)
    val kmLeft = intervalKm?.let { it - kmSince }
    val daysLeft = intervalMonths?.let {
        ((lastLog.timestamp + it * MONTH_MS - nowMillis) / DAY_MS).toInt()
    }
    val kmProgress = intervalKm?.takeIf { it > 0 }?.let { kmSince.toFloat() / it }
    val timeProgress = intervalMonths?.takeIf { it > 0 }?.let {
        (nowMillis - lastLog.timestamp).toFloat() / (it * MONTH_MS)
    }
    val progress = listOfNotNull(kmProgress, timeProgress).maxOrNull()?.coerceIn(0f, 1f)
    return ServiceStatus(type, lastLog, intervalKm, intervalMonths, kmLeft, daysLeft, progress)
}
