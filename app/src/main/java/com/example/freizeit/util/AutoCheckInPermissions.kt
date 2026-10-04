package com.example.freizeit.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Background location and notification permissions only exist as runtime concepts from
 * API 29 / 33 respectively — below those levels the app doesn't declare/request them, so
 * they read as already-granted rather than as a permanently-denied status row in Settings.
 */
object AutoCheckInPermissions {

    fun hasForegroundLocation(context: Context): Boolean = LocationHelper.hasPermission(context)

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** What check-in notifications still lack on this device, in asking order. */
    fun missing(context: Context): List<CheckInPermission> = missingCheckInPermissions(
        hasForegroundLocation = hasForegroundLocation(context),
        hasBackgroundLocation = hasBackgroundLocation(context),
        hasNotifications = hasNotifications(context)
    )
}

/** A runtime permission check-in notifications need. Declared in the order they must be asked:
 *  background location can only be requested once foreground location is granted. */
enum class CheckInPermission { FOREGROUND_LOCATION, NOTIFICATIONS, BACKGROUND_LOCATION }

/** The [CheckInPermission]s not yet granted, in asking order; empty when notifications can work. */
fun missingCheckInPermissions(
    hasForegroundLocation: Boolean,
    hasBackgroundLocation: Boolean,
    hasNotifications: Boolean
): List<CheckInPermission> = listOfNotNull(
    CheckInPermission.FOREGROUND_LOCATION.takeUnless { hasForegroundLocation },
    CheckInPermission.NOTIFICATIONS.takeUnless { hasNotifications },
    CheckInPermission.BACKGROUND_LOCATION.takeUnless { hasBackgroundLocation }
)
