package com.example.freizeit.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drag-progress math driving the swipeable card stack's fade/reveal/threshold behavior. */
class HomeSwipeDeckMathTest {

    private val threshold = 100f

    @Test
    fun `top card is fully opaque at rest`() {
        assertEquals(1f, topCardAlpha(0f, threshold), 0.001f)
    }

    @Test
    fun `top card is still partially visible right at the threshold`() {
        val alpha = topCardAlpha(threshold, threshold)
        assertTrue(alpha > 0f)
        assertTrue(alpha < 1f)
    }

    @Test
    fun `top card is fully transparent past the fade distance`() {
        assertEquals(0f, topCardAlpha(threshold * 2f, threshold), 0.001f)
    }

    @Test
    fun `top card alpha is direction-agnostic`() {
        assertEquals(
            topCardAlpha(-threshold * 0.5f, threshold),
            topCardAlpha(threshold * 0.5f, threshold),
            0.001f
        )
    }

    @Test
    fun `reveal progress mirrors top card alpha`() {
        val offset = threshold * 0.8f
        assertEquals(1f - topCardAlpha(offset, threshold), revealProgress(offset, threshold), 0.001f)
    }

    @Test
    fun `reveal progress is 0 at rest and 1 once fully faded`() {
        assertEquals(0f, revealProgress(0f, threshold), 0.001f)
        assertEquals(1f, revealProgress(threshold * 10f, threshold), 0.001f)
    }

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
