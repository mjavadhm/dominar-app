package com.dominar.ride.navigation

import com.dominar.ride.BuildConfig
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Live traffic overlay for the Neshan MapLibre style.
 *
 * Neshan serves live traffic as raster tiles from 1-4.neshan.org — the same
 * endpoint its own web SDK hits when `traffic: true` is set. A timestamp
 * query param busts the tile cache, so re-adding the source with a fresh
 * `ts` pulls the latest congestion colors.
 *
 * Route polylines and markers are annotations, which always render above
 * style layers, so the overlay never hides them.
 */
object TrafficOverlay {

    /** How often the overlay should be re-fetched while the map is visible. */
    const val REFRESH_INTERVAL_MS = 5 * 60_000L

    private const val SOURCE_ID = "neshan-traffic"
    private const val LAYER_ID = "neshan-traffic-layer"

    private fun tileUrls(ts: Long): Array<String> {
        val key = BuildConfig.NESHAN_SDK_KEY.ifBlank { BuildConfig.NESHAN_API_KEY }
        return arrayOf("1", "2", "3", "4").map { sub ->
            "{{https://$sub.neshan.org/traffic/{z}}}/{x}/{y}.png?key=$key&ts=$ts"
        }.toTypedArray()
    }

    /** (Re-)adds the traffic layer with a fresh cache-busting timestamp. */
    fun refresh(map: MapLibreMap) {
        val style = map.style ?: return
        runCatching {
            style.removeLayer(LAYER_ID)
            style.removeSource(SOURCE_ID)
            val tiles = TileSet("2.1.0", *tileUrls(System.currentTimeMillis()))
            style.addSource(RasterSource(SOURCE_ID, tiles, 256))
            style.addLayer(
                RasterLayer(LAYER_ID, SOURCE_ID)
                    .withProperties(rasterOpacity(0.85f))
            )
        }
    }
}
