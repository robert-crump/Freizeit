package com.example.freizeit.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.repository.allFavoritesOnce
import com.example.freizeit.util.GeofenceEventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-registers geofences right after a reboot (issue #58) or an update/reinstall of this app
 * (`MY_PACKAGE_REPLACED`, issue #61). Play Services drops them all in both cases, and otherwise
 * nothing re-adds them until the app process happens to start for another reason — auto check-in
 * would stay dead until the next app open. Both count as an epoch reset, so the sync also
 * schedules [GeofenceReregisterWorker]'s delayed forced pass.
 */
class GeofenceBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot completed"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "package replaced"
            else -> return
        }
        val pendingResult = goAsync()
        val app = context.applicationContext as FreizeitApplication
        GeofenceEventLog.append(app, "$trigger -> sync")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.container
                container.geofenceSyncManager.sync(
                    container.settingsRepository.autoCheckInEnabled.first(),
                    allFavoritesOnce(container.database.poiDao(), container.database.customPoiDao())
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
