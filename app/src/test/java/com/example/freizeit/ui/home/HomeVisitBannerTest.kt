package com.example.freizeit.ui.home

import com.example.freizeit.data.entity.PendingVisit
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The next-open banner appears only once a "Go" tap is at least ~2h old. */
class HomeVisitBannerTest {

    private fun visit(wentAt: Long) = PendingVisit(
        placeId = "node/1",
        snapshotName = "Café Sonne",
        snapshotCategory = "cafe",
        snapshotLat = 50.9,
        snapshotLon = 6.9,
        wentAt = wentAt
    )

    @Test
    fun `not ready right after the Go tap`() {
        val now = 10_000_000L
        assertFalse(visit(wentAt = now).isReadyForBanner(now))
    }

    @Test
    fun `not ready 1 hour later`() {
        val now = 10_000_000L
        val wentAt = now - Duration.ofHours(1).toMillis()
        assertFalse(visit(wentAt).isReadyForBanner(now))
    }

    @Test
    fun `ready exactly at the 2 hour threshold`() {
        val now = 10_000_000L
        val wentAt = now - Duration.ofHours(2).toMillis()
        assertTrue(visit(wentAt).isReadyForBanner(now))
    }

    @Test
    fun `ready well after 2 hours`() {
        val now = 10_000_000L
        val wentAt = now - Duration.ofDays(1).toMillis()
        assertTrue(visit(wentAt).isReadyForBanner(now))
    }
}
