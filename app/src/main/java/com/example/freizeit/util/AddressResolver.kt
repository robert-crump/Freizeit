package com.example.freizeit.util

import android.content.Context
import android.location.Geocoder
import java.io.IOException
import java.util.Locale

/** Resolves a free-text address to coordinates; null means "no match", never throws. */
fun interface AddressResolver {
    suspend fun resolve(address: String): LatLon?
}

/** Wraps the platform [Geocoder] — no API key, works offline-ish via the device's provider. */
class AndroidAddressResolver(private val context: Context) : AddressResolver {
    override suspend fun resolve(address: String): LatLon? = try {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault())
            .getFromLocationName(address, 1)
            ?.firstOrNull()
            ?.let { LatLon(it.latitude, it.longitude) }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
