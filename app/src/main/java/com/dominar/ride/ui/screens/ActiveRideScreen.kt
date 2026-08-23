package com.dominar.ride.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.dominar.ride.navigation.NavSession
import com.dominar.ride.navigation.NavTracker
import com.dominar.ride.navigation.NeshanApi
import com.dominar.ride.navigation.RouteOverlay
import com.dominar.ride.navigation.TrafficOverlay
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
 * Google-Maps-style navigation screen.
 *
 * Flow: search or long-press picks a destination -> route alternatives are
 * shown with a "Let's go" button -> tapping it starts live turn-by-turn
 * guidance (tilted camera locked on the rider, HUD, cluster forwarding).
 * All navigation state lives in [NavSession], owned by MainActivity, so
 * switching tabs never resets the ride.
 */
@SuppressLint("MissingPermission")
@Composable
fun ActiveRideScreen(app: AppState, nav: NavSession, onExit: () -> Unit) {
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

    // --- Screen-local UI state (map overlays are rebuilt on re-entry) ---
    var routing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var speedKmh by remember { mutableStateOf(0f) }
    var lastBearing by remember { mutableStateOf(0.0) }
    var stepPreview by remember { mutableStateOf(false) }

    // --- Search state ---
    var editingField by remember { mutableStateOf<EndpointField?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NavPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // --- Map overlays (route lines live in RouteOverlay style layers) ---
    var myMarker by remember { mutableStateOf<Marker?>(null) }
    var originMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }

    // ---------- Helpers ----------

    fun navCamera(m: MapLibreMap, ll: LatLng? = null) {
        val target = ll ?: nav.myLocation ?: return
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(17.0)
                    .tilt(55.0)
                    .bearing(lastBearing)
                    .build()
            ),
            900
        )
    }

    fun clearRouteLines() {
        map?.let { RouteOverlay.clear(it) }
    }

    // Route lines are style layers anchored below the traffic raster, so
    // live congestion colors paint directly onto the lines and riders can
    // compare traffic across the alternatives (Google-Maps-style).
    fun drawRoutes(m: MapLibreMap) {
        RouteOverlay.draw(m, nav.routes, nav.selectedRouteIndex)
    }

    fun fitRoute(m: MapLibreMap, r: NavRoute) {
        if (r.points.isEmpty()) return
        if (r.points.size < 2) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(r.points.first(), 14.0))
            return
        }
        val builder = LatLngBounds.Builder()
        r.points.forEach { builder.include(it) }
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
    }

    fun endNavigation() {
        if (nav.navigating) app.sendNavPacket(DominarProtocol.buildNavStopPacket())
        nav.navigating = false
        nav.tracker = null
        nav.progress = null
        nav.sentArrived = false
    }

    fun fetchRoutes() {
        val dest = nav.destination ?: return
        val start = nav.origin?.location ?: nav.myLocation
        if (start == null) {
            error = "No GPS fix yet \u2014 pick a starting point from search " +
                "or a long-press on the map"
            return
        }
        routing = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { NeshanApi.directions(start, dest.location) }
            }
            routing = false
            result.onSuccess { rs ->
                endNavigation()
                nav.clearRoutes()
                nav.routes = rs
                map?.let { m ->
                    TrafficOverlay.refresh(m)
                    drawRoutes(m)
                    fitRoute(m, rs.first())
                }
            }.onFailure { e ->
                error = e.message ?: "Routing failed"
            }
        }
    }

    fun setDestination(place: NavPlace?) {
        nav.destination = place
        val m = map
        destMarker?.let { m?.removeMarker(it) }
        destMarker = null
        endNavigation()
        clearRouteLines()
        nav.clearRoutes()
        stepPreview = false
        if (place != null) {
            if (m != null) {
                destMarker = m.addMarker(
                    markerOptions(context, place.location, pinBitmap(0xFFE53935.toInt()))
                )
            }
            fetchRoutes()
        }
    }

    fun setOrigin(place: NavPlace?) {
        nav.origin = place
        val m = map
        originMarker?.let { m?.removeMarker(it) }
        originMarker = null
        endNavigation()
        clearRouteLines()
        nav.clearRoutes()
        stepPreview = false
        if (place != null && m != null) {
            originMarker = m.addMarker(
                markerOptions(context, place.location, pinBitmap(0xFF2E7D32.toInt()))
            )
        }
        if (nav.destination != null) fetchRoutes()
    }

    fun swapEndpoints() {
        val dest = nav.destination ?: return
        val newDestination = nav.origin
            ?: nav.myLocation?.let { NavPlace("Your location", null, it) }
            ?: return
        val m = map
        endNavigation()
        clearRouteLines()
        nav.clearRoutes()
        stepPreview = false
        nav.origin = dest
        nav.destination = newDestination
        originMarker?.let { m?.removeMarker(it) }
        destMarker?.let { m?.removeMarker(it) }
        if (m != null) {
            originMarker = m.addMarker(
                markerOptions(context, dest.location, pinBitmap(0xFF2E7D32.toInt()))
            )
            destMarker = m.addMarker(
                markerOptions(context, newDestination.location, pinBitmap(0xFFE53935.toInt()))
            )
        }
        fetchRoutes()
    }

    fun showStep(index: Int) {
        val r = nav.selectedRoute ?: return
        if (r.steps.isEmpty()) return
        val idx = index.coerceIn(0, r.steps.lastIndex)
        nav.previewStepIndex = idx
        val target = r.steps[idx].location ?: return
        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(16.5).tilt(45.0).build()
            )
        )
    }

    fun startNavigation() {
        val r = nav.selectedRoute ?: return
        stepPreview = false
        nav.previewStepIndex = 0
        nav.tracker = NavTracker(r)
        nav.progress = null
        nav.sentArrived = false
        nav.navigating = true
        nav.cameraLocked = true
        app.sendNavPacket(DominarProtocol.buildNavStartPacket())
        map?.let { m ->
            drawRoutes(m)
            if (nav.myLocation != null) {
                navCamera(m)
            } else {
                // No GPS fix: jump to the route start for manual stepping.
                r.steps.firstOrNull()?.location?.let { start ->
                    m.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(start).zoom(16.5).tilt(45.0).build()
                        )
                    )
                }
            }
        }
    }

    fun clearAll() {
        endNavigation()
        clearRouteLines()
        nav.reset()
        stepPreview = false
        val m = map
        originMarker?.let { m?.removeMarker(it) }
        originMarker = null
        destMarker?.let { m?.removeMarker(it) }
        destMarker = null
        nav.myLocation?.let { ll ->
            m?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(ll).zoom(15.0).tilt(0.0).bearing(0.0).build()
                )
            )
        }
    }

    fun locateMe(recenter: Boolean) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        fused.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { loc ->
            if (loc == null) {
                if (recenter) error = "Couldn't get a GPS fix \u2014 is location turned on?"
                return@addOnSuccessListener
            }
            val ll = LatLng(loc.latitude, loc.longitude)
            nav.myLocation = ll
            val m = map ?: return@addOnSuccessListener
            myMarker?.let { m.removeMarker(it) }
            myMarker = m.addMarker(
                markerOptions(context, ll, dotBitmap(0xFF1E76FF.toInt(), 0xFFFFFFFF.toInt()))
            )
            if (recenter && !nav.navigating) {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 15.5))
            }
        }.addOnFailureListener {
            if (recenter) error = "Location unavailable \u2014 check the location permission"
        }
    }

    // ---------- Effects ----------

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

    // Lean sensor runs while this screen is visible and the HUD toggle is on.
    DisposableEffect(app.hudShowLeanAngle) {
        if (app.hudShowLeanAngle) perf.startLeanSensor()
        onDispose { perf.stopLeanSensor() }
    }

    // Continuous location updates while navigating: HUD + cluster forwarding.
    DisposableEffect(nav.navigating) {
        if (!nav.navigating) {
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
                nav.myLocation = ll
                speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
                if (loc.hasBearing()) lastBearing = loc.bearing.toDouble()
                nav.progress = nav.tracker?.update(ll)
                map?.let { m ->
                    myMarker?.let { m.removeMarker(it) }
                    myMarker = m.addMarker(
                        markerOptions(
                            context, ll,
                            dotBitmap(0xFF1E76FF.toInt(), 0xFFFFFFFF.toInt())
                        )
                    )
                    if (nav.cameraLocked) navCamera(m, ll)
                }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { fused.removeLocationUpdates(callback) }
    }

    // Forward each progress update to the cluster (~1/s, mirrors Bajaj Ride).
    LaunchedEffect(nav.progress) {
        if (!nav.navigating) return@LaunchedEffect
        val p = nav.progress ?: return@LaunchedEffect
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
        if (p.arrived && !nav.sentArrived) {
            nav.sentArrived = true
            app.sendNavPacket(DominarProtocol.buildDestinationReachedPacket())
        }
    }

    // Initialize the map: Neshan style, camera, gesture + long-press listeners.
    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.setStyle(Style.Builder().fromUri(NESHAN_STYLE_URI)) {
                TrafficOverlay.refresh(m)
                m.cameraPosition = CameraPosition.Builder()
                    .target(nav.myLocation ?: LatLng(35.6892, 51.3890)) // Tehran fallback
                    .zoom(11.0)
                    .build()
                map = m
            }
            // Panning the map unlocks the follow camera (recenter FAB re-locks).
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    nav.cameraLocked = false
                }
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

    // Live traffic: refresh the overlay every 5 minutes while this tab is open.
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        while (true) {
            delay(TrafficOverlay.REFRESH_INTERVAL_MS)
            TrafficOverlay.refresh(m)
        }
    }

    // Restore session overlays after (re-)entering the tab.
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        nav.myLocation?.let { ll ->
            myMarker = m.addMarker(
                markerOptions(context, ll, dotBitmap(0xFF1E76FF.toInt(), 0xFFFFFFFF.toInt()))
            )
        }
        nav.origin?.let {
            originMarker = m.addMarker(
                markerOptions(context, it.location, pinBitmap(0xFF2E7D32.toInt()))
            )
        }
        nav.destination?.let {
            destMarker = m.addMarker(
                markerOptions(context, it.location, pinBitmap(0xFFE53935.toInt()))
            )
        }
        if (nav.routes.isNotEmpty()) {
            drawRoutes(m)
            if (nav.navigating && nav.myLocation != null) navCamera(m)
            else nav.selectedRoute?.let { fitRoute(m, it) }
        } else {
            locateMe(recenter = true)
        }
    }

    // Debounced search-as-you-type.
    LaunchedEffect(query, editingField) {
        results = emptyList()
        if (editingField == null || query.trim().length < 2) return@LaunchedEffect
        searching = true
        delay(350)
        val center = nav.myLocation ?: LatLng(35.6997, 51.3380)
        val found = withContext(Dispatchers.IO) {
            runCatching { NeshanApi.search(query.trim(), center) }
        }
        searching = false
        found.onSuccess { results = it }.onFailure { error = it.message }
    }

    // ---------- UI ----------

    val hasFix = nav.myLocation != null

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })

        // ---------- Top: search / directions / guidance banner ----------
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

                nav.navigating -> {
                    // Clean top area while riding: just the guidance banner.
                }

                nav.destination == null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoundIconButton(Icons.Default.ArrowBack, "Back") { onExit() }
                    FakeSearchBar(
                        text = "Where to?",
                        modifier = Modifier.weight(1f),
                        onClick = { editingField = EndpointField.DESTINATION }
                    )
                }

                else -> DirectionsCard(
                    originTitle = nav.origin?.title ?: "Your location",
                    destinationTitle = nav.destination?.title ?: "",
                    onEditOrigin = { editingField = EndpointField.ORIGIN; query = "" },
                    onEditDestination = {
                        editingField = EndpointField.DESTINATION; query = ""
                    },
                    onSwap = { swapEndpoints() },
                    onClose = { clearAll() }
                )
            }

            // ---------- Guidance banner / manual step preview ----------
            if (editingField == null && nav.navigating) {
                val r = nav.selectedRoute
                if (!hasFix && r != null) {
                    Spacer(Modifier.height(8.dp))
                    StepBar(
                        route = r,
                        index = nav.previewStepIndex,
                        onPrev = { showStep(nav.previewStepIndex - 1) },
                        onNext = { showStep(nav.previewStepIndex + 1) }
                    )
                } else if (app.hudShowNextTurn) {
                    nav.progress?.let { p ->
                        Spacer(Modifier.height(8.dp))
                        NextTurnBanner(p)
                    }
                }
            }
            if (editingField == null && !nav.navigating && stepPreview) {
                nav.selectedRoute?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    StepBar(
                        route = r,
                        index = nav.previewStepIndex,
                        onPrev = { showStep(nav.previewStepIndex - 1) },
                        onNext = { showStep(nav.previewStepIndex + 1) }
                    )
                }
            }

            if (nav.navigating) {
                Spacer(Modifier.height(8.dp))
                ConnectionPill(
                    state = connState,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            if (BuildConfig.NESHAN_SDK_KEY.isBlank() || BuildConfig.NESHAN_API_KEY.isBlank()) {
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    "Neshan keys missing: add NESHAN_SDK_KEY (map) and NESHAN_API_KEY " +
                        "(search & routing) to local.properties \u2014 see README."
                )
            }
            error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(msg) { error = null }
            }
        }

        // ---------- Speed / lean HUD ----------
        if (nav.navigating && (app.hudShowSpeed || app.hudShowLeanAngle)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (app.hudShowSpeed) SpeedHud(speedKmh)
                if (app.hudShowLeanAngle) LeanHud(leanDeg)
            }
        }

        // ---------- My-location / recenter button ----------
        FloatingActionButton(
            onClick = {
                nav.cameraLocked = true
                val m = map
                if (nav.navigating && hasFix && m != null) navCamera(m)
                else locateMe(recenter = true)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 16.dp,
                    bottom = when {
                        nav.navigating -> 140.dp
                        nav.routes.isNotEmpty() || routing -> 210.dp
                        else -> 96.dp
                    }
                ),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "My location")
        }

        // ---------- Bottom: route options / live nav bar ----------
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
                            text = "Finding routes\u2026",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                nav.navigating -> nav.selectedRoute?.let { r ->
                    NavBottomBar(
                        progress = nav.progress,
                        route = r,
                        onEnd = { clearAll() }
                    )
                }

                nav.routes.isNotEmpty() && editingField == null -> RouteOptionsCard(
                    routes = nav.routes,
                    selected = nav.selectedRouteIndex,
                    stepPreviewOn = stepPreview,
                    onSelect = { i ->
                        nav.selectedRouteIndex = i
                        nav.previewStepIndex = 0
                        map?.let { m ->
                            drawRoutes(m)
                            nav.routes.getOrNull(i)?.let { r -> fitRoute(m, r) }
                        }
                    },
                    onToggleStepPreview = {
                        stepPreview = !stepPreview
                        if (stepPreview) {
                            showStep(nav.previewStepIndex)
                        } else {
                            nav.selectedRoute?.let { r ->
                                map?.let { m -> fitRoute(m, r) }
                            }
                        }
                    },
                    onGo = { startNavigation() }
                )

                else -> {
                    // Browse mode: keep the map clean, no stray buttons.
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

/** Manual step-by-step route preview (used when there is no GPS fix too). */
@Composable
private fun StepBar(route: NavRoute, index: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val step = route.steps.getOrNull(index)
    Surface(shape = RoundedCornerShape(16.dp), color = HudBackground, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, enabled = index > 0) {
                Text("\u25C0", color = if (index > 0) Color.White else HudSubtle)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Step ${index + 1} of ${route.steps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudSubtle
                )
                val text = step?.instruction?.takeIf { it.isNotBlank() }
                    ?: step?.name?.takeIf { it.isNotBlank() }
                    ?: "Continue"
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onNext, enabled = index < route.steps.lastIndex) {
                Text(
                    "\u25B6",
                    color = if (index < route.steps.lastIndex) Color.White else HudSubtle
                )
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

/** Route alternatives + "Let's go" (Google-Maps-style route preview). */
@Composable
private fun RouteOptionsCard(
    routes: List<NavRoute>,
    selected: Int,
    stepPreviewOn: Boolean,
    onSelect: (Int) -> Unit,
    onToggleStepPreview: () -> Unit,
    onGo: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                routes.forEachIndexed { i, r ->
                    RouteChip(
                        route = r,
                        label = if (i == 0) "Fastest" else "Alt $i",
                        selected = i == selected,
                        onClick = { onSelect(i) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleStepPreview,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(if (stepPreviewOn) "Hide steps" else "Steps")
                }
                Button(
                    onClick = onGo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Let's go", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun RouteChip(route: NavRoute, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = route.durationText.ifBlank { "\u2014" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = listOf(route.distanceText, label)
                .filter { it.isNotBlank() }
                .joinToString(" \u2022 "),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color(0xCCFFFFFF) else TextSubtleDark
        )
    }
}

/** Slim bar while navigating: remaining distance, ETA and an End button. */
@Composable
private fun NavBottomBar(progress: NavProgress?, route: NavRoute, onEnd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                val headline = progress?.let {
                    "${hudDistanceText(it.distanceLeftMeters)} left"
                } ?: "${route.durationText} \u2022 ${route.distanceText}"
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusGood
                )
                val subtitle = progress?.let {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.SECOND, it.etaSeconds.toInt())
                    }
                    String.format(
                        Locale.US, "Arrive ~ %d:%02d",
                        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
                    )
                } ?: "Waiting for GPS\u2026"
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSubtleDark
                )
            }
            Button(
                onClick = onEnd,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) {
                Text("End", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ConnectionPill(state: ConnectionState, modifier: Modifier = Modifier) {
    val (text, color) = when (state) {
        is ConnectionState.Connected -> "Cluster connected" to StatusGood
        is ConnectionState.Connecting -> "Connecting\u2026" to StatusWarning
        is ConnectionState.Reconnecting -> "Reconnecting\u2026" to StatusWarning
        is ConnectionState.Scanning -> "Scanning\u2026" to StatusWarning
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
