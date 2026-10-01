package com.example.freizeit.util

import com.example.freizeit.util.TravelMode.BIKE
import com.example.freizeit.util.TravelMode.CAR
import com.example.freizeit.util.TravelMode.WALK
import org.junit.Assert.assertEquals
import org.junit.Test

/** Straight-line distances below; every mode multiplies by the 1.3 detour first (#75). */
class TravelDurationTest {

    private fun modes(distanceMeters: Double) = TravelDuration.estimates(distanceMeters).map { it.mode }

    @Test
    fun `walk - 4 km per hour`() {
        assertEquals(18, TravelDuration.walkMinutes(900.0)) // 1170 m / 66.7 m/min = 17.6
        assertEquals(39, TravelDuration.walkMinutes(2_000.0)) // 2600 m = 39 min
    }

    @Test
    fun `bike - 10 km per hour within the first 3 km of road`() {
        assertEquals(9, TravelDuration.bikeMinutes(1_200.0)) // 1560 m / 166.7 m/min = 9.4
        assertEquals(18, TravelDuration.bikeMinutes(3_000.0 / 1.3)) // exactly 3 km of road
    }

    @Test
    fun `bike - 15 km per hour for the road beyond 3 km`() {
        // 6.5 km of road: 3 km at 10 km/h (18 min) + 3.5 km at 15 km/h (14 min)
        assertEquals(32, TravelDuration.bikeMinutes(5_000.0))
    }

    @Test
    fun `bike - a longer ride never estimates shorter`() {
        val minutes = (0..200).map { TravelDuration.bikeMinutes(it * 50.0) }
        assertEquals(minutes.sorted(), minutes)
    }

    @Test
    fun `car - 30 km per hour within the first 10 km of road`() {
        assertEquals(13, TravelDuration.carMinutes(5_000.0)) // 6500 m / 500 m/min
        assertEquals(20, TravelDuration.carMinutes(10_000.0 / 1.3)) // exactly 10 km of road
    }

    @Test
    fun `car - 70 km per hour for the road beyond 10 km`() {
        // 26 km of road: 10 km at 30 km/h (20 min) + 16 km at 70 km/h (13.7 min)
        assertEquals(34, TravelDuration.carMinutes(20_000.0))
    }

    @Test
    fun `every mode takes at least a minute`() {
        assertEquals(1, TravelDuration.walkMinutes(0.0))
        assertEquals(1, TravelDuration.bikeMinutes(0.0))
        assertEquals(1, TravelDuration.carMinutes(0.0))
    }

    @Test
    fun `walk up to 20 minutes - walk first, bike second`() {
        assertEquals(listOf(WALK, BIKE), modes(1_000.0)) // walk 19.5
    }

    @Test
    fun `walk over 20 minutes and bike under 25 - bike alone`() {
        assertEquals(listOf(BIKE), modes(1_200.0)) // walk 23, bike 6
        assertEquals(listOf(BIKE), modes(3_500.0)) // bike 24
    }

    @Test
    fun `bike 25 to 60 minutes - bike first, car second`() {
        assertEquals(listOf(BIKE, CAR), modes(5_000.0)) // bike 32
        assertEquals(listOf(BIKE, CAR), modes(10_380.0)) // bike 60
    }

    @Test
    fun `bike over 60 minutes - car alone`() {
        assertEquals(listOf(CAR), modes(12_000.0)) // bike 68
    }

    @Test
    fun `estimates carry each mode's own minutes`() {
        assertEquals(
            listOf(TravelEstimate(BIKE, 32), TravelEstimate(CAR, 13)),
            TravelDuration.estimates(5_000.0)
        )
    }
}
