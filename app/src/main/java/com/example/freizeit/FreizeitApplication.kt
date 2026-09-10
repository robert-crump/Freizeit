package com.example.freizeit

import android.app.Application
import com.example.freizeit.data.geofence.GeofenceNotifications
import com.example.freizeit.data.repository.observeAllFavorites
import com.example.freizeit.di.AppContainer
import com.example.freizeit.ui.widget.SuggestionWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/** How long to wait after the last verdict/check-in/radius change before pushing a widget
 *  refresh (issue #56) — long enough to collapse a burst of quick successive actions (e.g.
 *  favoriting two places back to back) into one recompute, short enough that "immediately"
 *  still feels true from the home screen. */
private const val WIDGET_REFRESH_DEBOUNCE_MS = 1_500L

class FreizeitApplication : Application() {

    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)
        GeofenceNotifications.ensureChannel(this)
        // Open the database off the main thread so schema creation
        // happens at startup rather than on first query.
        applicationScope.launch(Dispatchers.IO) {
            container.database.openHelper.writableDatabase
        }
        // Re-registers Play Services geofences (issue #28) whenever the auto check-in toggle or
        // the favorite list changes, including at process start (geofences don't survive it).
        // Above 100 favorites this replays the last closest-100 selection rather than re-ranking
        // (issue #29) — re-ranking only happens on significant location change, armed/disarmed
        // here alongside the toggle via geofenceLocationMonitor.
        applicationScope.launch(Dispatchers.IO) {
            combine(
                container.settingsRepository.autoCheckInEnabled,
                observeAllFavorites(container.database.poiDao(), container.database.customPoiDao())
            ) { enabled, favorites -> enabled to favorites }
                .distinctUntilChanged()
                .collect { (enabled, favorites) ->
                    container.geofenceSyncManager.sync(enabled, favorites)
                    if (enabled) {
                        container.geofenceLocationMonitor.start()
                    } else {
                        container.geofenceLocationMonitor.stop()
                    }
                }
        }
        // Event-driven widget refresh (issue #55's scheduled job is the other trigger; this is
        // the immediate one): reacts to exactly the three changes the issue names — a verdict set
        // or cleared, a check-in recorded (Home, the Check-in tab, or an auto check-in via
        // GeofenceBroadcastReceiver all write through the same visitDao), and the suggestion
        // radius changing — without threading a widget-refresh call through every ViewModel that
        // can cause one, mirroring the geofence-sync block above (react to the data, not the call
        // site). SuggestionWidgetUpdater.refreshAndPush is already a no-op with no widget placed,
        // so this costs nothing when the widget isn't in use.
        applicationScope.launch(Dispatchers.IO) {
            combine(
                container.database.verdictDao().observeAll(),
                container.database.visitDao().observeAll(),
                container.settingsRepository.suggestionRadiusKm
            ) { _, _, _ -> Unit }
                // combine() fires once immediately with each flow's current value — drop that
                // startup emission so this only reacts to an actual later change.
                .drop(1)
                .debounce(WIDGET_REFRESH_DEBOUNCE_MS)
                .collect { SuggestionWidgetUpdater.refreshAndPush(this@FreizeitApplication) }
        }
    }
}
