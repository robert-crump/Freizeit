package com.example.freizeit.domain.suggestion

import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.playgroundFar
import com.example.freizeit.domain.suggestion.SuggestionFixture.playgroundNear
import com.example.freizeit.util.GeoDistance
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The distance pre-filter behind issue #21: favorites farther than the configured radius never
 * reach [SuggestionEngine.rankAll], and are checked separately from evaluate()'s hard filters so
 * a caller can tell "nothing is within range" apart from "in range but closed/rainy".
 */
class SuggestionRadiusTest {

    // playgroundNear is 0.5 km out, playgroundFar is 12 km out (see SuggestionFixture).
    private val pois = listOf(playgroundNear, playgroundFar)

    @Test
    fun `keeps only favorites within the radius`() {
        val result = SuggestionEngine.withinRadius(pois, HOME, radiusMeters = 5_000.0)
        assertEquals(listOf(playgroundNear), result)
    }

    @Test
    fun `a favorite exactly at the radius boundary is kept`() {
        val exact = GeoDistance.metersBetween(HOME.lat, HOME.lon, playgroundNear.lat, playgroundNear.lon)
        assertEquals(listOf(playgroundNear), SuggestionEngine.withinRadius(listOf(playgroundNear), HOME, exact))
    }

    @Test
    fun `no favorites within radius returns an empty list`() {
        val result = SuggestionEngine.withinRadius(listOf(playgroundFar), HOME, radiusMeters = 5_000.0)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `null location skips the filter entirely - never penalize a guess`() {
        val result = SuggestionEngine.withinRadius(pois, location = null, radiusMeters = 5_000.0)
        assertEquals(pois, result)
    }
}
