package com.dominar.ride.navigation

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dominar.ride.BuildConfig
import com.dominar.ride.debug.DebugLog
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Live traffic overlay for the Neshan MapLibre style.
 *
 * Neshan does not publicly document a traffic tile endpoint for mobile
 * MapLibre apps, so this overlay self-discovers one: on first [refresh] it
 * probes a small set of candidate tile URLs (different hosts, with each
 * configured key and without a key) against a fixed Tehran tile, then keeps
 * the first combination that returns an image. Every attempt is logged under
 * the `TrafficOverlay` tag (`adb logcat -s TrafficOverlay`) and mirrored to
 * the in-app debug log (Settings > Debug), so a failing setup is diagnosable
 * from the device instead of guesswork.
 *
 * A timestamp query param busts the tile cache, so re-adding the source with
 * a fresh `ts` pulls the latest congestion colors.
 *
 * [RouteOverlay] anchors the route line layers *below* this layer, so live
 * congestion colors paint directly onto the route lines (Google-Maps-style).
 * Markers are annotations and always render above everything.
 */
object TrafficOverlay {

    /** How often the overlay should be re-fetched while the map is visible. */
    const val REFRESH_INTERVAL_MS = 5 * 60_000L

    /** Public so [RouteOverlay] can anchor the route lines below this layer. */
    const val LAYER_ID = "neshan-traffic-layer"

    private const val TAG = "TrafficOverlay"
    private const val SOURCE_ID = "neshan-traffic"

    /** A central-Tehran tile used to verify that a candidate endpoint serves imagery. */
    private const val PROBE_TILE = "12/2632/1612.png"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val probing = AtomicBoolean(false)

    /** Tile URL templates confirmed to work; null until a probe succeeds. */
    @Volatile
    private var resolvedTemplates: Array<String>? = null

    /** When the last full probe came up empty, to avoid hammering the network. */
    @Volatile
    private var lastFailedProbeAt = 0L

    /**
     * (Re-)adds the traffic layer with a fresh cache-busting timestamp.
     *
     * Must be called from the main thread. If no working endpoint is known
     * yet, a background probe starts and the layer is added once it succeeds.
     */
    fun refresh(map: MapLibreMap) {
        val templates = resolvedTemplates
        if (templates != null) {
            apply(map, templates)
            return
        }
        if (System.currentTimeMillis() - lastFailedProbeAt < REFRESH_INTERVAL_MS) return
        if (!probing.compareAndSet(false, true)) return
        thread(name = "traffic-probe") {
            val found = probeCandidates()
            probing.set(false)
            if (found == null) {
                lastFailedProbeAt = System.currentTimeMillis()
                Log.w(TAG, "No traffic tile endpoint responded; overlay stays hidden")
                DebugLog.log(TAG, "No traffic tile endpoint responded; overlay stays hidden")
            } else {
                resolvedTemplates = found
                mainHandler.post { runCatching { apply(map, found) } }
            }
        }
    }

    /** Adds (or re-adds) the raster source and layer on top of the style. */
    private fun apply(map: MapLibreMap, templates: Array<String>) {
        val style = map.style ?: return
        val ts = System.currentTimeMillis()
        val urls = templates
            .map { "$it${if ('?' in it) '&' else '?'}ts=$ts" }
            .toTypedArray()
        runCatching {
            style.removeLayer(LAYER_ID)
            style.removeSource(SOURCE_ID)
            style.addSource(RasterSource(SOURCE_ID, TileSet("2.1.0", *urls), 256))
            style.addLayer(
                RasterLayer(LAYER_ID, SOURCE_ID)
                    .withProperties(rasterOpacity(0.85f))
            )
        }.onFailure {
            Log.w(TAG, "Failed to add traffic layer: ${it.message}")
            DebugLog.log(TAG, "Failed to add traffic layer: ${it.message}")
        }
    }

    /** Tries every candidate template set and returns the first that serves an image. */
    private fun probeCandidates(): Array<String>? {
        for (templates in candidateTemplates()) {
            val sample = templates.first().replace("{z}/{x}/{y}.png", PROBE_TILE)
            if (probeUrl(sample)) {
                Log.i(TAG, "Traffic tiles resolved via ${redactKey(templates.first())}")
                DebugLog.log(TAG, "Traffic tiles resolved via ${redactKey(templates.first())}")
                return templates
            }
        }
        return null
    }

    /**
     * Candidate tile URL template sets, most likely first: the subdomain
     * endpoint used by Neshan's web SDK with each configured key (SDK, web,
     * service), then the plain tile host, then key-less variants.
     */
    private fun candidateTemplates(): List<Array<String>> {
        val keys = listOf(
            BuildConfig.NESHAN_SDK_KEY,
            BuildConfig.NESHAN_WEB_KEY,
            BuildConfig.NESHAN_API_KEY,
        ).filter { it.isNotBlank() }.distinct()

        val candidates = mutableListOf<Array<String>>()
        for (key in keys) {
            candidates += subdomainUrls("?key=$key")
            candidates += arrayOf("https://tile.neshan.org/traffic/{z}/{x}/{y}.png?key=$key")
        }
        candidates += subdomainUrls("")
        candidates += arrayOf("https://tile.neshan.org/traffic/{z}/{x}/{y}.png")
        return candidates
    }

    private fun subdomainUrls(query: String): Array<String> =
        arrayOf("1", "2", "3", "4").map { sub ->
            "https://$sub.neshan.org/traffic/{z}/{x}/{y}.png$query"
        }.toTypedArray()

    /** GETs [url] and reports whether it returned an HTTP 200 image. */
    private fun probeUrl(url: String): Boolean = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            val type = conn.contentType.orEmpty()
            val ok = code == 200 && type.startsWith("image")
            // Keep the raw (non-image) response body so key/endpoint errors
            // are diagnosable from the in-app debug log.
            val rawBody = if (ok) "" else {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText().take(2000) } ?: ""
            }
            Log.i(TAG, "Probe ${redactKey(url)} -> HTTP $code ($type)")
            DebugLog.log(TAG, "Probe ${redactKey(url)} -> HTTP $code ($type)", rawBody)
            ok
        } finally {
            conn.disconnect()
        }
    }.getOrElse {
        Log.w(TAG, "Probe ${redactKey(url)} failed: ${it.message}")
        DebugLog.log(TAG, "Probe ${redactKey(url)} failed: ${it.message}")
        false
    }

    /** Keeps API keys out of logs. */
    private fun redactKey(url: String): String =
        url.replace(Regex("key=[^&]+"), "key=***")
}
