package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.allPois
import com.example.freizeit.domain.suggestion.SuggestionFixture.cafe
import com.example.freizeit.domain.suggestion.SuggestionFixture.coldClear
import com.example.freizeit.domain.suggestion.SuggestionFixture.favoriteAll
import com.example.freizeit.domain.suggestion.SuggestionFixture.iceCream
import com.example.freizeit.domain.suggestion.SuggestionFixture.kiosk
import com.example.freizeit.domain.suggestion.SuggestionFixture.park
import com.example.freizeit.domain.suggestion.SuggestionFixture.playgroundFar
import com.example.freizeit.domain.suggestion.SuggestionFixture.playgroundNear
import com.example.freizeit.domain.suggestion.SuggestionFixture.rainComing
import com.example.freizeit.domain.suggestion.SuggestionFixture.rainingNow
import com.example.freizeit.domain.suggestion.SuggestionFixture.restaurant
import com.example.freizeit.domain.suggestion.SuggestionFixture.saturdayAt
import com.example.freizeit.domain.suggestion.SuggestionFixture.sunny
import com.example.freizeit.domain.suggestion.SuggestionFixture.tuesdayAt
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.util.LatLon
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ~20 canned situations asserting the favorites-only ranking (issue #17
 * redesign) produces sensible cards. Each test is one situation: a clock,
 * a sky, a set of favorites — and what a reasonable concierge would and
 * would not put on the table.
 */
class SuggestionScenariosTest {

    private fun ctx(
        now: LocalDateTime,
        weather: WeatherSnapshot?,
        location: LatLon? = HOME,
        verdicts: Map<String, Verdict> = favoriteAll()
    ) = SuggestionContext(now, location, weather, verdicts = verdicts)

    private fun List<Suggestion>.ids() = map { it.poi.id }
    private fun List<Suggestion>.reasonsOf(poi: Poi) =
        first { it.poi.id == poi.id }.reasons.joinToString(" · ")
    private fun List<Suggestion>.statusOf(poi: Poi) =
        first { it.poi.id == poi.id }.openStatus
    private fun List<Suggestion>.warningsOf(poi: Poi) =
        first { it.poi.id == poi.id }.warnings

