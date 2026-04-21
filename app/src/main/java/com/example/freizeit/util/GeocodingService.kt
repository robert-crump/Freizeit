package com.example.freizeit.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

class GeocodingService {
    private val client = OkHttpClient()

    companion object {
        private const val TAG = "GeocodingService"
    }

    data class GeocodingResult(
        val latitude: Double,
        val longitude: Double,
        val displayName: String
    )

    suspend fun geocodeAddress(
        street: String,
        houseNumber: String,
        zipCode: String,
        city: String
    ): GeocodingResult? = withContext(Dispatchers.IO) {
        try {
            val address = "$street $houseNumber, $zipCode $city"
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encodedAddress&format=json&limit=1"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Freizeit-App")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val jsonArray = JSONArray(responseBody)
                    if (jsonArray.length() > 0) {
                        val firstResult = jsonArray.getJSONObject(0)
                        val lat = firstResult.getDouble("lat")
                        val lon = firstResult.getDouble("lon")
                        val displayName = firstResult.getString("display_name")
                        return@use GeocodingResult(lat, lon, displayName)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            null
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?" +
                        "format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Freizeit-App")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val jsonObject = org.json.JSONObject(responseBody)
                        val address = jsonObject.optJSONObject("address")
                        address?.optString("city")
                            ?: address?.optString("town")
                            ?: address?.optString("village")
                            ?: address?.optString("municipality")
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reverse geocoding failed", e)
                null
            }
        }
}
