package com.example.freizeit.ui.home

/**
 * Maps the suggestion deck onto a looping pager (#65, ported from MyQuotes): the deck repeats
 * [LAPS] times, so there are cards on both sides of every card, including the first. Decks of 0
 * or 1 places don't loop.
 */
internal object LoopingPositions {
    const val LAPS = 1000

    /** The pager's page count for a deck of this size. */
    fun count(size: Int): Int = if (size <= 1) size else size * LAPS

    /** The deck index shown at this pager position. */
    fun indexOf(pagerPosition: Int, size: Int): Int =
        if (size <= 1) pagerPosition else pagerPosition % size

    /**
     * The pager position showing deck [index] in the same lap as [around] (usually the current
     * page). Near either end of the range it starts over from the middle lap instead, so there is
     * always room to swipe both ways.
     */
    fun pagerPositionOf(index: Int, size: Int, around: Int): Int {
        if (size <= 1) return index
        var lap = around / size
        if (lap <= 0 || lap >= LAPS - 1) lap = LAPS / 2
        return lap * size + index
    }
}
