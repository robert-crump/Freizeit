package com.example.freizeit.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.freizeit.ui.theme.FreizeitTheme

class MainActivity : ComponentActivity() {

    /** Read fresh in [onCreate]/[onNewIntent], read live from [setContent]'s composition — a
     *  `mutableStateOf` (not a plain var) so a relaunch delivered via [onNewIntent] while this
     *  Activity is already on screen recomposes [FreizeitApp] with the new value instead of it
     *  only ever taking effect on a fresh cold start. See #50. */
    private var targetPoiId by mutableStateOf<String?>(null)

    /** A [FreizeitDestination] route to navigate to on launch — the widget's empty-state hint
     *  rows (#53), which have no specific place to deep-link to, use this instead of
     *  [targetPoiId] to land on Explore (Map) or Settings rather than the default Home tab. */
    private var targetDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        targetPoiId = intent.getStringExtra(EXTRA_TARGET_POI_ID)
        targetDestination = intent.getStringExtra(EXTRA_TARGET_DESTINATION)
        setContent {
            FreizeitTheme {
                FreizeitApp(targetPoiId = targetPoiId, targetDestination = targetDestination)
            }
        }
    }

    /** A relaunch while already running — e.g. a widget row tap (#52), which sets
     *  FLAG_ACTIVITY_SINGLE_TOP — is delivered here instead of a fresh onCreate; pick up its
     *  extra the same way, and keep it as the Activity's current intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPoiId = intent.getStringExtra(EXTRA_TARGET_POI_ID)
        targetDestination = intent.getStringExtra(EXTRA_TARGET_DESTINATION)
    }

    companion object {
        /** Intent extra carrying a POI id (OSM or custom) to auto-open in Home's detail sheet on
         *  launch. Verify with e.g.:
         *  `adb shell am start -n com.example.freizeit/.ui.MainActivity --es target_poi_id <id>`
         */
        const val EXTRA_TARGET_POI_ID = "target_poi_id"

        /** Intent extra carrying a [FreizeitDestination.route] to navigate to on launch (#53). */
        const val EXTRA_TARGET_DESTINATION = "target_destination"
    }
}
