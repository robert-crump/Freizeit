package com.example.freizeit.ui.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.freizeit.BuildConfig
import com.example.freizeit.util.millisUntilNextWidgetRefresh
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules [WidgetRefreshWorker] via WorkManager (issue #55) — the app's first periodic
 * background job, so there's no existing scheduling infrastructure to extend. WorkManager
 * persists enqueued work to its own store and (per the boot receiver its own library manifest
 * merges in, gated on `RECEIVE_BOOT_COMPLETED`) re-arms it after a reboot with no extra code
 * here.
 */
object WidgetRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "widget_refresh_schedule"

    /**
     * Shortened, fixed interval in debug builds — same shape as
     * [com.example.freizeit.data.geofence.GeofenceSyncManager.DEBUG_LOITERING_DELAY_MILLIS]: swap
     * in a short constant rather than a different mechanism, so the real self-rescheduling loop
     * and the recompute-and-push call are both exercised end to end without waiting for the real
     * weekday-noon/weekend-8am wall clock (issue's debug-verifiability AC).
     */
    private const val DEBUG_REFRESH_INTERVAL_MILLIS = 30_000L

    /**
     * Call once at app start. [ExistingWorkPolicy.KEEP] so this only ever arms the very first
     * occurrence (fresh install, or upgrading from a build that predates this schedule) — a
     * pending or already-rescheduled occurrence from [WidgetRefreshWorker] itself is left alone,
     * so relaunching the app doesn't keep pushing the schedule back.
     */
    fun scheduleIfNeeded(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

    /** Called by [WidgetRefreshWorker] right after it fires, to arm the next occurrence. */
    fun scheduleNext(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val delayMillis = if (BuildConfig.DEBUG) {
            DEBUG_REFRESH_INTERVAL_MILLIS
        } else {
            millisUntilNextWidgetRefresh(ZonedDateTime.now())
        }
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }
}
