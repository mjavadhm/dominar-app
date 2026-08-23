package com.dominar.ride.navigation

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.exponential
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Route rendering as native style layers (GeoJSON sources + line layers).
 *
 * Compared to the old annotation polylines this gives:
 *  - crisp rounded joins/caps plus a casing, like Google Maps;
 *  - zoom-scaled widths, so the line hugs the road at every zoom level;
 *  - control over layer order: routes sit *below* [TrafficOverlay], which
 *    means live congestion colors paint directly onto the route lines and
 *    riders can compare traffic across the alternatives.
 *
 * Markers stay annotations and always render on top of everything.
 */
object RouteOverlay {

    private const val ALT_SOURCE_ID = "route-alt-source"
    private const val SEL_SOURCE_ID = "route-sel-source"

    private const val ALT_CASING_ID = "route-alt-casing"
    private const val ALT_LINE_ID = "route-alt-line"
    private const val SEL_CASING_ID = "route-sel-casing"
    private const val SEL_LINE_ID = "route-sel-line"

    private const val SELECTED_COLOR = "#1E76FF"
    private const val SELECTED_CASING_COLOR = "#0D47A1"
    private const val ALT_COLOR = "#AEB6C2"
    private const val ALT_CASING_COLOR = "#7A8494"

    /** Renders [routes], highlighting [selectedIndex]. Call again on reselect. */
    fun draw(map: MapLibreMap, routes: List<NavRoute>, selectedIndex: Int) {
        val style = map.style ?: return
        runCatching {
            ensureLayers(style)
            val alternatives = routes.filterIndexed { i, _ -> i != selectedIndex }
            setSource(style, ALT_SOURCE_ID, alternatives)
            setSource(style, SEL_SOURCE_ID, listOfNotNull(routes.getOrNull(selectedIndex)))
        }
    }

    /** Removes all route lines from the map (sources stay for reuse). */
    fun clear(map: MapLibreMap) {
        val style = map.style ?: return
        runCatching {
            setSource(style, ALT_SOURCE_ID, emptyList())
            setSource(style, SEL_SOURCE_ID, emptyList())
        }
    }

    // -----------------------------------------------------------------------

    private fun setSource(style: Style, sourceId: String, routes: List<NavRoute>) {
        val features = routes
            .filter { it.points.size >= 2 }
            .map { route ->
                Feature.fromGeometry(
                    LineString.fromLngLats(
                        route.points.map { Point.fromLngLat(it.longitude, it.latitude) }
                    )
                )
            }
        style.getSourceAs<GeoJsonSource>(sourceId)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun ensureLayers(style: Style) {
        if (style.getLayer(SEL_LINE_ID) != null) return

        style.addSource(GeoJsonSource(ALT_SOURCE_ID))
        style.addSource(GeoJsonSource(SEL_SOURCE_ID))

        // Draw order (bottom to top): alt casing, alt line, selected casing,
        // selected line — all anchored below the traffic raster.
        addLayer(style, lineLayer(ALT_CASING_ID, ALT_SOURCE_ID, ALT_CASING_COLOR, casing = true))
        addLayer(style, lineLayer(ALT_LINE_ID, ALT_SOURCE_ID, ALT_COLOR, casing = false))
        addLayer(style, lineLayer(SEL_CASING_ID, SEL_SOURCE_ID, SELECTED_CASING_COLOR, casing = true))
        addLayer(style, lineLayer(SEL_LINE_ID, SEL_SOURCE_ID, SELECTED_COLOR, casing = false))
    }

    /** Routes go right below the traffic raster so congestion paints on them. */
    private fun addLayer(style: Style, layer: LineLayer) {
        if (style.getLayer(TrafficOverlay.LAYER_ID) != null) {
            style.addLayerBelow(layer, TrafficOverlay.LAYER_ID)
        } else {
            style.addLayer(layer)
        }
    }

    private fun lineLayer(id: String, sourceId: String, color: String, casing: Boolean) =
        LineLayer(id, sourceId).withProperties(
            lineColor(color),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
            // Width scales with zoom so the line matches road widths and
            // never looks detached from the map while zooming.
            lineWidth(
                interpolate(
                    exponential(1.5f), zoom(),
                    stop(10f, if (casing) 5.5f else 3.5f),
                    stop(14f, if (casing) 9f else 6f),
                    stop(18f, if (casing) 16f else 11f)
                )
            )
        )
}
