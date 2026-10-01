package com.example.freizeit.readme

import android.Manifest
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.checkIn
import com.example.freizeit.data.dao.setVerdict
import com.example.freizeit.ui.MainActivity
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LocationHelper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Screenshots for the README (#70), taken on an emulator from real OSM places around Aachen Markt
 * (`readme-pois.json`, cut by tools/poi_extraction/make_readme_fixture.py). Run through
 * `./gradlew readmeScreenshots`, which also clears the app's data, sets the clock to Saturday
 * 11:00, puts the emulator's GPS on Aachen Markt, sets up a clean status bar and copies the PNGs
 * to `docs/screenshots/`. Replaces the app's places, verdicts and visits, so [EmulatorOnlyRule]
 * skips it without the argument and refuses real devices.
 */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmulatorOnlyRule()).around(compose)

    private lateinit var context: Context
    private lateinit var screenshots: ReadmeScreenshotCapture

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        screenshots = ReadmeScreenshotCapture.cleared(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(UiModeManager::class.java).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
        }
        assertEquals(
            "The device clock isn't on a Saturday; run through ./gradlew readmeScreenshots",
            DayOfWeek.SATURDAY, LocalDate.now().dayOfWeek
        )
        // Granted up front so Home never shows the permission dialog.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        // Before Home's ViewModel first loads it, and fresh, so Home never fetches real weather.
        writeWeatherCache()
        awaitLocationAtMarkt()

        val fixtureText = instrumentation.context.assets.open(FIXTURE).use { it.readBytes().decodeToString() }
        val fixtureFile = File(context.cacheDir, FIXTURE).apply { writeText(fixtureText) }
        val demo = JsonParser.parseString(fixtureText).asJsonObject.getAsJsonArray("demo")

        val container = (context.applicationContext as FreizeitApplication).container
        runBlocking {
            // The same import Settings runs on a picked file.
            container.poiRepository.importFrom(Uri.fromFile(fixtureFile))
            val poiDao = container.database.poiDao()
            val today = LocalDate.now()
            demo.map { it.asJsonObject }.forEach { place ->
                val id = place["id"].asString
                val poi = checkNotNull(poiDao.getById(id)) { "Demo place $id wasn't imported" }
                container.database.verdictDao().setVerdict(poi, place["verdict"].asString)
                place.getAsJsonArray("visits_days_ago").forEach { daysAgo ->
                    val visitedAt = today.minusDays(daysAgo.asLong).atTime(VISIT_TIME)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    container.database.visitDao().checkIn(poi, visitedAt = visitedAt)
                }
            }
        }
    }

    @Test
    fun captureReadmeScreenshots() {
        captureHome()
    }

    /** Home with the ice-cream place, the day's top suggestion, in the middle of the pager. */
    private fun captureHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(HERO)
            waitForText("ice-cream weather")
            waitForText("Last visit")
            MapSettle.await(scenario, MAP_TIMEOUT_MS)
            val hero = compose.onAllNodes(hasText(HERO)).fetchSemanticsNodes()
                .map { it.boundsInWindow.center.x }
            val screenCenter = context.resources.displayMetrics.widthPixels / 2f
            assertTrue(
                "$HERO isn't the centered (top) suggestion",
                hero.any { abs(it - screenCenter) < screenCenter / 2 }
            )
            screenshots.capture(compose, "home")
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Makes the gradle task's `geo fix` Home's last known location. The emulator only feeds it to
     * the GPS provider while something listens, and a backgrounded app can't read the last known
     * location, so this listens with the app in the foreground until the fix is at Aachen Markt.
     */
    @SuppressLint("MissingPermission")
    private fun awaitLocationAtMarkt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { }
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
            }
            try {
                val deadline = SystemClock.uptimeMillis() + LOCATION_TIMEOUT_MS
                while (true) {
                    val location = LocationHelper.lastKnownLocation(context)
                    if (location != null &&
                        GeoDistance.metersBetween(location.lat, location.lon, MARKT_LAT, MARKT_LON) < LOCATION_TOLERANCE_M
                    ) {
                        return
                    }
                    if (SystemClock.uptimeMillis() > deadline) {
                        throw AssertionError("The emulator's location isn't Aachen Markt (last known: $location)")
                    }
                    Thread.sleep(250)
                }
            } finally {
                instrumentation.runOnMainSync { manager.removeUpdates(listener) }
            }
        }
    }

    /** WeatherRepository's cache file: sunny and 22° now, dry for the next day. */
    private fun writeWeatherCache() {
        val now = LocalDateTime.now()
        val hours = JsonArray()
        val firstHour = now.truncatedTo(ChronoUnit.HOURS)
        repeat(FORECAST_HOURS) { i ->
            hours.add(JsonObject().apply {
                addProperty("time", firstHour.plusHours(i.toLong()).toString())
                addProperty("tempC", TEMPERATURE_C)
                addProperty("precipitationProbability", 0)
                addProperty("weatherCode", CLEAR_SKY)
            })
        }
        val cache = JsonObject().apply {
            addProperty("fetchedAtMillis", System.currentTimeMillis())
            addProperty("currentTempC", TEMPERATURE_C)
            addProperty("currentWeatherCode", CLEAR_SKY)
            addProperty("isDay", true)
            add("hours", hours)
        }
        File(context.filesDir, "weather_cache.json").writeText(cache.toString())
    }

    private companion object {
        const val FIXTURE = "readme-pois.json"
        const val HERO = "Eiscafé Tasin"
        const val MARKT_LAT = 50.7753
        const val MARKT_LON = 6.0839
        const val LOCATION_TOLERANCE_M = 100.0
        const val TEMPERATURE_C = 22.0
        const val CLEAR_SKY = 0
        const val FORECAST_HOURS = 24
        val VISIT_TIME: LocalTime = LocalTime.of(15, 0)
        const val LOCATION_TIMEOUT_MS = 30_000L
        const val UI_TIMEOUT_MS = 30_000L
        const val MAP_TIMEOUT_MS = 60_000L
    }
}
