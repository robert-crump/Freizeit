package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.suggestion.SuggestionFixture.HOME
import com.example.freizeit.domain.suggestion.SuggestionFixture.allPois
import com.example.freizeit.domain.suggestion.SuggestionFixture.cafe
import com.example.freizeit.domain.suggestion.SuggestionFixture.saturdayAt
import com.example.freizeit.domain.suggestion.SuggestionFixture.sunny
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #6's ranking rules: 👎 is a permanent hard filter, ❤️/👍 boost the
 * score once their cooldown has passed, and the cooldown itself is
 * verdict-aware (❤️ ~2 days, 👍 ~2 weeks — kids' rituals are a feature).
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

    @Test
    fun `a downvoted place never appears, no matter how fresh or stale the verdict`() {
        val fresh = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_DOWN, ageMillis = 0))
        val old = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_DOWN, Duration.ofDays(365).toMillis()))
        assertFalse(cafe.id in SuggestionEngine.rankAll(allPois, ctx(fresh)).map { it.poi.id })
        assertFalse(cafe.id in SuggestionEngine.rankAll(allPois, ctx(old)).map { it.poi.id })
    }

    @Test
    fun `a loved place is suppressed within its 2-day cooldown`() {
        val verdicts = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_LOVE, Duration.ofHours(6).toMillis()))
        assertFalse(cafe.id in SuggestionEngine.rankAll(allPois, ctx(verdicts)).map { it.poi.id })
    }

    @Test
    fun `a loved place returns and gets a boost once 2 days have passed`() {
        val verdicts = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_LOVE, Duration.ofDays(3).toMillis()))
        val ranked = SuggestionEngine.rankAll(allPois, ctx(verdicts))
        val cafeSuggestion = ranked.first { it.poi.id == cafe.id }
        assertTrue("❤️ favorite" in cafeSuggestion.reasons)
    }

    @Test
    fun `a liked place is suppressed within its 2-week cooldown`() {
        val verdicts = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_UP, Duration.ofDays(5).toMillis()))
        assertFalse(cafe.id in SuggestionEngine.rankAll(allPois, ctx(verdicts)).map { it.poi.id })
    }

    @Test
    fun `a liked place returns and gets a smaller boost once 2 weeks have passed`() {
        val verdicts = mapOf(cafe.id to verdict(cafe.id, Verdict.VALUE_UP, Duration.ofDays(15).toMillis()))
        val ranked = SuggestionEngine.rankAll(allPois, ctx(verdicts))
        val cafeSuggestion = ranked.first { it.poi.id == cafe.id }
        assertTrue("👍 liked before" in cafeSuggestion.reasons)
    }

    @Test
    fun `an unverdicted place is unaffected`() {
        val ranked = SuggestionEngine.rankAll(allPois, ctx(emptyMap()))
        assertTrue(cafe.id in ranked.map { it.poi.id })
        assertTrue(ranked.first { it.poi.id == cafe.id }.reasons.none { "❤️" in it || "👍" in it })
    }
}
