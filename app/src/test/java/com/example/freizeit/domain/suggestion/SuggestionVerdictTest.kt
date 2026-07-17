package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.allPois
import com.example.freizeit.domain.suggestion.SuggestionFixture.cafe
import com.example.freizeit.domain.suggestion.SuggestionFixture.favoriteVerdict
import com.example.freizeit.domain.suggestion.SuggestionFixture.saturdayAt
import com.example.freizeit.domain.suggestion.SuggestionFixture.sunny
import kotlin.math.exp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The favorites-only rewrite's ranking rules (issue #17): the candidate pool
 * is exactly the favorited places, with no cooldown — a favorite should be
 * able to resurface the moment it's favorited — and a nearby favorite gets
 * an extra edge on top of the ordinary distance-decay term.
 */
class SuggestionVerdictTest {

    private val now = saturdayAt(10)
    private val weather = sunny(now)

    private fun ctx(verdicts: Map<String, Verdict>) =
        SuggestionContext(now, HOME, weather, verdicts = verdicts)

    private fun poiAt(id: String, kmNorth: Double) = Poi(
        id = id,
        category = "cafe",
        lat = HOME.lat + kmNorth * 0.009,
        lon = HOME.lon,
        name = "Test $id"
    )

    @Test
    fun `an unfavorited place never appears in the deck`() {
        assertFalse(cafe.id in SuggestionEngine.rankAll(allPois, ctx(emptyMap())).map { it.poi.id })
    }

    @Test
    fun `a favorite shows up immediately - no cooldown after favoriting`() {
        val verdicts = mapOf(cafe.id to favoriteVerdict(cafe).copy(verdictedAt = ctx(emptyMap()).nowMillis))
        assertTrue(cafe.id in SuggestionEngine.rankAll(allPois, ctx(verdicts)).map { it.poi.id })
    }

    @Test
    fun `a nearby favorite gets an extra proximity boost over a far favorite`() {
        val near = poiAt("node/near", 0.5) // ~3 min by bike
        val far = poiAt("node/far", 12.0) // ~62 min by bike
        val verdicts = mapOf(
            near.id to favoriteVerdict(near),
            far.id to favoriteVerdict(far)
        )
        val ranked = SuggestionEngine.rankAll(listOf(near, far), ctx(verdicts))
        val nearScore = ranked.first { it.poi.id == near.id }.score
        val farScore = ranked.first { it.poi.id == far.id }.score

        // Both survive as favorites and get the ordinary distance-decay term; the gap between
        // them must exceed what that alone explains, proving the extra proximity term contributed.
        val distanceOnlyGap = 40.0 * (exp(-3.0 / 25.0) - exp(-62.0 / 25.0))
        assertTrue(nearScore - farScore > distanceOnlyGap)
    }
}
