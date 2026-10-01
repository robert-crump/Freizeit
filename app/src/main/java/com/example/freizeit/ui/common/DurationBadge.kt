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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.util.TravelDuration
import com.example.freizeit.util.TravelMode

/** Duration color bands (issues #41, #75): a genuinely close place reads as motivating green,
 *  further out is a neutral yellow. Only the first chip is banded; the second is always gray. */
private const val GREEN_MAX_MINUTES = 10

private val DurationGreenFont = Color(0xFF4BFFC5)
private val DurationGreenBackground = Color(0xFF194234)
private val DurationYellowFont = Color(0xFFFFBD34)
private val DurationYellowBackground = Color(0xFF5A380A)
private val DurationGrayFont = Color(0xFF9AA0A6)
private val DurationGrayBackground = Color(0xFF2C2C2E)

/**
 * One or two travel-time chips side by side ([TravelDuration.estimates], #75): walk + bike when
 * close, bike, bike + car, or car alone when far. The first chip is the pitch and is colored by
 * its minutes; the second is the gray alternative. Fixed colors regardless of light/dark theme,
 * same pattern as [com.example.freizeit.ui.theme.FavoriteRed]/[com.example.freizeit.ui.theme.WantToGoBlue].
 */
@Composable
fun DurationBadge(distanceMeters: Double, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TravelDuration.estimates(distanceMeters).forEachIndexed { index, estimate ->
            val (font, background) = when {
                index > 0 -> DurationGrayFont to DurationGrayBackground
                estimate.minutes < GREEN_MAX_MINUTES -> DurationGreenFont to DurationGreenBackground
                else -> DurationYellowFont to DurationYellowBackground
            }
            DurationChip(estimate.mode.icon(), estimate.minutes, font, background)
        }
    }
}

private fun TravelMode.icon(): ImageVector = when (this) {
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