    @Test
    fun `sunny Saturday morning - an outdoor favorite is in the deck`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        assertTrue(ranked.any { it.poi.category == "playground" || it.poi.category == "park" })
    }

    @Test
    fun `only favorited places are candidates - a non-favorite never appears`() {
        // 15:00: every other fixture place is open, so the count check isolates the favorite filter.
        val verdicts = favoriteAll(allPois - cafe)
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(15), sunny(saturdayAt(15)), verdicts = verdicts))
        assertFalse(cafe.id in ranked.ids())
        assertTrue(ranked.size == allPois.size - 1)
    }

    @Test
    fun `no favorites at all - the deck is empty`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10)), verdicts = emptyMap()))
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `rainy Tuesday afternoon - outdoor favorites stay in the deck with a rain warning`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(tuesdayAt(16), rainingNow(tuesdayAt(16))))
        assertTrue(playgroundNear.id in ranked.ids())
        assertTrue(park.id in ranked.ids())
        assertTrue(ranked.warningsOf(playgroundNear).any { "rain probability" in it })
    }

    @Test
    fun `rainy Tuesday afternoon - indoor favorites suggested with a rainy-day reason`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(tuesdayAt(16), rainingNow(tuesdayAt(16))))
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.any { "good for a rainy day" in it.reasons })
    }

    @Test
    fun `only the current hour is checked - rain due next hour doesn't warn yet`() {
        // Dry this hour (14:00), rain from 15:00 on - the old 3h lookahead would have filtered
        // this, but only the current hour's probability drives the warning now.
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(tuesdayAt(14), rainComing(tuesdayAt(14), inHours = 1))
        )
        assertTrue(playgroundNear.id in ranked.ids())
        assertTrue(ranked.warningsOf(playgroundNear).isEmpty())
    }

    @Test
    fun `current-hour rounding - just past the hour still reads the hour that just started`() {
        val hour = tuesdayAt(16)
        val weather = rainComing(hour, inHours = 1) // dry at 16:00, rainy from 17:00
        val ranked = SuggestionEngine.rankAll(allPois, ctx(hour.plusMinutes(20), weather))
        assertTrue(ranked.warningsOf(playgroundNear).isEmpty())
    }

    @Test
    fun `current-hour rounding - past the half hour rounds up into the next, rainier hour`() {
        val hour = tuesdayAt(16)
        val weather = rainComing(hour, inHours = 1) // dry at 16:00, rainy from 17:00
        val ranked = SuggestionEngine.rankAll(allPois, ctx(hour.plusMinutes(40), weather))
        assertTrue(ranked.warningsOf(playgroundNear).any { "rain probability" in it })
    }

    @Test
    fun `rain warning threshold - 60 percent does not warn, 61 percent does`() {
        val atThreshold = SuggestionEngine.rankAll(
            allPois, ctx(tuesdayAt(14), SuggestionFixture.rainChance(tuesdayAt(14), 60))
        )
        assertTrue(atThreshold.warningsOf(playgroundNear).isEmpty())

        val overThreshold = SuggestionEngine.rankAll(
            allPois, ctx(tuesdayAt(14), SuggestionFixture.rainChance(tuesdayAt(14), 61))
        )
        assertTrue(overThreshold.warningsOf(playgroundNear).contains("Warning: 61% rain probability"))
    }

    @Test
    fun `warm sunny afternoon - ice cream gets its weather bonus`() {
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(saturdayAt(15), sunny(saturdayAt(15), tempC = 28.0))
        )
        assertTrue("ice-cream weather" in ranked.reasonsOf(iceCream))
    }

    @Test
    fun `cold clear day - outdoor still allowed, no ice cream bonus at 3 degrees`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(13), coldClear(saturdayAt(13))))
        assertTrue(playgroundNear.id in ranked.ids())
        assertFalse("ice-cream weather" in ranked.reasonsOf(iceCream))
    }

    @Test
    fun `evening - closed cafe stays with a closed warning, open restaurant has none`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(21), sunny(saturdayAt(21))))
        assertTrue(cafe.id in ranked.ids()) // closes 18:00, no longer excluded
        assertTrue(ranked.warningsOf(cafe).contains("Warning: Currently closed"))
        assertTrue(restaurant.id in ranked.ids()) // open until 22:00
        assertTrue(ranked.warningsOf(restaurant).isEmpty())
    }

    @Test
    fun `morning - restaurant not yet open gets a closed warning instead of being hidden`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(9), sunny(saturdayAt(9))))
        assertTrue(restaurant.id in ranked.ids())
        assertTrue(ranked.warningsOf(restaurant).contains("Warning: Currently closed"))
        assertTrue(cafe.id in ranked.ids())
        assertTrue(ranked.warningsOf(cafe).isEmpty())
    }

    @Test
    fun `night owl at 3am - closed places stay with a warning, the 24-7 kiosk has none`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(3), sunny(saturdayAt(3))))
        assertTrue(kiosk.id in ranked.ids())
        assertTrue(ranked.warningsOf(kiosk).isEmpty())
        assertTrue(cafe.id in ranked.ids())
        assertTrue(ranked.warningsOf(cafe).contains("Warning: Currently closed"))
        assertTrue(iceCream.id in ranked.ids())
        assertTrue(ranked.warningsOf(iceCream).contains("Warning: Currently closed"))
    }

    @Test
    fun `unknown hours are never filtered by time or warned - issue 1 hybrid decision`() {
        // Playground and park have no tagged hours; 23:00 must not exclude or warn about them
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(23), sunny(saturdayAt(23))))
        assertTrue(playgroundNear.id in ranked.ids())
        assertTrue(park.id in ranked.ids())
        assertTrue(ranked.warningsOf(playgroundNear).isEmpty())
    }

    @Test
    fun `no reachability filter - a favorite far away still shows up`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        assertTrue(playgroundFar.id in ranked.ids()) // 62 min ride away, no longer filtered
    }

    @Test
    fun `open status reflects hours only where they are actually known`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        assertEquals(OpenStatus.OPEN, ranked.statusOf(cafe))
        assertEquals(OpenStatus.UNKNOWN, ranked.statusOf(playgroundNear))
    }

    @Test
    fun `reason line carries the bike estimate for the actual distance`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        // ~800 m x 1.3 detour / 250 m per min ≈ 4 min
        assertTrue("4 min by bike" in ranked.reasonsOf(cafe))
    }

    @Test
    fun `no location - nothing is unreachable and no bike estimates are claimed`() {
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(saturdayAt(10), sunny(saturdayAt(10)), location = null)
        )
        assertTrue(playgroundFar.id in ranked.ids())
        assertTrue(ranked.flatMap { it.reasons }.none { "by bike" in it })
    }

    @Test
    fun `no weather - outdoor is not filtered and no weather is claimed`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(tuesdayAt(16), weather = null))
        assertTrue(playgroundNear.id in ranked.ids())
        assertTrue(ranked.flatMap { it.reasons }.none { "dry" in it || "rainy" in it })
    }

    @Test
    fun `determinism - same context always produces the same deck`() {
        val context = ctx(saturdayAt(10), sunny(saturdayAt(10)))
        assertEquals(
            SuggestionEngine.rankAll(allPois, context).ids(),
            SuggestionEngine.rankAll(allPois, context).ids()
        )
    }
}
