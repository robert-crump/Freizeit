package com.example.freizeit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.util.DurationBand
import com.example.freizeit.util.TravelDuration
import com.example.freizeit.util.TravelLimits
import com.example.freizeit.util.TravelMode

/** The user's travel-time color limits (Settings), provided once at the app root. */
val LocalTravelLimits = staticCompositionLocalOf { TravelLimits() }

// Duration chip colors, the UV index palette's good / moderate / very high pairs.
private val DurationGreenFont = Color(0xFF4BFFC5)
private val DurationGreenBackground = Color(0xFF194234)
private val DurationOrangeFont = Color(0xFFFFBD34)
private val DurationOrangeBackground = Color(0xFF5A380A)
private val DurationRedFont = Color(0xFFF32D1F)
private val DurationRedBackground = Color(0xFF511D12)

/**
 * Walk, bike and car chips side by side ([TravelDuration.estimates]), each colored by its own
 * minutes against [LocalTravelLimits]. With [showMinutes] false (search results) the chips carry only
 * the mode icon. Fixed colors regardless of light/dark theme, same pattern as
 * [com.example.freizeit.ui.theme.FavoriteRed]/[com.example.freizeit.ui.theme.WantToGoBlue].
 */
@Composable
fun DurationBadge(distanceMeters: Double, modifier: Modifier = Modifier, showMinutes: Boolean = true) {
    val limits = LocalTravelLimits.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (showMinutes) 8.dp else 4.dp)) {
        TravelDuration.estimates(distanceMeters).forEach { estimate ->
            val (font, background) = limits.bandOf(estimate.minutes).colors()
            if (showMinutes) {
                DurationChip(estimate.mode.icon(), estimate.minutes, font, background)
            } else {
                DurationIconChip(estimate.mode.icon(), font, background)
            }
        }
    }
}

/** Font (icon/text) and background color of a chip in this band; the background alone is the
 *  band's swatch in Settings. */
fun DurationBand.colors(): Pair<Color, Color> = when (this) {
    DurationBand.GREEN -> DurationGreenFont to DurationGreenBackground
    DurationBand.ORANGE -> DurationOrangeFont to DurationOrangeBackground
    DurationBand.RED -> DurationRedFont to DurationRedBackground
}

fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    TravelMode.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    TravelMode.CAR -> Icons.Filled.DirectionsCar
}

@Composable
private fun DurationChip(icon: ImageVector, minutes: Int, font: Color, background: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = font, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.duration_minutes, minutes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = font
        )
    }
}

/** [DurationChip] without the minutes: just the colored mode icon. */
@Composable
private fun DurationIconChip(icon: ImageVector, font: Color, background: Color) {
    Icon(
        icon,
        contentDescription = null,
        tint = font,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .size(16.dp)
    )
}
