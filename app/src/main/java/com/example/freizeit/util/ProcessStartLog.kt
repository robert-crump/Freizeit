package com.example.freizeit.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.time.Instant

/**
 * Logs every process start to [GeofenceEventLog], with how the previous process ended where the
 * platform records it (Android 11+). A force stop (Studio's Stop button, Settings → Force stop)
 * silently drops every geofence and cancels pending work until the next app open; in the trail it
 * otherwise looks only like a long silence. With this it reads `last exit: USER_REQUESTED`.
 */
object ProcessStartLog {

    fun append(context: Context) {
        GeofenceEventLog.append(context, processStartLine(lastExit(context)))
    }

    private fun lastExit(context: Context): LastExit? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = try {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
        } catch (e: RuntimeException) {
            // Diagnostics must never crash app startup.
            null
        } ?: return null
        return LastExit(exitReasonName(info.reason), info.timestamp, info.description)
    }
}

data class LastExit(val reason: String, val timestampMillis: Long, val description: String?)

fun processStartLine(lastExit: LastExit?): String {
    if (lastExit == null) return "process start (last exit: unknown)"
    val description = lastExit.description?.takeIf { it.isNotBlank() }?.let { " \"$it\"" }.orEmpty()
    return "process start (last exit: ${lastExit.reason} at ${Instant.ofEpochMilli(lastExit.timestampMillis)}$description)"
}

/** [ApplicationExitInfo] reason constants by name; plain ints so it's testable off-device. */
fun exitReasonName(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
    ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
    ApplicationExitInfo.REASON_CRASH -> "CRASH"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
    ApplicationExitInfo.REASON_ANR -> "ANR"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
    ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
    ApplicationExitInfo.REASON_OTHER -> "OTHER"
    ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
    else -> "UNKNOWN($reason)"
}
