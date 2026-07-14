package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.allPois
import com.example.freizeit.domain.suggestion.SuggestionFixture.cafe
import com.example.freizeit.domain.suggestion.SuggestionFixture.saturdayAt
import com.example.freizeit.domain.suggestion.SuggestionFixture.sunny
import java.time.Duration
import kotlin.math.exp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The family-favorites rewrite's ranking rules: ❤️ boosts the score once its
 * 2-day cooldown has passed (kids' rituals are a feature — a favorite should
 * resurface soon), and a nearby favorite gets an extra edge on top of the
 * flat bonus.
 */
class SuggestionVerdictTest {

    private val now = saturdayAt(10)
    private val weather = sunny(now)

    private fun ctx(verdicts: Map<String, Verdict>) =
        SuggestionContext(now, HOME, weather, verdicts = verdicts)

    private fun verdict(placeId: String, value: String, ageMillis: Long) = Verdict(
        placeId = placeId,
        value = value,
        verdictedAt = SuggestionContext(now, HOME, weather).nowMillis - ageMillis,
        snapshotName = "snapshot",
        snapshotLat = 0.0,
        snapshotLon = 0.0,
        snapshotCategory = "cafe"
    )

    private fun poiAt(id: String, kmNorth: Double) = Poi(
        id = id,
        category = "cafe",
        lat = HOME.lat + kmNorth * 0.009,
        lon = HOME.lon,
        name = "Test $id"
    )

    @Test
    fun `a favorited place is suppressed within its 2-day cooldown`() {
        val verdicts = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_FAVORITE, Duration.ofHours(6).toMillis()))
        assertFalse(cafe.id in SuggestionEngine.rankAll(allPois, ctx(verdicts)).map { it.poi.id })
    }

    @Test
    fun `a favorited place returns and gets a boost once 2 days have passed`() {
        val verdicts = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_FAVORITE, Duration.ofDays(3).toMillis()))
        val ranked = SuggestionEngine.rankAll(allPois, ctx(verdicts))
        val cafeSuggestion = ranked.first { it.poi.id == cafe.id }
        assertTrue("❤️ favorite" in cafeSuggestion.reasons)
    }

    @Test
    fun `an unverdicted place is unaffected`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(emptyMap()))
        assertTrue(cafe.id in ranked.map { it.poi.id })
        assertTrue(ranked.first { it.poi.id == cafe.id }.reasons.none { "❤️" in it })
    }

    @Test
    fun `a nearby favorite gets an extra proximity boost beyond the flat favorite bonus`() {
        val near = poiAt("node/near", 0.5) // ~3 min by bike
        val far = poiAt("node/far", 12.0) // ~62 min by bike
        val verdicts = mapOf(
            near.id to verdict(near.id, Verdict.VALUE_FAVORITE, Duration.ofDays(3).toMillis()),
            far.id to verdict(far.id, Verdict.VALUE_FAVORITE, Duration.ofDays(3).toMillis())
        )
        val ranked = SuggestionEngine.rankAll(listOf(near, far), ctx(verdicts))
        val nearScore = ranked.first { it.poi.id == near.id }.score
        val farScore = ranked.first { it.poi.id == far.id }.score

        // Both get the flat +25 favorite bonus and the ordinary distance-decay term;
        // the gap between them must exceed what distance decay alone explains, proving
        // the extra nearby-favorite term contributed something.
        val distanceOnlyGap = 40.0 * (exp(-3.0 / 25.0) - exp(-62.0 / 25.0))
        assertTrue(nearScore - farScore > distanceOnlyGap)
    }
}
