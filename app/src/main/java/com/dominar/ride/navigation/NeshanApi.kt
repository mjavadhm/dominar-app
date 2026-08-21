package com.dominar.ride.navigation

import com.dominar.ride.BuildConfig
import org.json.JSONObject
import org.neshan.common.model.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A place returned by search / picked on the map. */
data class NavPlace(
    val title: String,
    val address: String?,
    val location: LatLng,
)

data class NavRouteStep(
    val instruction: String,
    val distanceText: String,
    val name: String,
)

data class NavRoute(
    val points: List<LatLng>,
    val distanceText: String,
    val durationText: String,
    val steps: List<NavRouteStep>,
)

class NeshanApiException(message: String) : Exception(message)

/**
 * Thin client for the Neshan web services (https://platform.neshan.org/api/).
 * All functions are blocking — call them from Dispatchers.IO.
 *
 * Requires a "Web service" API key set as NESHAN_API_KEY (see README).
 */
object NeshanApi {

    private const val BASE = "https://api.neshan.org"

    private fun get(url: String): JSONObject {
        val key = BuildConfig.NESHAN_API_KEY
        if (key.isBlank()) {
            throw NeshanApiException(
                "Neshan API key missing — add NESHAN_API_KEY to local.properties"
            )
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Api-Key", key)
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            return when (code) {
                in 200..299 -> JSONObject(body)
                401, 403 -> throw NeshanApiException("Neshan API key is invalid (HTTP $code)")
                429 -> throw NeshanApiException("Neshan API rate limit exceeded")
                470, 480, 481, 482, 485 ->
                    throw NeshanApiException("Neshan API error $code — check key type/quota")
                else -> throw NeshanApiException("Neshan API failed (HTTP $code)")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Search API v1 — finds places near [around]. */
    fun search(term: String, around: LatLng): List<NavPlace> {
        val url = "$BASE/v1/search" +
            "?term=${URLEncoder.encode(term, "UTF-8")}" +
            "&lat=${around.latitude}&lng=${around.longitude}"
        val json = get(url)
        val items = json.optJSONArray("items") ?: return emptyList()
        val places = ArrayList<NavPlace>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val loc = o.optJSONObject("location") ?: continue
            places.add(
                NavPlace(
                    title = o.optString("title", "Unknown"),
                    address = o.optString("address").takeIf { it.isNotBlank() },
                    // Neshan returns location as x = lng, y = lat
                    location = LatLng(loc.optDouble("y"), loc.optDouble("x")),
                )
            )
        }
        return places
    }

    /** Reverse geocoding v5 — best-effort address for a point (null on failure). */
    fun reverse(location: LatLng): String? = runCatching {
        val json = get("$BASE/v5/reverse?lat=${location.latitude}&lng=${location.longitude}")
        json.optString("formatted_address").takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Direction API v4 — motorcycle routing. */
    fun direction(origin: LatLng, destination: LatLng): NavRoute {
        val url = "$BASE/v4/direction" +
            "?type=motorcycle" +
            "&origin=${origin.latitude},${origin.longitude}" +
            "&destination=${destination.latitude},${destination.longitude}"
        val json = get(url)
        val routes = json.optJSONArray("routes")
        if (routes == null || routes.length() == 0) {
            throw NeshanApiException("No route found between these points")
        }
        val route = routes.getJSONObject(0)
        val overview = route.optJSONObject("overview_polyline")?.optString("points") ?: ""
        val leg = route.optJSONArray("legs")?.optJSONObject(0)
            ?: throw NeshanApiException("Malformed route response")

        val steps = ArrayList<NavRouteStep>()
        val stepPoints = ArrayList<LatLng>()
        val stepsJson = leg.optJSONArray("steps")
        if (stepsJson != null) {
            for (i in 0 until stepsJson.length()) {
                val s = stepsJson.optJSONObject(i) ?: continue
                steps.add(
                    NavRouteStep(
                        instruction = s.optString("instruction"),
                        distanceText = s.optJSONObject("distance")?.optString("text") ?: "",
                        name = s.optString("name"),
                    )
                )
                if (overview.isBlank()) {
                    val poly = s.optString("polyline")
                    if (poly.isNotBlank()) stepPoints.addAll(decodePolyline(poly))
                }
            }
        }

        val points = if (overview.isNotBlank()) decodePolyline(overview) else stepPoints
        if (points.isEmpty()) throw NeshanApiException("Route has no geometry")

        return NavRoute(
            points = points,
            distanceText = leg.optJSONObject("distance")?.optString("text") ?: "",
            durationText = leg.optJSONObject("duration")?.optString("text") ?: "",
            steps = steps,
        )
    }

    /** Decodes a Google-encoded polyline (precision 5). */
    fun decodePolyline(encoded: String): List<LatLng> {
        val points = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return points
    }
}
