package com.example.freizeit.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.ui.common.LocalTravelLimits
import com.example.freizeit.ui.theme.FreizeitTheme
import com.example.freizeit.ui.theme.LocalDarkTheme
import com.example.freizeit.util.TravelLimits

class MainActivity : ComponentActivity() {

    /** A [FreizeitDestination] route to navigate to on launch — the widget's empty-state hint
     *  rows (#53), which have no specific place to deep-link to, use this to land on Explore
     *  (Map) or Settings rather than the default Home tab.
     *
     *  Like the other launch values below: read fresh in [onCreate]/[onNewIntent], read live
     *  from [setContent]'s composition — a `mutableStateOf` (not a plain var) so a relaunch
     *  delivered via [onNewIntent] while this Activity is already on screen recomposes
     *  [FreizeitApp] with the new value instead of it only ever taking effect on a fresh cold
     *  start. */
    private var targetDestination by mutableStateOf<String?>(null)

    /** A POI id to open on the Map (#63): tab switch, filters cleared, camera on the place, its
     *  detail sheet open. Widget suggestion taps use this one (#66). */
    private var openOnMapPoiId by mutableStateOf<String?>(null)

    /** Bumped on every [onNewIntent], so tapping the same widget row twice (same extra value)
     *  still counts as a fresh request in [FreizeitApp] rather than being a no-op (#66). */
    private var relaunchCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        targetDestination = intent.getStringExtra(EXTRA_TARGET_DESTINATION)
        openOnMapPoiId = intent.getStringExtra(EXTRA_OPEN_ON_MAP_POI_ID)
        val settingsRepository = (application as FreizeitApplication).container.settingsRepository
        setContent {
            // null until DataStore's first read, so a stored Dark never flashes Light first.
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val travelLimits by settingsRepository.travelLimits.collectAsStateWithLifecycle(initialValue = TravelLimits())
            themeMode?.let { mode ->
                FreizeitTheme(themeMode = mode) {
                    SystemBarsFollowTheme()
                    CompositionLocalProvider(LocalTravelLimits provides travelLimits) {
                        FreizeitApp(
                            targetDestination = targetDestination,
                            openOnMapPoiId = openOnMapPoiId,
                            relaunchCount = relaunchCount
                        )
                    }
                }
            }
        }
    }

    /** enableEdgeToEdge's default reads the system's dark setting; re-run it with the app's, so
     *  the status bar icons stay legible when Settings forces Light or Dark. */
    @Composable
    private fun SystemBarsFollowTheme() {
        val darkTheme = LocalDarkTheme.current
        LaunchedEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { darkTheme }
            )
        }
    }

    /** A relaunch while already running — e.g. a widget row tap (#52/#66), which sets
     *  FLAG_ACTIVITY_SINGLE_TOP — is delivered here instead of a fresh onCreate; pick up its
     *  extra the same way, and keep it as the Activity's current intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetDestination = intent.getStringExtra(EXTRA_TARGET_DESTINATION)
        openOnMapPoiId = intent.getStringExtra(EXTRA_OPEN_ON_MAP_POI_ID)
        relaunchCount++
    }

    companion object {
        // enableEdgeToEdge's own default navigation bar scrims (activity 1.8).
        private val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

        /** Intent extra carrying a [FreizeitDestination.route] to navigate to on launch (#53). */
        const val EXTRA_TARGET_DESTINATION = "target_destination"

        /** Intent extra carrying a POI id (OSM or custom) to open on the Map (#63). Verify with:
         *  `adb shell am start -n com.example.freizeit/.ui.MainActivity --es open_on_map_poi_id <id>`
         */
        const val EXTRA_OPEN_ON_MAP_POI_ID = "open_on_map_poi_id"
    }
}
