package com.example.freizeit.ui.common

import android.content.ActivityNotFoundException
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.freizeit.R
import com.example.freizeit.util.AutoCheckInPermissions
import com.example.freizeit.util.CheckInPermission

/** "Check-in notifications are paused. Missing: …" — shared by Settings' paused row and Home's
 *  paused card (#79). */
@Composable
fun checkInPausedText(missing: List<CheckInPermission>): String {
    val names = missing.map {
        stringResource(
            when (it) {
                CheckInPermission.FOREGROUND_LOCATION -> R.string.settings_notifications_missing_location
                CheckInPermission.NOTIFICATIONS -> R.string.settings_notifications_missing_notifications
                CheckInPermission.BACKGROUND_LOCATION -> R.string.settings_notifications_missing_background_location
                CheckInPermission.BATTERY_UNRESTRICTED -> R.string.settings_notifications_missing_battery
            }
        )
    }
    return stringResource(R.string.settings_notifications_missing, names.joinToString(", "))
}

/** Opens the page that fixes [first] — a settings page, not the permission dialog again: Android
 *  stops showing those after two refusals, while the page always works. */
@Composable
fun CheckInFixButton(first: CheckInPermission) {
    val context = LocalContext.current
    TextButton(
        onClick = {
            try {
                context.startActivity(AutoCheckInPermissions.fixIntent(context, first))
            } catch (e: ActivityNotFoundException) {
                context.startActivity(AutoCheckInPermissions.appDetailsIntent(context))
            }
        }
    ) {
        Text(
            stringResource(
                when (first) {
                    CheckInPermission.NOTIFICATIONS -> R.string.settings_notifications_fix_notifications
                    CheckInPermission.BATTERY_UNRESTRICTED -> R.string.settings_notifications_fix_battery
                    else -> R.string.settings_notifications_open_system_settings
                }
            )
        )
    }
}
