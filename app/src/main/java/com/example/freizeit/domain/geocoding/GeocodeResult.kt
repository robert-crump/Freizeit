package com.example.freizeit.domain.geocoding

/**
 * One Nominatim search hit (issue #46's address search), already reduced to what the add-POI
 * flow needs: a location to drop the pin at, plus the structured address fields
 * [com.example.freizeit.ui.map.AddPoiForm] autofills from — never the full Nominatim response
 * shape, so nothing outside [com.example.freizeit.data.geocoding.NominatimClient] needs to know
 * about it.
 */
data class GeocodeResult(
    val lat: Double,
    val lon: Double,
    val displayName: String,
    val street: String? = null,
    val housenumber: String? = null,
    val postcode: String? = null,
    val city: String? = null
)
