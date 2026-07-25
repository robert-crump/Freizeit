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
}
