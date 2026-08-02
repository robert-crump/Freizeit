package com.example.freizeit.util

import android.content.Context
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Append-only diagnostic trail for the auto check-in geofence path (issue #43) — plain logcat
 * isn't useful here since a missed check-in is only ever noticed hours later, long after the
 * buffer has rotated and without a live ADB connection at the time. Persisted to app-private
 * storage instead; pull it later once back at a computer:
 * `adb shell run-as com.example.freizeit cat files/geofence_log.txt`
 */
object GeofenceEventLog {

    fun append(context: Context, line: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.length() > MAX_BYTES) trim(file)
            file.appendText("${Instant.now()} $line\n")
        } catch (e: IOException) {
            // Diagnostics must never crash the geofence path they're observing.
        }
    }

    private fun trim(file: File) {
        val lines = file.readLines()
        file.writeText(lines.drop(lines.size / 2).joinToString(separator = "\n", postfix = "\n"))
    }

    private const val FILE_NAME = "geofence_log.txt"
    private const val MAX_BYTES = 500_000L
}
