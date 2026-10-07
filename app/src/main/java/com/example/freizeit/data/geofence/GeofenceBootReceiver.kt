package com.example.freizeit.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.util.GeofenceEventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers geofences right after a reboot (issue #58), an update/reinstall of this app
 * (`MY_PACKAGE_REPLACED`, issue #61), or the app leaving the stopped state after a force stop
 * (Android 15+ sends `BOOT_COMPLETED` then, see [GeofenceSyncManager.syncAfterBootCompleted]).
 * Play Services drops them all in each case, and otherwise nothing re-adds them until the app
 * process happens to start for another reason — auto check-in would stay dead until the next app
 * open. Reboots and updates count as an epoch reset, so the sync also schedules
 * [GeofenceReregisterWorker]'s delayed forced pass.
 */
class GeofenceBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bootCompleted = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> true
            Intent.ACTION_MY_PACKAGE_REPLACED -> false
            else -> return
        }
        val pendingResult = goAsync()
        val app = context.applicationContext as FreizeitApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.container
                val (enabled, places) = container.geofenceTargetsOnce()
                if (bootCompleted) {
                    container.geofenceSyncManager.syncAfterBootCompleted(enabled, places)
                } else {
                    GeofenceEventLog.append(app, "package replaced -> sync")
                    container.geofenceSyncManager.sync(enabled, places)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
