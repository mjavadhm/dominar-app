package com.dominar.ride.navigation

import com.dominar.ride.protocol.DominarProtocol
import org.maplibre.android.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Live navigation progress for the HUD and the cluster. */
data class NavProgress(
    /** The upcoming maneuver step (null once past the arrive step). */
    val step: NavRouteStep?,
    val maneuver: DominarProtocol.Maneuver,
    val distanceToTurnMeters: Double,
    val distanceLeftMeters: Double,
    /** Rough remaining travel time, scaled from the route estimate. */
    val etaSeconds: Double,
    val arrived: Boolean,
)

/**
 * Tracks progress along a [NavRoute] from GPS fixes.
 *
 * Pure logic, no Android dependencies. Steps follow the OSRM convention:
 * step i's maneuver happens at its start_location, so steps[0] is "depart"
 * (already behind us) and the first upcoming maneuver is steps[1].
 */
class NavTracker(private val route: NavRoute) {

    private var nextIndex = if (route.steps.size > 1) 1 else 0
    private var arrived = false

    fun update(location: LatLng): NavProgress {
        // Advance past maneuvers we've reached.
        while (nextIndex < route.steps.size) {
            val point = route.steps[nextIndex].location ?: break
            if (haversineMeters(location, point) < ADVANCE_RADIUS_M) {
                if (nextIndex == route.steps.lastIndex) arrived = true
                nextIndex++
            } else {
                break
            }
        }

        val step = route.steps.getOrNull(nextIndex)
        val target = step?.location ?: route.points.lastOrNull()
        val distToTurn = target?.let { haversineMeters(location, it) } ?: 0.0
        if (step == null && distToTurn < ADVANCE_RADIUS_M) arrived = true

        var distLeft = distToTurn
        for (i in nextIndex until route.steps.size) {
            distLeft += route.steps[i].distanceMeters
        }

        val eta = if (route.distanceMeters > 1.0) {
            route.durationSeconds * (distLeft / route.distanceMeters)
        } else 0.0

        return NavProgress(
            step = step,
            maneuver = if (arrived) DominarProtocol.Maneuver.DESTINATION_REACHED
            else maneuverOf(step),
            distanceToTurnMeters = distToTurn,
            distanceLeftMeters = distLeft,
            etaSeconds = eta,
            arrived = arrived,
        )
    }

    companion object {
        private const val ADVANCE_RADIUS_M = 30.0
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Maps a Neshan/OSRM step to the cluster's maneuver code. */
        fun maneuverOf(step: NavRouteStep?): DominarProtocol.Maneuver {
            if (step == null) return DominarProtocol.Maneuver.DESTINATION_REACHED
            val type = step.type.lowercase()
            val modifier = step.modifier.lowercase()
            return when {
                type == "arrive" -> DominarProtocol.Maneuver.DESTINATION_REACHED
                type.contains("rotary") || type.contains("roundabout") ->
                    if (modifier.contains("left")) DominarProtocol.Maneuver.ROUNDABOUT_LEFT
                    else DominarProtocol.Maneuver.ROUNDABOUT_RIGHT
                modifier.contains("uturn") ->
                    if (modifier.contains("right")) DominarProtocol.Maneuver.U_TURN_RIGHT
                    else DominarProtocol.Maneuver.U_TURN_LEFT
                modifier == "sharp left" -> DominarProtocol.Maneuver.TURN_SHARP_LEFT
                modifier == "sharp right" -> DominarProtocol.Maneuver.TURN_SHARP_RIGHT
                modifier == "slight left" -> DominarProtocol.Maneuver.TURN_SLIGHT_LEFT
                modifier == "slight right" -> DominarProtocol.Maneuver.TURN_SLIGHT_RIGHT
                modifier == "left" -> DominarProtocol.Maneuver.TURN_LEFT
                modifier == "right" -> DominarProtocol.Maneuver.TURN_RIGHT
                else -> DominarProtocol.Maneuver.STRAIGHT
            }
        }

        fun haversineMeters(a: LatLng, b: LatLng): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLng = Math.toRadians(b.longitude - a.longitude)
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
            return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
        }
    }
}
