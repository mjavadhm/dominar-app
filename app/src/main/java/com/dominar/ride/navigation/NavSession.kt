package com.dominar.ride.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.maplibre.android.geometry.LatLng

/**
 * App-scoped navigation session. Owned by MainActivity (survives tab
 * switches) and passed into ActiveRideScreen, so leaving the Ride tab no
 * longer resets the destination, the fetched routes or live guidance.
 */
class NavSession {

    /** Last known GPS position of the rider. */
    var myLocation by mutableStateOf<LatLng?>(null)

    /** Custom starting point; null means "my location". */
    var origin by mutableStateOf<NavPlace?>(null)

    var destination by mutableStateOf<NavPlace?>(null)

    /** Route alternatives from Neshan; index 0 is the fastest. */
    var routes by mutableStateOf<List<NavRoute>>(emptyList())
    var selectedRouteIndex by mutableIntStateOf(0)

    /** True after the rider taps "Let's go". */
    var navigating by mutableStateOf(false)

    /** Live tracker for the selected route (survives tab switches). */
    var tracker: NavTracker? = null
    var progress by mutableStateOf<NavProgress?>(null)

    /** Step index for the manual step-by-step preview. */
    var previewStepIndex by mutableIntStateOf(0)

    /** Camera follows the rider while navigating until they pan the map. */
    var cameraLocked by mutableStateOf(true)

    /** Guards the one-shot "destination reached" cluster packet. */
    var sentArrived = false

    val selectedRoute: NavRoute?
        get() = routes.getOrNull(selectedRouteIndex)

    /** Drops routes + live guidance but keeps the endpoints. */
    fun clearRoutes() {
        routes = emptyList()
        selectedRouteIndex = 0
        navigating = false
        tracker = null
        progress = null
        previewStepIndex = 0
        sentArrived = false
        cameraLocked = true
    }

    /** Full reset back to the browse state. */
    fun reset() {
        clearRoutes()
        origin = null
        destination = null
    }
}
