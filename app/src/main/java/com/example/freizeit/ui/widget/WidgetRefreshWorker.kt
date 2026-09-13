package com.example.freizeit.ui.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * The scheduled half of issue #55's daily refresh (the manual button and event-driven triggers
 * are #54/#56, both via the same [SuggestionWidgetUpdater]). Fires once at whichever weekday/
 * weekend trigger [WidgetRefreshScheduler] last computed, pushes a refresh, then immediately
 * re-enqueues itself for the following occurrence — self-rescheduling one-time work rather than
 * an [androidx.work.PeriodicWorkRequest], since WorkManager's periodic requests only support a
 * fixed repeat interval, not an alternating weekday-noon/weekend-8am time-of-day.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SuggestionWidgetUpdater.refreshAndPush(applicationContext)
        WidgetRefreshScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}
