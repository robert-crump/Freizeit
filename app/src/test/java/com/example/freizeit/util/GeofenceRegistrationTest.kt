package com.example.freizeit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GeofenceRegistrationTest {

    private val ids = setOf("node/1", "custom/2")

    @Test
    fun `same epoch keeps the persisted ids`() {
        val epoch = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        assertEquals(ids, effectiveRegisteredIds(ids, epoch, epoch))
    }

    @Test
    fun `reboot invalidates the persisted ids`() {
        val before = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        val after = registrationEpoch(bootCount = 8, lastUpdateTimeMillis = 1_000L)
        assertEquals(emptySet<String>(), effectiveRegisteredIds(ids, before, after))
    }

    @Test
    fun `reinstall or update invalidates the persisted ids`() {
        val before = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        val after = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 2_000L)
        assertEquals(emptySet<String>(), effectiveRegisteredIds(ids, before, after))
    }

    @Test
    fun `missing epoch from a pre-fix install invalidates the persisted ids`() {
        val now = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        assertEquals(emptySet<String>(), effectiveRegisteredIds(ids, null, now))
    }
}
