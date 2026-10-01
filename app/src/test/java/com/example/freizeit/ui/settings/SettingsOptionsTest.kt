package com.example.freizeit.ui.settings

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsOptionsTest {

    @Test
    fun `a preset radius offers just the presets`() {
        assertEquals(listOf(5, 10, 20, 30, 40, 60, 80, 100), suggestionRadiusOptions(40))
    }

    @Test
    fun `a saved non-preset radius is kept as an extra option in sorted order`() {
        assertEquals(listOf(5, 10, 20, 25, 30, 40, 60, 80, 100), suggestionRadiusOptions(25))
        assertEquals(listOf(1, 5, 10, 20, 30, 40, 60, 80, 100), suggestionRadiusOptions(1))
        assertEquals(listOf(5, 10, 20, 30, 40, 60, 80, 100, 150), suggestionRadiusOptions(150))
    }

    @Test
    fun `backup file name starts with the date as YYMMDD`() {
        assertEquals("261001-freizeit-backup.json", backupFileName(LocalDate.of(2026, 10, 1)))
        assertEquals("270309-freizeit-backup.json", backupFileName(LocalDate.of(2027, 3, 9)))
    }
}
