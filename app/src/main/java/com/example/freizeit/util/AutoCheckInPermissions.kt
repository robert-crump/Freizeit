package com.example.freizeit.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.freizeit.data.geofence.GeofenceNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** The runtime grant isn't enough: the app's notifications, or just the check-in channel,
     *  can be blocked in system settings on any API level. A channel not created yet counts as
     *  fine — it's created at app start with default importance. */
    fun hasNotifications(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        val channelBlocked = manager.getNotificationChannelCompat(GeofenceNotifications.CHANNEL_ID)
            ?.importance == NotificationManager.IMPORTANCE_NONE
        return granted && manager.areNotificationsEnabled() && !channelBlocked
    }

    /** "Unrestricted" battery usage — optimized apps get geofence transitions late or never on
     *  many devices. */
    fun hasBatteryExemption(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** What check-in notifications still lack on this device, in asking order. */
    fun missing(context: Context): List<CheckInPermission> = missingCheckInPermissions(
        hasForegroundLocation = hasForegroundLocation(context),
        hasBackgroundLocation = hasBackgroundLocation(context),
        hasNotifications = hasNotifications(context),
        hasBatteryExemption = hasBatteryExemption(context)
    )

    /** The system dialog asking to exempt the app from battery optimization. */
    @SuppressLint("BatteryLife") // Check-ins are the app's core background feature; see README.
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))

    /**
     * Where [permission] can be fixed once Android no longer shows its dialog (denied twice, or
     * changed in system settings): the notification page for notifications, the exemption dialog
     * for battery, the app's details page for location.
     */
    fun fixIntent(context: Context, permission: CheckInPermission): Intent = when (permission) {
        CheckInPermission.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        CheckInPermission.BATTERY_UNRESTRICTED -> batteryExemptionIntent(context)
        CheckInPermission.FOREGROUND_LOCATION, CheckInPermission.BACKGROUND_LOCATION -> appDetailsIntent(context)
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}

/** A grant check-in notifications need. Declared in the order they must be asked: background
 *  location can only be requested once foreground location is granted; battery comes last. */
enum class CheckInPermission { FOREGROUND_LOCATION, NOTIFICATIONS, BACKGROUND_LOCATION, BATTERY_UNRESTRICTED }

/** The [CheckInPermission]s not yet granted, in asking order; empty when notifications can work. */
fun missingCheckInPermissions(
    hasForegroundLocation: Boolean,
    hasBackgroundLocation: Boolean,
    hasNotifications: Boolean,
    hasBatteryExemption: Boolean
): List<CheckInPermission> = listOfNotNull(
    CheckInPermission.FOREGROUND_LOCATION.takeUnless { hasForegroundLocation },
    CheckInPermission.NOTIFICATIONS.takeUnless { hasNotifications },
    CheckInPermission.BACKGROUND_LOCATION.takeUnless { hasBackgroundLocation },
    CheckInPermission.BATTERY_UNRESTRICTED.takeUnless { hasBatteryExemption }
)

/** A switch is chosen but can't work: the condition behind both Settings' "paused" row and
 *  Home's paused card (#79), so the two never disagree. */
fun checkInsPaused(favorites: Boolean, wantToGo: Boolean, missing: List<CheckInPermission>): Boolean =
    (favorites || wantToGo) && missing.isNotEmpty()

/**
 * App-wide view of [AutoCheckInPermissions.missing]. Grants change outside the app (system
 * settings, battery page) and granting doesn't restart the process, so [refresh] runs on every
 * activity resume; geofence registration and the Settings switches both follow [missing].
 */
class CheckInReadiness(private val context: Context) {
    private val _missing = MutableStateFlow(AutoCheckInPermissions.missing(context))
    val missing: StateFlow<List<CheckInPermission>> = _missing.asStateFlow()

    fun refresh() {
        _missing.value = AutoCheckInPermissions.missing(context)
    }
}
