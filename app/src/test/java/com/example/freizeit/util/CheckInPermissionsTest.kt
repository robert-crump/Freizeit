package com.example.freizeit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInPermissionsTest {

    @Test
    fun `nothing missing when everything is granted`() {
        assertEquals(emptyList<CheckInPermission>(), missingCheckInPermissions(true, true, true))
    }

    @Test
    fun `everything missing comes back in asking order - background location last`() {
        assertEquals(
            listOf(
                CheckInPermission.FOREGROUND_LOCATION,
                CheckInPermission.NOTIFICATIONS,
                CheckInPermission.BACKGROUND_LOCATION
            ),
            missingCheckInPermissions(false, false, false)
        )
    }

    @Test
    fun `while-in-use location without notifications - the state that silently broke check-ins`() {
        assertEquals(
            listOf(CheckInPermission.NOTIFICATIONS, CheckInPermission.BACKGROUND_LOCATION),
            missingCheckInPermissions(hasForegroundLocation = true, hasBackgroundLocation = false, hasNotifications = false)
        )
    }
}
