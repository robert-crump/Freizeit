package com.example.freizeit.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.freizeit.R
import com.example.freizeit.util.AutoCheckInPermissions
import com.example.freizeit.util.CheckInPermission

/** How a [CheckInPermissionFlow.request] ended. */
enum class CheckInPermissionOutcome {
    /** Every step was asked (each grant may still have been refused, except foreground location). */
    DONE,
    /** Foreground location was refused — nothing location-based can work, so the flow stopped. */
    LOCATION_DENIED,
    /** The background-location disclosure was cancelled. */
    CANCELLED
}

/** Asks for whatever check-in notifications still lack; see [rememberCheckInPermissionFlow]. */
class CheckInPermissionFlow internal constructor(
    private val start: ((CheckInPermissionOutcome) -> Unit) -> Unit
) {
    fun request(onDone: (CheckInPermissionOutcome) -> Unit) = start(onDone)
}

/**
 * The one permission sequence for check-in notifications, shared by the first launch and the
 * Settings switches: foreground location, then notifications, then — after the background-location
 * disclosure (issue #24's Play Store requirement) — background location. Steps already granted are
 * skipped; with nothing missing, [CheckInPermissionFlow.request] reports DONE straight away.
 */
@Composable
fun rememberCheckInPermissionFlow(): CheckInPermissionFlow {
    val context = LocalContext.current
    var onDone by remember { mutableStateOf<((CheckInPermissionOutcome) -> Unit)?>(null) }
    var showDisclosure by remember { mutableStateOf(false) }

    fun finish(outcome: CheckInPermissionOutcome) {
        val callback = onDone
        onDone = null
        callback?.invoke(outcome)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { finish(CheckInPermissionOutcome.DONE) }

    fun askBackgroundLocation() {
        if (CheckInPermission.BACKGROUND_LOCATION in AutoCheckInPermissions.missing(context)) {
            showDisclosure = true
        } else {
            finish(CheckInPermissionOutcome.DONE)
        }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { askBackgroundLocation() }

    fun askNotifications() {
        if (CheckInPermission.NOTIFICATIONS in AutoCheckInPermissions.missing(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            askBackgroundLocation()
        }
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.none { it }) finish(CheckInPermissionOutcome.LOCATION_DENIED) else askNotifications()
    }

    if (showDisclosure) {
        CheckInDisclosureDialog(
            onConfirm = {
                showDisclosure = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    finish(CheckInPermissionOutcome.DONE)
                }
            },
            onDismiss = {
                showDisclosure = false
                finish(CheckInPermissionOutcome.CANCELLED)
            }
        )
    }

    return remember {
        CheckInPermissionFlow { callback ->
            onDone = callback
            if (CheckInPermission.FOREGROUND_LOCATION in AutoCheckInPermissions.missing(context)) {
                foregroundLocationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            } else {
                askNotifications()
            }
        }
    }
}

@Composable
private fun CheckInDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notifications_disclosure_title)) },
        text = { Text(stringResource(R.string.settings_notifications_disclosure_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_notifications_disclosure_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
