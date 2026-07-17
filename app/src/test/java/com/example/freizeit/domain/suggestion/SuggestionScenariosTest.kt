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
    fun `rainy Tuesday afternoon - no outdoor favorite anywhere in the deck`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(tuesdayAt(16), rainingNow(tuesdayAt(16))))
        assertTrue(ranked.none { it.poi.category == "playground" || it.poi.category == "park" })
    }

    @Test
    fun `rainy Tuesday afternoon - indoor favorites suggested with a rainy-day reason`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(tuesdayAt(16), rainingNow(tuesdayAt(16))))
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.any { "good for a rainy day" in it.reasons })
    }

    @Test
    fun `rain arriving within the fixed outing window - playground filtered even though dry right now`() {
        // Rain from 16:00, fixed 3 h outing window starting 14:00
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(tuesdayAt(14), rainComing(tuesdayAt(14), inHours = 2))
        )
        assertFalse(playgroundNear.id in ranked.ids())
    }

    @Test
    fun `rain arriving after the fixed outing window - playground stays`() {
        // Rain from 18:00, outside the fixed 3 h window starting 14:00
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(tuesdayAt(14), rainComing(tuesdayAt(14), inHours = 4))
        )
        assertTrue(playgroundNear.id in ranked.ids())
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
    fun `evening - closed cafe filtered even though nearby, open restaurant stays`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(21), sunny(saturdayAt(21))))
        assertFalse(cafe.id in ranked.ids()) // closes 18:00
        assertTrue(restaurant.id in ranked.ids()) // open until 22:00
    }

    @Test
    fun `morning - restaurant that opens at 11 is not suggested at 9`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(9), sunny(saturdayAt(9))))
        assertFalse(restaurant.id in ranked.ids())
        assertTrue(cafe.id in ranked.ids())
    }

    @Test
    fun `night owl at 3am - only the 24-7 kiosk survives the hours filter`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(3), sunny(saturdayAt(3))))
        assertTrue(kiosk.id in ranked.ids())
        assertFalse(cafe.id in ranked.ids())
        assertFalse(iceCream.id in ranked.ids())
    }

    @Test
    fun `unknown hours are never filtered by time - issue 1 hybrid decision`() {
        // Playground and park have no tagged hours; 23:00 must not exclude them
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(23), sunny(saturdayAt(23))))
        assertTrue(playgroundNear.id in ranked.ids())
        assertTrue(park.id in ranked.ids())
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
