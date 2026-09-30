package com.example.freizeit.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Looping pager math for the Home deck (#65), mirroring MyQuotes' LoopingPositionsTest. */
class LoopingPositionsTest {

    @Test
    fun `count does not loop empty or single decks`() {
        assertEquals(0, LoopingPositions.count(0))
        assertEquals(1, LoopingPositions.count(1))
    }

    @Test
    fun `count repeats longer decks for every lap`() {
        assertEquals(5 * LoopingPositions.LAPS, LoopingPositions.count(5))
    }

    @Test
    fun `indexOf round-trips through pagerPositionOf`() {
        val around = LoopingPositions.pagerPositionOf(0, 5, 0)
        for (index in 0 until 5) {
            val pos = LoopingPositions.pagerPositionOf(index, 5, around)
            assertEquals(index, LoopingPositions.indexOf(pos, 5))
        }
    }

    @Test
    fun `indexOf wraps across laps`() {
        assertEquals(4, LoopingPositions.indexOf(5 * 10 - 1, 5))
        assertEquals(0, LoopingPositions.indexOf(5 * 10, 5))
    }

    @Test
    fun `pagerPositionOf stays in the lap of around`() {
        val around = 5 * 300 + 2
        assertEquals(5 * 300 + 4, LoopingPositions.pagerPositionOf(4, 5, around))
    }

    @Test
    fun `pagerPositionOf starts from the middle lap near either end`() {
        val middle = 5 * (LoopingPositions.LAPS / 2)
        assertEquals(middle + 3, LoopingPositions.pagerPositionOf(3, 5, 0))
        assertEquals(middle + 3, LoopingPositions.pagerPositionOf(3, 5, 4))
        assertEquals(middle + 3, LoopingPositions.pagerPositionOf(3, 5, LoopingPositions.count(5) - 1))
    }

    @Test
    fun `pagerPositionOf is the index for single decks`() {
        assertEquals(0, LoopingPositions.pagerPositionOf(0, 1, 0))
    }

    @Test
    fun `pagerPositionOf stays in range when the deck shrinks`() {
        // Removing a verdict re-anchors around the old (bigger deck's) position.
        val around = LoopingPositions.count(5) - 6
        val pos = LoopingPositions.pagerPositionOf(3, 4, around)
        assertTrue(pos in 0 until LoopingPositions.count(4))
        assertEquals(3, LoopingPositions.indexOf(pos, 4))
    }
}
