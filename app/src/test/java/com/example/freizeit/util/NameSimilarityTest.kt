package com.example.freizeit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSimilarityTest {

    @Test
    fun `identical names match`() {
        assertTrue(NameSimilarity.isSimilar("Café Sonne", "Café Sonne"))
    }

    @Test
    fun `case and punctuation differences are ignored`() {
        assertTrue(NameSimilarity.isSimilar("Cafe Sonne!", "cafe   sonne"))
    }

    @Test
    fun `one name containing the other matches`() {
        assertTrue(NameSimilarity.isSimilar("Café Sonne", "Café Sonne Aachen"))
    }

    @Test
    fun `close but not identical names match by edit distance`() {
        // A single missing letter - not a substring match either way, but close enough.
        assertTrue(NameSimilarity.isSimilar("Cafe Sonne", "Cafe Sone"))
    }

    @Test
    fun `unrelated names do not match`() {
        assertFalse(NameSimilarity.isSimilar("Cafe Sonne", "Backerei Klein"))
    }

    @Test
    fun `blank name never matches, even another blank`() {
        assertFalse(NameSimilarity.isSimilar("", "Café Sonne"))
        assertFalse(NameSimilarity.isSimilar("Café Sonne", "   "))
        assertFalse(NameSimilarity.isSimilar("", ""))
    }

    @Test
    fun `punctuation-only name normalizes to blank and never matches`() {
        assertFalse(NameSimilarity.isSimilar("!!!", "Café Sonne"))
    }

    @Test
    fun `custom threshold is stricter than the default`() {
        // "Cafe Sone" vs "Cafe Sonne" is close but not identical - passes the default threshold,
        // fails a near-1.0 one.
        assertTrue(NameSimilarity.isSimilar("Cafe Sone", "Cafe Sonne"))
        assertFalse(NameSimilarity.isSimilar("Cafe Sone", "Cafe Sonne", threshold = 0.99))
    }

    @Test
    fun `similarityRatio is 1 for identical strings and 0 for completely disjoint same-length strings`() {
        assertEquals(1.0, NameSimilarity.similarityRatio("abc", "abc"), 0.0001)
        assertEquals(0.0, NameSimilarity.similarityRatio("abc", "xyz"), 0.0001)
    }

    @Test
    fun `similarityRatio of two empty strings is 1`() {
        assertEquals(1.0, NameSimilarity.similarityRatio("", ""), 0.0001)
    }

    @Test
    fun `normalize collapses whitespace and punctuation and lowercases`() {
        assertEquals("cafe sonne", NameSimilarity.normalize("  Cafe-Sonne!! "))
    }
}
