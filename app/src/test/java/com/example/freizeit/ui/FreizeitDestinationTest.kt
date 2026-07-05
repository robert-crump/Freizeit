package com.example.freizeit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FreizeitDestinationTest {

    @Test
    fun `has exactly the three tabs`() {
        assertEquals(
            listOf("home", "explore", "settings"),
            FreizeitDestination.entries.map { it.route }
        )
    }

    @Test
    fun `routes are unique`() {
        val routes = FreizeitDestination.entries.map { it.route }
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun `home is the start destination`() {
        assertEquals(FreizeitDestination.HOME, FreizeitDestination.entries.first())
    }
}
