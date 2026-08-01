package com.example.freizeit.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drag-distance math deciding whether a released swipe commits (fades out/in) or springs back. */
class HomeSwipeDeckMathTest {

    private val threshold = 100f

    @Test
    fun `not past threshold below the limit`() {
        assertFalse(isPastSwipeThreshold(threshold * 0.99f, threshold))
    }

    @Test
    fun `past threshold once the limit is exceeded`() {
        assertTrue(isPastSwipeThreshold(threshold * 1.01f, threshold))
        assertTrue(isPastSwipeThreshold(-threshold * 1.01f, threshold))
    }
}
