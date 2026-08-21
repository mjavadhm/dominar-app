package com.dominar.ride.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.ContextThemeWrapper
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.carto.graphics.Color as CartoColor
import com.carto.styles.LineStyle
import com.carto.styles.LineStyleBuilder
import com.carto.styles.MarkerStyleBuilder
import com.carto.utils.BitmapUtils
import com.dominar.ride.BuildConfig
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.navigation.NavPlace
import com.dominar.ride.navigation.NavRoute
import com.dominar.ride.navigation.NeshanApi
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.neshan.common.model.LatLng
import org.neshan.mapsdk.MapView
import org.neshan.mapsdk.model.Marker
import org.neshan.mapsdk.model.Polyline

private enum class EndpointField { ORIGIN, DESTINATION }

/**
 * Google-Maps-style navigation screen backed by the Neshan map SDK and
 * Neshan web services (search / reverse geocode / motorcycle routing).
 *
 * - Starts centered on the rider's current GPS location.
 * - "Where to?" search bar picks a destination; origin defaults to your location.
 * - Both endpoints can be changed via search or a long-press on the map.
 */
@SuppressLint("MissingPermission")
@Composable
fun ActiveRideScreen(app: AppState, onStopRide: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connState by app.connectionState.collectAsState()

    val mapView = remember {
        val themed =
            ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light)
        MapView(themed).apply { setZoom(15f, 0f) }
    }

    // --- Navigation state ---
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var origin by remember { mutableStateOf<NavPlace?>(null) } // null == my location
    var destination by remember { mutableStateOf<NavPlace?>(null) }
    var route by remember { mutableStateOf<NavRoute?>(null) }
    var routing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
        routeLine?.let { mapView.removePolyline(it) }
        routeLine = null
        route = null
    }

    fun fetchRoute() {
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
                val line = Polyline(ArrayList(r.points), routeLineStyle())
                mapView.addPolyline(line)
                routeLine = line
                fitCamera(mapView, start, dest.location)
            }.onFailure { e ->
                error = e.message ?: "Routing failed"
            }
        }
    }

    fun setDestination(place: NavPlace?) {
        destination = place
        destMarker?.let { mapView.removeMarker(it) }
        destMarker = null
        clearRouteOverlay()
        if (place != null) {
            val m = markerOf(place.location, pinBitmap(0xFFE53935.toInt()), 32f)
            mapView.addMarker(m)
            destMarker = m
            mapView.moveCamera(place.location, 0.3f)
            fetchRoute()
        }
    }

    fun setOrigin(place: NavPlace?) {
        origin = place
        originMarker?.let { mapView.removeMarker(it) }
        originMarker = null
        clearRouteOverlay()
        if (place != null) {
            val m = markerOf(place.location, pinBitmap(0xFF2E7D32.toInt()), 28f)
            mapView.addMarker(m)
            originMarker = m
        }
        if (destination != null) fetchRoute()
    }

    fun swapEndpoints() {
        val dest = destination ?: return
        val newDestination = origin
            ?: myLocation?.let { NavPlace("Your location", null, it) }
            ?: return
        origin = dest
        destination = newDestination
        originMarker?.let { mapView.removeMarker(it) }
        destMarker?.let { mapView.removeMarker(it) }
        val om = markerOf(dest.location, pinBitmap(0xFF2E7D32.toInt()), 28f)
        mapView.addMarker(om)
        originMarker = om
        val dm = markerOf(newDestination.location, pinBitmap(0xFFE53935.toInt()), 32f)
        mapView.addMarker(dm)
        destMarker = dm
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
            myMarker?.let { mapView.removeMarker(it) }
            val m = markerOf(ll, dotBitmap(0xFF1E76FF.toInt(), 0xFFFFFFFF.toInt()), 20f)
            mapView.addMarker(m)
            myMarker = m
            if (recenter) {
                mapView.moveCamera(ll, 0.3f)
                mapView.setZoom(15.5f, 0.3f)
            }
        }.addOnFailureListener {
            if (recenter) error = "Location unavailable — check the location permission"
        }
    }

    // Center on the rider as soon as the screen opens (like Google Maps),
    // and let a long-press drop a pin for the field being edited.
    LaunchedEffect(Unit) {
        locateMe(recenter = true)
        mapView.setOnMapLongClickListener { latLng ->
            scope.launch {
                val target = editingField
                editingField = null
                query = ""
                val place = NavPlace("Dropped pin", null, latLng)
                if (target == EndpointField.ORIGIN) setOrigin(place) else setDestination(place)
            }
        }
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

            Spacer(Modifier.height(8.dp))
            ConnectionPill(
                state = connState,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (BuildConfig.NESHAN_API_KEY.isBlank()) {
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    "Search & routing need a Neshan API key. " +
                        "Add NESHAN_API_KEY to local.properties (see README)."
                )
            }
            error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(msg) { error = null }
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

private fun markerOf(latLng: LatLng, bitmap: Bitmap, size: Float): Marker {
    val style = MarkerStyleBuilder().apply {
        this.size = size
        this.bitmap = BitmapUtils.createBitmapFromAndroidBitmap(bitmap)
    }.buildStyle()
    return Marker(latLng, style)
}

private fun routeLineStyle(): LineStyle =
    LineStyleBuilder().apply {
        color = CartoColor(30.toShort(), 118.toShort(), 255.toShort(), 235.toShort())
        width = 8f
    }.buildStyle()

/** Blue dot with a white ring — the rider's current position. */
private fun dotBitmap(fill: Int, ring: Int): Bitmap {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = ring
    canvas.drawCircle(48f, 48f, 44f, paint)
    paint.color = fill
    canvas.drawCircle(48f, 48f, 32f, paint)
    return bmp
}

/** Filled circle with a white center — origin/destination pin. */
private fun pinBitmap(color: Int): Bitmap {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = color
    canvas.drawCircle(48f, 48f, 44f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(48f, 48f, 16f, paint)
    return bmp
}

private fun fitCamera(mapView: MapView, a: LatLng, b: LatLng) {
    val mid = LatLng(
        (a.latitude + b.latitude) / 2.0,
        (a.longitude + b.longitude) / 2.0
    )
    val km = haversineKm(a, b)
    val zoom = when {
        km < 1 -> 14.5f
        km < 3 -> 13.5f
        km < 7 -> 12.5f
        km < 15 -> 11.5f
        km < 40 -> 10.5f
        km < 100 -> 9f
        km < 300 -> 7.5f
        else -> 6f
    }
    mapView.moveCamera(mid, 0.4f)
    mapView.setZoom(zoom, 0.4f)
}

private fun haversineKm(a: LatLng, b: LatLng): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
        sin(dLon / 2).pow(2)
    return 2 * earthRadiusKm * asin(sqrt(h))
}
