package com.example.freizeit.util

import com.example.freizeit.util.TravelMode.BIKE
import com.example.freizeit.util.TravelMode.CAR
import com.example.freizeit.util.TravelMode.WALK
import org.junit.Assert.assertEquals
import org.junit.Test

/** Straight-line distances below; every mode multiplies by the 1.3 detour first (#75). */
class TravelDurationTest {

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
    fun `estimates - walk, bike and car with each mode's own minutes`() {
        assertEquals(
            listOf(TravelEstimate(WALK, 98), TravelEstimate(BIKE, 32), TravelEstimate(CAR, 13)),
            TravelDuration.estimates(5_000.0)
        )
    }

    @Test
    fun `bands - upper limits are inclusive`() {
        val limits = BandLimits(greenMax = 20, orangeMax = 30)
        assertEquals(DurationBand.GREEN, limits.bandOf(20))
        assertEquals(DurationBand.ORANGE, limits.bandOf(21))
        assertEquals(DurationBand.ORANGE, limits.bandOf(30))
        assertEquals(DurationBand.RED, limits.bandOf(31))
    }

    @Test
    fun `default limits - car stays orange longer than walk and bike`() {
        val limits = TravelLimits()
        assertEquals(DurationBand.RED, limits.bandOf(TravelEstimate(WALK, 35)))
        assertEquals(DurationBand.RED, limits.bandOf(TravelEstimate(BIKE, 35)))
        assertEquals(DurationBand.ORANGE, limits.bandOf(TravelEstimate(CAR, 35)))
    }

    @Test
    fun `sanitized limits - snapped to 5 minute steps within 5 to 90, orange never below green`() {
        assertEquals(BandLimits(20, 30), BandLimits(19, 31).sanitized())
        assertEquals(BandLimits(5, 90), BandLimits(0, 200).sanitized())
        assertEquals(BandLimits(40, 40), BandLimits(40, 25).sanitized())
    }
}
