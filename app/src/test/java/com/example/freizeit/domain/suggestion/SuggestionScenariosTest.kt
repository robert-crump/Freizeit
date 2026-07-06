package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.allPois
import com.example.freizeit.domain.suggestion.SuggestionFixture.cafe
import com.example.freizeit.domain.suggestion.SuggestionFixture.coldClear
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
 * ~20 canned situations asserting the ranking produces sensible cards
 * (issue #5's "suggestions must not feel dumb" fixture). Each test is one
 * situation: a clock, a sky, a budget — and what a reasonable concierge
 * would and would not put on the table.
 */
class SuggestionScenariosTest {

    private fun ctx(
        now: LocalDateTime,
        weather: WeatherSnapshot?,
        budgetMinutes: Int = 180,
        location: LatLon? = HOME,
        kidsAlong: Boolean = true
    ) = SuggestionContext(now, location, weather, budgetMinutes, kidsAlong)

    private fun List<Suggestion>.ids() = map { it.poi.id }
    private fun List<Suggestion>.categories() = map { it.poi.category }.toSet()
    private fun List<Suggestion>.reasonsOf(poi: Poi) =
        first { it.poi.id == poi.id }.reasons.joinToString(" · ")

    @Test
    fun `sunny Saturday morning - an outdoor place is on the cards`() {
        val cards = SuggestionEngine.suggest(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        assertEquals(3, cards.size)
        assertTrue(cards.categories().any { it == "playground" || it == "park" })
    }

    @Test
    fun `sunny Saturday morning - three cards span at least two categories`() {
        val cards = SuggestionEngine.suggest(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        assertTrue(cards.categories().size >= 2)
    }

    @Test
    fun `rainy Tuesday afternoon - no outdoor place anywhere in the ranking`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(tuesdayAt(16), rainingNow(tuesdayAt(16))))
        assertTrue(ranked.none { it.poi.category == "playground" || it.poi.category == "park" })
    }

    @Test
    fun `rainy Tuesday afternoon - indoor places suggested with a rainy-day reason`() {
        val cards = SuggestionEngine.suggest(allPois, ctx(tuesdayAt(16), rainingNow(tuesdayAt(16))))
        assertEquals(3, cards.size)
        assertTrue(cards.any { "good for a rainy day" in it.reasons })
    }

    @Test
    fun `rain arriving mid-outing - playground filtered even though it is dry right now`() {
        // Rain from 16:00, default 3 h budget starting 14:00
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(tuesdayAt(14), rainComing(tuesdayAt(14), inHours = 2))
        )
        assertFalse(playgroundNear.id in ranked.ids())
    }

    @Test
    fun `rain arriving after a short outing - playground stays`() {
        // Rain from 20:00; a 1 h outing at 14:00 doesn't care
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(tuesdayAt(14), rainComing(tuesdayAt(14), inHours = 6), budgetMinutes = 60)
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
    fun `one hour budget - far playground is unreachable and dropped`() {
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(saturdayAt(10), sunny(saturdayAt(10)), budgetMinutes = 60)
        )
        assertFalse(playgroundFar.id in ranked.ids()) // 62 min ride each way
        assertTrue(playgroundNear.id in ranked.ids())
    }

    @Test
    fun `all day budget - far playground comes back`() {
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(saturdayAt(10), sunny(saturdayAt(10)), budgetMinutes = 480)
        )
        assertTrue(playgroundFar.id in ranked.ids())
    }

    @Test
    fun `reason line says Open only where hours are actually known`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        assertTrue("Open" in ranked.reasonsOf(cafe))
        assertFalse("Open" in ranked.reasonsOf(playgroundNear))
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
            allPois, ctx(saturdayAt(10), sunny(saturdayAt(10)), budgetMinutes = 60, location = null)
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
    fun `reroll - next three cards are fresh, no repeats`() {
        val context = ctx(saturdayAt(15), sunny(saturdayAt(15)))
        val first = SuggestionEngine.suggest(allPois, context)
        val second = SuggestionEngine.suggest(allPois, context, excludeIds = first.ids().toSet())
        assertEquals(3, second.size)
        assertTrue(first.ids().toSet().intersect(second.ids().toSet()).isEmpty())
    }

    @Test
    fun `determinism - same context always produces the same cards`() {
        val context = ctx(saturdayAt(10), sunny(saturdayAt(10)))
        assertEquals(
            SuggestionEngine.suggest(allPois, context).ids(),
            SuggestionEngine.suggest(allPois, context).ids()
        )
    }

    @Test
    fun `diversity - three same-category leaders get one swapped out`() {
        val threePlaygroundsAndACafe = listOf(
            playgroundNear,
            playgroundNear.copy(id = "node/91", name = "Spielplatz B"),
            playgroundNear.copy(id = "node/92", name = "Spielplatz C"),
            cafe.copy(id = "node/93", lat = HOME.lat + 5 * 0.009) // far, scores lowest
        )
        val cards = SuggestionEngine.suggest(
            threePlaygroundsAndACafe, ctx(saturdayAt(10), sunny(saturdayAt(10)))
        )
        assertEquals(3, cards.size)
        assertTrue(cards.categories().size >= 2)
    }

    @Test
    fun `adults only - playgrounds drop off the cards`() {
        val cards = SuggestionEngine.suggest(
            allPois, ctx(saturdayAt(15), sunny(saturdayAt(15)), kidsAlong = false)
        )
        assertEquals(3, cards.size)
        assertTrue(cards.none { it.poi.category == "playground" })
    }

    @Test
    fun `issue 7 - reason line calls out an adults-only override`() {
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(saturdayAt(15), sunny(saturdayAt(15)), kidsAlong = false)
        )
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.all { "adults-only pick" in it.reasons })
    }

    @Test
    fun `issue 7 - default kids-along context never claims an adults-only override`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(15), sunny(saturdayAt(15))))
        assertTrue(ranked.flatMap { it.reasons }.none { "adults-only" in it })
    }

    @Test
    fun `issue 7 - one hour budget reason line says quick outing`() {
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(saturdayAt(10), sunny(saturdayAt(10)), budgetMinutes = 60)
        )
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.all { "quick outing" in it.reasons })
    }

    @Test
    fun `issue 7 - all day budget reason line says all day`() {
        val ranked = SuggestionEngine.rankAll(
            allPois, ctx(saturdayAt(10), sunny(saturdayAt(10)), budgetMinutes = 480)
        )
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.all { "you've got all day" in it.reasons })
    }

    @Test
    fun `issue 7 - default 3h budget reason line makes no time-budget claim`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(saturdayAt(10), sunny(saturdayAt(10))))
        assertTrue(
            ranked.flatMap { it.reasons }
                .none { "quick outing" in it || "you've got all day" in it }
        )
    }
}
