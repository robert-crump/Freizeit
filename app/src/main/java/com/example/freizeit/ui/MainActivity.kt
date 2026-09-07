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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        targetPoiId = intent.getStringExtra(EXTRA_TARGET_POI_ID)
        setContent {
            FreizeitTheme {
                FreizeitApp(targetPoiId = targetPoiId)
            }
        }
    }

    /** A relaunch while already running (e.g. a future widget/notification tap using
     *  FLAG_ACTIVITY_SINGLE_TOP) is delivered here instead of a fresh onCreate — pick up its
     *  extra the same way, and keep it as the Activity's current intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPoiId = intent.getStringExtra(EXTRA_TARGET_POI_ID)
    }

    companion object {
        /** Intent extra carrying a POI id (OSM or custom) to auto-open in Home's detail sheet on
         *  launch. Verify with e.g.:
         *  `adb shell am start -n com.example.freizeit/.ui.MainActivity --es target_poi_id <id>`
         */
        const val EXTRA_TARGET_POI_ID = "target_poi_id"
    }
}
