package com.example.freizeit.data.geocoding

import com.example.freizeit.domain.geocoding.GeocodeResult
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Minimal Nominatim (OpenStreetMap) address search client for issue #46 — mirrors
 * [com.example.freizeit.data.weather.OpenMeteoClient]'s pattern (plain HttpURLConnection + Gson,
 * one GET per lookup, null-on-any-failure, no new client library dependency).
 *
 * Nominatim's usage policy requires a real identifying User-Agent (its default rejects a bare
 * HttpURLConnection one) — [USER_AGENT] names the app and links its repo rather than a personal
 * contact address, so no user-identifying data leaves the device for this third-party request.
 */
object NominatimClient {

    private const val TIMEOUT_MS = 10_000
    private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
    private const val USER_AGENT = "Freizeit-Android/1.0 (+https://github.com/robert-crump/Freizeit)"
    private val gson = Gson()

    // Mirrors tools/poi_extraction/extract_pois.py's AACHEN_BBOX (lat_min, lon_min, lat_max,
    // lon_max) — duplicated as a literal rather than shared, since one side is Python and the
    // other Kotlin. Used only to bias results (Nominatim's viewbox+bounded=0), not restrict them.
    private const val BBOX_LAT_MIN = 50.5956
    private const val BBOX_LON_MIN = 5.7996
    private const val BBOX_LAT_MAX = 50.9550
    private const val BBOX_LON_MAX = 6.3682

    /**
     * One explicit-submit search (no live-typeahead, per Nominatim's 1 req/sec policy and the
     * issue's own spec). Returns null on any network/parse failure; an empty list is a genuine
     * zero-result search — callers tell the two apart to show the right inline message.
     */
    fun search(query: String, limit: Int = 5): List<GeocodeResult>? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        return try {
            val connection = URL(buildUrl(trimmed, limit)).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (connection.responseCode != 200) return null
                val body = InputStreamReader(connection.inputStream, Charsets.UTF_8).use { it.readText() }
                parse(body)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Visible for tests: pure mapping from Nominatim's JSON array shape to [GeocodeResult]s. */
    fun parse(json: String): List<GeocodeResult>? = try {
        val type = object : TypeToken<List<NominatimResult>>() {}.type
        val results: List<NominatimResult>? = gson.fromJson(json, type)
        results?.mapNotNull { it.toGeocodeResult() }
    } catch (e: Exception) {
        null
    }

    private fun buildUrl(query: String, limit: Int): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // viewbox is "left,top,right,bottom" (lon_min,lat_max,lon_max,lat_min); bounded=0 (the
        // default) biases toward it instead of excluding results outside it.
        val viewbox = String.format(
            Locale.US, "%s,%s,%s,%s", BBOX_LON_MIN, BBOX_LAT_MAX, BBOX_LON_MAX, BBOX_LAT_MIN
        )
        return "$BASE_URL?q=$encodedQuery&format=jsonv2&addressdetails=1&limit=$limit" +
            "&viewbox=$viewbox&bounded=0"
    }

    private class NominatimResult(
        val lat: String? = null,
        val lon: String? = null,
        @SerializedName("display_name") val displayName: String? = null,
        val address: NominatimAddress? = null
    ) {
        fun toGeocodeResult(): GeocodeResult? {
            return GeocodeResult(
                lat = lat?.toDoubleOrNull() ?: return null,
                lon = lon?.toDoubleOrNull() ?: return null,
                displayName = displayName ?: return null,
                street = address?.road,
                housenumber = address?.houseNumber,
                postcode = address?.postcode,
                city = address?.city ?: address?.town ?: address?.village
            )
        }
    }

    private class NominatimAddress(
        val road: String? = null,
        @SerializedName("house_number") val houseNumber: String? = null,
        val postcode: String? = null,
        val city: String? = null,
        val town: String? = null,
        val village: String? = null
    )
}
