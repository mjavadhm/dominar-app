package com.dominar.ride.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dominar.ride.BuildConfig
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.navigation.NavPlace
import com.dominar.ride.navigation.NavProgress
import com.dominar.ride.navigation.NavRoute
import com.dominar.ride.navigation.NavTracker
import com.dominar.ride.navigation.NeshanApi
import com.dominar.ride.protocol.DominarProtocol
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.PerformanceViewModel
import com.dominar.ride.ui.theme.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class EndpointField { ORIGIN, DESTINATION }

private const val NESHAN_STYLE_URI =
    "https://static.neshan.org/sdk/maplibre/styles/light.json"

/**
 * Google-Maps-style navigation screen backed by the new MapLibre-based Neshan
 * SDK and Neshan web services (search / reverse geocode / motorcycle routing).
 *
 * - Starts centered on the rider's current GPS location.
 * - "Where to?" search bar picks a destination; origin defaults to your location.
 * - Both endpoints can be changed via search or a long-press on the map.
 * - While a route is active: live HUD overlays (speed / lean / next turn) and
 *   turn-by-turn forwarding to the cluster over BLE.
 */
@SuppressLint("MissingPermission")
@Composable
fun ActiveRideScreen(app: AppState, onStopRide: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val connState by app.connectionState.collectAsState()

    // --- Lean angle (shared engine with the Performance tab) ---
    val perf: PerformanceViewModel = hiltViewModel()
    val leanDeg by perf.leanDeg.collectAsState()

    // --- Map ---
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    // --- Navigation state ---
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var origin by remember { mutableStateOf<NavPlace?>(null) } // null == my location
    var destination by remember { mutableStateOf<NavPlace?>(null) }
    var route by remember { mutableStateOf<NavRoute?>(null) }
    var routing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // --- Live guidance state ---
    var navTracker by remember { mutableStateOf<NavTracker?>(null) }
    var navProgress by remember { mutableStateOf<NavProgress?>(null) }
    var speedKmh by remember { mutableStateOf(0f) }
    var sentArrived by remember { mutableStateOf(false) }

    // --- Search state ---
    var editingField by remember { mutableStateOf<EndpointField?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NavPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // --- Map overlays ---
    var myMarker by remember { mutableStateOf<Marker?>(null) }
    var originMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }
    var routeLine by remember { mutableStateOf<Polyline?>(null) }

    fun clearRouteOverlay() {
        if (route != null) {
            app.sendNavPacket(DominarProtocol.buildNavStopPacket())
        }
        val m = map
        routeLine?.let { m?.removePolyline(it) }
        routeLine = null
        route = null
        navTracker = null
        navProgress = null
        sentArrived = false
    }

    fun fetchRoute() {
        val m = map ?: return
        val dest = destination ?: return
        val start = origin?.location ?: myLocation
        if (start == null) {
            error = "Waiting for GPS location — tap the location button and try again"
            return
        }
        routing = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { NeshanApi.direction(start, dest.location) }
            }
            routing = false
            result.onSuccess { r ->
                clearRouteOverlay()
                route = r
                navTracker = NavTracker(r)
                navProgress = null
                sentArrived = false
                app.sendNavPacket(DominarProtocol.buildNavStartPacket())
                routeLine = m.addPolyline(
                    PolylineOptions()
                        .addAll(r.points)
                        .color(android.graphics.Color.rgb(30, 118, 255))
                        .width(6f)
                )
                val bounds = LatLngBounds.Builder()
                    .include(start)
                    .include(dest.location)
                    .build()
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
            }.onFailure { e ->
                error = e.message ?: "Routing failed"
            }
        }
    }

    fun setDestination(place: NavPlace?) {
        val m = map ?: return
        destination = place
        destMarker?.let { m.removeMarker(it) }
        destMarker = null
        clearRouteOverlay()
        if (place != null) {
            destMarker = m.addMarker(
                markerOptions(context, place.location, pinBitmap(0xFFE53935.toInt()))
            )
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(place.location, 14.5))
            fetchRoute()
        }
    }

    fun setOrigin(place: NavPlace?) {
        val m = map ?: return
        origin = place
        originMarker?.let { m.removeMarker(it) }
        originMarker = null
        clearRouteOverlay()
        if (place != null) {
            originMarker = m.addMarker(
                markerOptions(context, place.location, pinBitmap(0xFF2E7D32.toInt()))
            )
        }
        if (destination != null) fetchRoute()
    }

    fun swapEndpoints() {
        val m = map ?: return
        val dest = destination ?: return
        val newDestination = origin
            ?: myLocation?.let { NavPlace("Your location", null, it) }
            ?: return
        origin = dest
        destination = newDestination
        originMarker?.let { m.removeMarker(it) }
        destMarker?.let { m.removeMarker(it) }
        originMarker = m.addMarker(
            markerOptions(context, dest.location, pinBitmap(0xFF2E7D32.toInt()))
        )
        destMarker = m.addMarker(
            markerOptions(context, newDestination.location, pinBitmap(0xFFE53935.toInt()))
        )
        clearRouteOverlay()
        fetchRoute()
    }

    fun locateMe(recenter: Boolean) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        fused.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { loc ->
            if (loc == null) {
                if (recenter) error = "Couldn't get a GPS fix — is location turned on?"
                return@addOnSuccessListener
            }
            val ll = LatLng(loc.latitude, loc.longitude)
            myLocation = ll
            val m = map ?: return@addOnSuccessListener
            myMarker?.let { m.removeMarker(it) }
            myMarker = m.addMarker(
                markerOptions(context, ll, dotBitmap(0xFF1E76FF.toInt(), 0xFFFFFFFF.toInt()))
            )
            if (recenter) {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 15.5))
            }
        }.addOnFailureListener {
            if (recenter) error = "Location unavailable — check the location permission"
        }
    }

    // Forward the activity lifecycle to the MapView (required by MapLibre).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Tell the cluster navigation ended when the rider leaves this screen.
    DisposableEffect(Unit) {
        onDispose { app.sendNavPacket(DominarProtocol.buildNavStopPacket()) }
    }

    // Lean sensor runs while this screen is visible and the HUD toggle is on.
    DisposableEffect(app.hudShowLeanAngle) {
        if (app.hudShowLeanAngle) perf.startLeanSensor()
        onDispose { perf.stopLeanSensor() }
    }

    // Continuous location updates while navigating: HUD + cluster forwarding.
    DisposableEffect(route) {
        if (route == null) {
            speedKmh = 0f
            return@DisposableEffect onDispose {}
        }
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val ll = LatLng(loc.latitude, loc.longitude)
                myLocation = ll
                speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
                navProgress = navTracker?.update(ll)
                map?.let { m ->
                    myMarker?.let { m.removeMarker(it) }
                    myMarker = m.addMarker(
                        markerOptions(
                            context, ll,
                            dotBitmap(0xFF1E76FF.toInt(), 0xFFFFFFFF.toInt())
                        )
                    )
                }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { fused.removeLocationUpdates(callback) }
    }

    // Forward each progress update to the cluster (~1/s, mirrors Bajaj Ride).
    LaunchedEffect(navProgress) {
        val p = navProgress ?: return@LaunchedEffect
        val eta = Calendar.getInstance().apply { add(Calendar.SECOND, p.etaSeconds.toInt()) }
        val useMeters = p.distanceToTurnMeters < 950
        app.sendNavPacket(
            DominarProtocol.buildNavigationPacket(
                isPm = eta.get(Calendar.AM_PM) == Calendar.PM,
                distanceUnitMeters = useMeters,
                maneuver = p.maneuver,
                distanceToTurn = if (useMeters) p.distanceToTurnMeters
                else p.distanceToTurnMeters / 1000.0,
                etaHour = eta.get(Calendar.HOUR),
                etaMinute = eta.get(Calendar.MINUTE),
                distanceLeft = p.distanceLeftMeters / 1000.0,
                roundaboutExit = p.step?.exit ?: 0,
                instructionText = p.step?.name ?: ""
            )
        )
        if (p.arrived && !sentArrived) {
            sentArrived = true
            app.sendNavPacket(DominarProtocol.buildDestinationReachedPacket())
        }
    }

    // Initialize the map: Neshan style, initial camera, long-press pin drop.
    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.setStyle(Style.Builder().fromUri(NESHAN_STYLE_URI)) {
                m.cameraPosition = CameraPosition.Builder()
                    .target(myLocation ?: LatLng(35.6892, 51.3890)) // Tehran fallback
                    .zoom(11.0)
                    .build()
                map = m
            }
            m.addOnMapLongClickListener { latLng ->
                val target = editingField
                editingField = null
                query = ""
                val place = NavPlace("Dropped pin", null, latLng)
                if (target == EndpointField.ORIGIN) setOrigin(place) else setDestination(place)
                true
            }
        }
    }

    // Center on the rider as soon as the map is ready (like Google Maps).
    LaunchedEffect(map) {
        if (map != null) locateMe(recenter = true)
    }

    // Debounced search-as-you-type.
    LaunchedEffect(query, editingField) {
        results = emptyList()
        if (editingField == null || query.trim().length < 2) return@LaunchedEffect
        searching = true
        delay(350)
        val center = myLocation ?: LatLng(35.6997, 51.3380)
        val found = withContext(Dispatchers.IO) {
            runCatching { NeshanApi.search(query.trim(), center) }
        }
        searching = false
        found.onSuccess { results = it }.onFailure { error = it.message }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })

        // ---------- Top: search / directions ----------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp)
        ) {
            when {
                editingField != null -> {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = if (editingField == EndpointField.ORIGIN)
                            "Search starting point" else "Search destination",
                        onBack = { editingField = null; query = "" },
                        onClear = { query = "" }
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            if (editingField == EndpointField.ORIGIN) {
                                item {
                                    ResultRow(
                                        icon = "\uD83D\uDCCD",
                                        title = "Your location",
                                        subtitle = "Use current GPS position"
                                    ) {
                                        editingField = null
                                        query = ""
                                        setOrigin(null)
                                    }
                                    HorizontalDivider(color = BorderDark)
                                }
                            }
                            if (searching) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                            items(results) { place ->
                                ResultRow(
                                    icon = "\uD83D\uDCCC",
                                    title = place.title,
                                    subtitle = place.address
                                ) {
                                    val target = editingField
                                    editingField = null
                                    query = ""
                                    if (target == EndpointField.ORIGIN) setOrigin(place)
                                    else setDestination(place)
                                }
                            }
                            if (!searching && results.isEmpty() && query.trim().length >= 2) {
                                item {
                                    Text(
                                        text = "No results",
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSubtleDark
                                    )
                                }
                            }
                            item {
                                Text(
                                    text = "Tip: long-press on the map to drop a pin",
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp, vertical = 10.dp
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSubtleDark
                                )
                            }
                        }
                    }
                }

                destination == null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoundIconButton(Icons.Default.ArrowBack, "Back") { onStopRide() }
                    FakeSearchBar(
                        text = "Where to?",
                        modifier = Modifier.weight(1f),
                        onClick = { editingField = EndpointField.DESTINATION }
                    )
                }

                else -> DirectionsCard(
                    originTitle = origin?.title ?: "Your location",
                    destinationTitle = destination?.title ?: "",
                    onEditOrigin = { editingField = EndpointField.ORIGIN; query = "" },
                    onEditDestination = {
                        editingField = EndpointField.DESTINATION; query = ""
                    },
                    onSwap = { swapEndpoints() },
                    onClose = { setDestination(null); setOrigin(null) }
                )
            }

            // ---------- Next-turn HUD banner ----------
            if (route != null && app.hudShowNextTurn && editingField == null) {
                navProgress?.let { p ->
                    Spacer(Modifier.height(8.dp))
                    NextTurnBanner(p)
                }
            }

            Spacer(Modifier.height(8.dp))
            ConnectionPill(
                state = connState,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (BuildConfig.NESHAN_SDK_KEY.isBlank() || BuildConfig.NESHAN_API_KEY.isBlank()) {
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    "Neshan keys missing: add NESHAN_SDK_KEY (map) and NESHAN_API_KEY " +
                        "(search & routing) to local.properties — see README."
                )
            }
            error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(msg) { error = null }
            }
        }

        // ---------- Speed / lean HUD ----------
        if (route != null && (app.hudShowSpeed || app.hudShowLeanAngle)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 210.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (app.hudShowSpeed) SpeedHud(speedKmh)
                if (app.hudShowLeanAngle) LeanHud(leanDeg)
            }
        }

        // ---------- My-location button ----------
        FloatingActionButton(
            onClick = { locateMe(recenter = true) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 16.dp,
                    bottom = if (route != null || routing) 200.dp else 96.dp
                ),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "My location")
        }

        // ---------- Bottom: route summary / end ride ----------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            when {
                routing -> Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Finding the best route…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                route != null -> RouteSummaryCard(
                    route = route!!,
                    onClear = { setDestination(null) },
                    onEndRide = onStopRide
                )

                else -> OutlinedButton(
                    onClick = onStopRide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("End Ride", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// HUD pieces
// ---------------------------------------------------------------------------

private val HudBackground = Color(0xE6101418)
private val HudSubtle = Color(0xFF9AA4B2)

private fun maneuverSymbol(m: DominarProtocol.Maneuver): String = when (m) {
    DominarProtocol.Maneuver.TURN_LEFT -> "\u2B05"
    DominarProtocol.Maneuver.TURN_RIGHT -> "\u27A1"
    DominarProtocol.Maneuver.TURN_SLIGHT_LEFT -> "\u2196"
    DominarProtocol.Maneuver.TURN_SLIGHT_RIGHT -> "\u2197"
    DominarProtocol.Maneuver.TURN_SHARP_LEFT -> "\u2199"
    DominarProtocol.Maneuver.TURN_SHARP_RIGHT -> "\u2198"
    DominarProtocol.Maneuver.STRAIGHT -> "\u2B06"
    DominarProtocol.Maneuver.ROUNDABOUT_LEFT -> "\u27F2"
    DominarProtocol.Maneuver.ROUNDABOUT_RIGHT -> "\u27F3"
    DominarProtocol.Maneuver.U_TURN_LEFT -> "\u21B6"
    DominarProtocol.Maneuver.U_TURN_RIGHT -> "\u21B7"
    DominarProtocol.Maneuver.DESTINATION_REACHED -> "\uD83C\uDFC1"
}

private fun hudDistanceText(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m"
    else String.format(Locale.US, "%.1f km", meters / 1000.0)

@Composable
private fun NextTurnBanner(progress: NavProgress) {
    Surface(shape = RoundedCornerShape(16.dp), color = HudBackground, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(maneuverSymbol(progress.maneuver), fontSize = 26.sp, color = Color.White)
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (progress.arrived) "You have arrived"
                    else hudDistanceText(progress.distanceToTurnMeters),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                val subtitle = progress.step?.instruction?.takeIf { it.isNotBlank() }
                    ?: progress.step?.name.orEmpty()
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = HudSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedHud(speedKmh: Float) {
    Surface(shape = RoundedCornerShape(16.dp), color = HudBackground, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = speedKmh.roundToInt().toString(),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.labelSmall,
                color = HudSubtle
            )
        }
    }
}

@Composable
private fun LeanHud(leanDeg: Float) {
    val direction = when {
        leanDeg < -0.5f -> "L"
        leanDeg > 0.5f -> "R"
        else -> ""
    }
    Surface(shape = RoundedCornerShape(16.dp), color = HudBackground, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${abs(leanDeg).roundToInt()}\u00B0$direction",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "lean",
                style = MaterialTheme.typography.labelSmall,
                color = HudSubtle
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Composable pieces
// ---------------------------------------------------------------------------

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(placeholder) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun FakeSearchBar(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextSubtleDark
            )
            Text(
                text = text,
                color = TextSubtleDark,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun DirectionsCard(
    originTitle: String,
    destinationTitle: String,
    onEditOrigin: () -> Unit,
    onEditDestination: () -> Unit,
    onSwap: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EndpointRow(
                    dotColor = StatusGood,
                    text = originTitle,
                    onClick = onEditOrigin
                )
                HorizontalDivider(color = BorderDark)
                EndpointRow(
                    dotColor = Color(0xFFE53935),
                    text = destinationTitle,
                    onClick = onEditDestination
                )
            }
            Column {
                IconButton(onClick = onSwap) { Text("\u21C5", fontSize = 18.sp) }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close directions")
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(dotColor: Color, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResultRow(
    icon: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 18.sp)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSubtleDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(
    route: NavRoute,
    onClear: () -> Unit,
    onEndRide: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83C\uDFCD", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = route.durationText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StatusGood
                    )
                    Text(
                        text = "${route.distanceText} • fastest route",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtleDark
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear route")
                }
            }
            route.steps.firstOrNull()?.instruction?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSubtleDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onEndRide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("End Ride", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConnectionPill(state: ConnectionState, modifier: Modifier = Modifier) {
    val (text, color) = when (state) {
        is ConnectionState.Connected -> "Cluster connected" to StatusGood
        is ConnectionState.Connecting -> "Connecting…" to StatusWarning
        is ConnectionState.Reconnecting -> "Reconnecting…" to StatusWarning
        is ConnectionState.Scanning -> "Scanning…" to StatusWarning
        is ConnectionState.Error -> "Connection error" to Color(0xFFEF5350)
        else -> "Cluster disconnected" to Color(0xFF8A93A5)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF3A2E15)) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFD54F)
        )
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF4E1D1D)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB4AB)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFFFB4AB)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Map helpers
// ---------------------------------------------------------------------------

private fun markerOptions(context: Context, latLng: LatLng, bitmap: Bitmap): MarkerOptions =
    MarkerOptions()
        .position(latLng)
        .icon(IconFactory.getInstance(context).fromBitmap(bitmap))

/** Blue dot with a white ring — the rider's current position. */
private fun dotBitmap(fill: Int, ring: Int): Bitmap {
    val size = 72
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = ring
    canvas.drawCircle(36f, 36f, 34f, paint)
    paint.color = fill
    canvas.drawCircle(36f, 36f, 24f, paint)
    return bmp
}

/** Filled circle with a white center — origin/destination pin. */
private fun pinBitmap(color: Int): Bitmap {
    val size = 72
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = color
    canvas.drawCircle(36f, 36f, 34f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(36f, 36f, 12f, paint)
    return bmp
}
