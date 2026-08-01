package com.example.freizeit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.util.TravelDuration

/** Bike-duration color bands (issue #41): a genuinely close place reads as motivating green,
 *  further out is a neutral yellow, and once biking there stops being realistic the chip goes
 *  gray rather than alarming red — "far" isn't a failure, it just stops being the pitch. */
private const val GREEN_MAX_MINUTES = 10
private const val GRAY_MIN_MINUTES = 25

private val DurationGreenFont = Color(0xFF4BFFC5)
private val DurationGreenBackground = Color(0xFF194234)
private val DurationYellowFont = Color(0xFFFFBD34)
private val DurationYellowBackground = Color(0xFF5A380A)
private val DurationGrayFont = Color(0xFF9AA0A6)
private val DurationGrayBackground = Color(0xFF2C2C2E)

/**
 * Colored bike-duration chip. Once biking there is unrealistic ([GRAY_MIN_MINUTES]+), a second,
 * plain/uncolored line with the car-equivalent duration appears underneath — the #41 answer for
 * "I always drive there" places: no per-POI travel-mode setting, just an alternative estimate
 * once bike time stops being motivating. Fixed colors regardless of light/dark theme, same
 * pattern as [com.example.freizeit.ui.theme.FavoriteRed]/[com.example.freizeit.ui.theme.WantToGoBlue].
 */
@Composable
fun DurationBadge(
    distanceMeters: Double,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    val bikeMinutes = TravelDuration.bikeMinutes(distanceMeters)
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        DurationChip(bikeMinutes)
        if (bikeMinutes >= GRAY_MIN_MINUTES) {
            Text(
                text = stringResource(R.string.duration_car_alternative, TravelDuration.carMinutes(distanceMeters)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DurationChip(bikeMinutes: Int) {
    val (font, background) = when {
        bikeMinutes < GREEN_MAX_MINUTES -> DurationGreenFont to DurationGreenBackground
        bikeMinutes < GRAY_MIN_MINUTES -> DurationYellowFont to DurationYellowBackground
        else -> DurationGrayFont to DurationGrayBackground
    }
    Text(
        text = stringResource(R.string.duration_minutes, bikeMinutes),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = font,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
