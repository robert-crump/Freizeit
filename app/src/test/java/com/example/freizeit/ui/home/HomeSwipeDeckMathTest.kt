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
    fun `top card is fully faded right at the threshold`() {
        assertEquals(0f, topCardAlpha(threshold, threshold), 0.001f)
    }

    @Test
    fun `top card alpha bottoms out at 0 once past the threshold`() {
        assertEquals(0f, topCardAlpha(threshold * 2f, threshold), 0.001f)
        assertEquals(0f, topCardAlpha(threshold * 10f, threshold), 0.001f)
    }

    @Test
    fun `top card alpha is still above zero just short of the threshold`() {
        assertTrue(topCardAlpha(threshold * 0.9f, threshold) > 0f)
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
    fun `reveal progress reaches 1 once top card alpha has already bottomed out`() {
        val offset = threshold * 1.75f
        assertEquals(1f, revealProgress(offset, threshold), 0.001f)
        assertEquals(0f, topCardAlpha(offset, threshold), 0.001f)
    }

    @Test
    fun `reveal progress is 0 at rest and 1 once fully dragged past the fade distance`() {
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
