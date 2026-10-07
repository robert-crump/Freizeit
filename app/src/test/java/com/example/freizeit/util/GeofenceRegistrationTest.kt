package com.example.freizeit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `different persisted epoch is a reset`() {
        val before = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        val after = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 2_000L)
        assertTrue(isEpochReset(ids, before, after))
    }

    @Test
    fun `same persisted epoch is not a reset`() {
        val epoch = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        assertFalse(isEpochReset(ids, epoch, epoch))
    }

    @Test
    fun `nothing persisted is not a reset`() {
        val now = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        assertFalse(isEpochReset(emptySet(), null, now))
    }

    @Test
    fun `ids persisted without an epoch is a reset`() {
        val now = registrationEpoch(bootCount = 7, lastUpdateTimeMillis = 1_000L)
        assertTrue(isEpochReset(ids, null, now))
    }

    @Test
    fun `boot completed within the persisted boot is an unstop after force stop`() {
        val epoch = registrationEpoch(bootCount = 21, lastUpdateTimeMillis = 1_000L)
        assertTrue(isUnstopAfterForceStop(epoch, currentBootCount = 21))
    }

    @Test
    fun `boot completed after a reboot is not an unstop`() {
        val epoch = registrationEpoch(bootCount = 21, lastUpdateTimeMillis = 1_000L)
        assertFalse(isUnstopAfterForceStop(epoch, currentBootCount = 22))
    }

    @Test
    fun `boot completed with nothing persisted is not an unstop`() {
        assertFalse(isUnstopAfterForceStop(null, currentBootCount = 21))
    }

    @Test
    fun `an update within the same boot still counts as an unstop`() {
        // Studio's install force-stops the app, so the first launch after it is an unstop too.
        val beforeUpdate = registrationEpoch(bootCount = 21, lastUpdateTimeMillis = 1_000L)
        assertTrue(isUnstopAfterForceStop(beforeUpdate, currentBootCount = 21))
    }
}
