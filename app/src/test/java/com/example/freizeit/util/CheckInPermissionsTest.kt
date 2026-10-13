package com.example.freizeit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInPermissionsTest {

    @Test
    fun `nothing missing when everything is granted`() {
        assertEquals(emptyList<CheckInPermission>(), missingCheckInPermissions(true, true, true, true))
    }

    @Test
    fun `everything missing comes back in asking order - battery last`() {
        assertEquals(
            listOf(
                CheckInPermission.FOREGROUND_LOCATION,
                CheckInPermission.NOTIFICATIONS,
                CheckInPermission.BACKGROUND_LOCATION,
                CheckInPermission.BATTERY_UNRESTRICTED
            ),
            missingCheckInPermissions(false, false, false, false)
        )
    }

    @Test
    fun `while-in-use location without notifications - the state that silently broke check-ins`() {
        assertEquals(
            listOf(CheckInPermission.NOTIFICATIONS, CheckInPermission.BACKGROUND_LOCATION),
            missingCheckInPermissions(
                hasForegroundLocation = true,
                hasBackgroundLocation = false,
                hasNotifications = false,
                hasBatteryExemption = true
            )
        )
    }

    @Test
    fun `paused only while a switch is on and something is missing`() {
        val missing = listOf(CheckInPermission.BACKGROUND_LOCATION)
        assertEquals(true, checkInsPaused(favorites = true, wantToGo = false, missing = missing))
        assertEquals(true, checkInsPaused(favorites = false, wantToGo = true, missing = missing))
        assertEquals(false, checkInsPaused(favorites = false, wantToGo = false, missing = missing))
        assertEquals(false, checkInsPaused(favorites = true, wantToGo = true, missing = emptyList()))
    }

    @Test
    fun `every grant but battery optimization still counts as missing`() {
        assertEquals(
            listOf(CheckInPermission.BATTERY_UNRESTRICTED),
            missingCheckInPermissions(
                hasForegroundLocation = true,
                hasBackgroundLocation = true,
                hasNotifications = true,
                hasBatteryExemption = false
            )
        )
    }
}
