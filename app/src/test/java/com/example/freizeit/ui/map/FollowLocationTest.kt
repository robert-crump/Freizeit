package com.example.freizeit.ui.map

import com.example.freizeit.util.LatLon
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowLocationTest {

    private fun fix(accuracyMeters: Float?) = LatLon(50.77, 6.08, accuracyMeters)

    @Test
    fun `a fix within 50 m ends following`() {
        assertTrue(isFineFix(fix(12f)))
        assertTrue(isFineFix(fix(50f)))
    }

    @Test
    fun `a coarse fix keeps following`() {
        assertFalse(isFineFix(fix(50.5f)))
        assertFalse(isFineFix(fix(1200f)))
    }

    @Test
    fun `a fix without an accuracy counts as coarse`() {
        assertFalse(isFineFix(fix(null)))
    }
}
