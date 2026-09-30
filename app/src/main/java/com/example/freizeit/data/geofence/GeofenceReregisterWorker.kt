package com.example.freizeit.data.geofence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.repository.allFavoritesOnce
import com.example.freizeit.util.GeofenceEventLog
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * One-off forced re-register that follows every epoch reset (issue #61) — see
 * [com.example.freizeit.util.isEpochReset] for the race it beats. Deliberately one-off, never
 * periodic: each forced pass resets in-progress dwell clocks, which the diff-based registration
 * otherwise exists to avoid.
 */
class GeofenceReregisterWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FreizeitApplication).container
        container.geofenceSyncManager.forceReregister(
            container.settingsRepository.autoCheckInEnabled.first(),
            allFavoritesOnce(container.database.poiDao(), container.database.customPoiDao())
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "geofence_forced_reregister"
        private const val DELAY_MINUTES = 2L

        /** [ExistingWorkPolicy.REPLACE], so back-to-back resets (reboot, then an update) still
         *  leave exactly one pending pass, timed from the latest one. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<GeofenceReregisterWorker>()
                .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            GeofenceEventLog.append(context, "delayed forced re-register scheduled in ${DELAY_MINUTES}min")
        }
    }
}
