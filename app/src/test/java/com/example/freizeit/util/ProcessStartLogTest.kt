package com.example.freizeit.util

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessStartLogTest {

    @Test
    fun `force stop reads as USER_REQUESTED with its time and description`() {
        val line = processStartLine(
            LastExit(exitReasonName(ApplicationExitInfo.REASON_USER_REQUESTED), 0L, "stop com.example.freizeit")
        )
        assertEquals(
            "process start (last exit: USER_REQUESTED at 1970-01-01T00:00:00Z \"stop com.example.freizeit\")",
            line
        )
    }

    @Test
    fun `blank description is left out`() {
        val line = processStartLine(LastExit("LOW_MEMORY", 0L, " "))
        assertEquals("process start (last exit: LOW_MEMORY at 1970-01-01T00:00:00Z)", line)
    }

    @Test
    fun `no recorded exit reads as unknown`() {
        assertEquals("process start (last exit: unknown)", processStartLine(null))
    }

    @Test
    fun `unlisted reason keeps its number`() {
        assertEquals("UNKNOWN(999)", exitReasonName(999))
    }
}
