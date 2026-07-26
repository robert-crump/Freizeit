package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.allPois
import com.example.freizeit.domain.suggestion.SuggestionFixture.cafe
import com.example.freizeit.domain.suggestion.SuggestionFixture.favoriteVerdict
import com.example.freizeit.domain.suggestion.SuggestionFixture.kiosk
import com.example.freizeit.domain.suggestion.SuggestionFixture.saturdayAt
import com.example.freizeit.domain.suggestion.SuggestionFixture.sunny
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merged favorites/want-to-go deck rules (#31): a "want to go" verdict is a second,
 * equally valid way onto the deck alongside "favorite" — same hard filter, same scoring,
 * no special-cased ranking treatment for either bucket.
 */
class SuggestionWantToGoTest {

    private val now = saturdayAt(10)
    private val weather = sunny(now)

    private fun ctx(verdicts: Map<String, Verdict>) =
        SuggestionContext(now, HOME, weather, verdicts = verdicts)

    private fun wantToGoVerdict(poi: Poi): Verdict = Verdict(
        placeId = poi.id,
        value = Verdict.VALUE_WANT_TO_GO,
        verdictedAt = 0L,
        snapshotName = poi.name,
        snapshotLat = poi.lat,
        snapshotLon = poi.lon,
        snapshotCategory = poi.category
    )

    @Test
    fun `a want-to-go place appears in the deck`() {
        val deck = SuggestionEngine.rankAll(allPois, ctx(mapOf(cafe.id to wantToGoVerdict(cafe))))
        assertTrue(cafe.id in deck.map { it.poi.id })
    }

    @Test
    fun `a place with a verdict value that is neither favorite nor want-to-go is excluded`() {
        val stray = wantToGoVerdict(cafe).copy(value = "some_other_value")
        val deck = SuggestionEngine.rankAll(allPois, ctx(mapOf(cafe.id to stray)))
        assertFalse(cafe.id in deck.map { it.poi.id })
    }

    @Test
    fun `each suggestion carries the verdict value it was ranked under`() {
        val verdicts = mapOf(
            cafe.id to wantToGoVerdict(cafe),
            kiosk.id to favoriteVerdict(kiosk)
        )
        val deck = SuggestionEngine.rankAll(allPois, ctx(verdicts))

        assertEquals(Verdict.VALUE_WANT_TO_GO, deck.first { it.poi.id == cafe.id }.verdictValue)
        assertEquals(Verdict.VALUE_FAVORITE, deck.first { it.poi.id == kiosk.id }.verdictValue)
    }

    @Test
    fun `a favorite and a want-to-go place at identical distance and conditions score identically`() {
        // Different POI ids get different novelty jitter, so compare a place against itself
        // under each verdict rather than two different places — isolates the scoring formula
        // from the (intentionally id-based) jitter term.
        val favoriteScore = SuggestionEngine.rankAll(listOf(cafe), ctx(mapOf(cafe.id to favoriteVerdict(cafe))))
            .first().score
        val wantToGoScore = SuggestionEngine.rankAll(listOf(cafe), ctx(mapOf(cafe.id to wantToGoVerdict(cafe))))
            .first().score
        assertEquals(favoriteScore, wantToGoScore, 0.0001)
    }
}
