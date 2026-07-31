package com.example.freizeit.domain.suggestion

import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.cafe
import com.example.freizeit.domain.suggestion.SuggestionFixture.favoriteAll
import com.example.freizeit.domain.suggestion.SuggestionFixture.kiosk
import com.example.freizeit.domain.suggestion.SuggestionFixture.saturdayAt
import com.example.freizeit.domain.suggestion.SuggestionFixture.sunny
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "More of the best" (issue #27): a favorite visited often in the trailing 90
 * days should outrank an equally-placed favorite with no recent visits.
 */
class SuggestionVisitFrequencyTest {

    private val now = saturdayAt(10)
    private val weather = sunny(now)
    private val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val dayMillis = 24 * 60 * 60 * 1000L

    private fun ctx(visits: Map<String, List<Long>>) =
        SuggestionContext(now, HOME, weather, verdicts = favoriteAll(listOf(cafe, kiosk)), visits = visits)

    private fun baselineScore() = SuggestionEngine.rankAll(listOf(cafe), ctx(emptyMap())).first().score

    @Test
    fun `no visits - no boost and no last-visit label`() {
        val ranked = SuggestionEngine.rankAll(listOf(cafe), ctx(emptyMap()))
        assertEquals(baselineScore(), ranked.first().score, 0.0001)
        assertNull(ranked.first().lastVisit)
    }

    @Test
    fun `several recent visits - boost applied, outranks a same-spot favorite with none, last-visit reflects the most recent`() {
        val visits = mapOf(cafe.id to listOf(nowMillis - 5 * dayMillis, nowMillis - 10 * dayMillis, nowMillis - 20 * dayMillis))
        val ranked = SuggestionEngine.rankAll(listOf(cafe, kiosk), ctx(visits))

        val cafeSuggestion = ranked.first { it.poi.id == cafe.id }
        val kioskSuggestion = ranked.first { it.poi.id == kiosk.id }
        assertEquals("5 days ago", cafeSuggestion.lastVisit)
        assertTrue(cafeSuggestion.score > kioskSuggestion.score)
        assertTrue(cafeSuggestion.score > baselineScore())
    }

    @Test
    fun `visits outside the 90-day window contribute no boost`() {
        val visits = mapOf(cafe.id to listOf(nowMillis - 91 * dayMillis, nowMillis - 200 * dayMillis))
        val ranked = SuggestionEngine.rankAll(listOf(cafe), ctx(visits))
        assertEquals(baselineScore(), ranked.first().score, 0.0001)
    }

    @Test
    fun `a visit exactly at the 90-day boundary still counts`() {
        val visits = mapOf(cafe.id to listOf(nowMillis - 90 * dayMillis))
        val ranked = SuggestionEngine.rankAll(listOf(cafe), ctx(visits))
        assertTrue(ranked.first().score > baselineScore())
    }
}
