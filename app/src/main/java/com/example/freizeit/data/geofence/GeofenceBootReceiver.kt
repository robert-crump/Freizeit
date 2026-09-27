package com.example.freizeit.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.repository.allFavoritesOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-registers geofences right after a reboot (issue #58). Play Services drops them all on boot,
 * and otherwise nothing re-adds them until the app process happens to start for another reason —
 * auto check-in would stay dead until the next app open.
 */
class GeofenceBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val app = context.applicationContext as FreizeitApplication
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
