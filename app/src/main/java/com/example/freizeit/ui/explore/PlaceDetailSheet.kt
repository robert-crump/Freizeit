package com.example.freizeit.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.ui.common.categoryColor
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.util.GeoDistance

/**
 * Shared place detail sheet, opened from map markers and list rows.
 * Verdict buttons (#6) get added below the facts — keep that slot in mind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    item: PoiWithDistance,
    onDismiss: () -> Unit
) {
    val poi = item.poi
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = poi.displayName(),
                style = MaterialTheme.typography.headlineSmall
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryDot(poi.category)
                Text(
                    text = categoryDisplayName(poi.category),
                    style = MaterialTheme.typography.labelLarge
                )
                if (poi.missingFromOsm) {
                    Text(
                        text = stringResource(R.string.detail_no_longer_in_osm),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item.distanceMeters?.let {
                Text(
                    text = stringResource(R.string.detail_distance, GeoDistance.format(it)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            poi.addressLine()?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            poi.openingHours?.let {
                Text(
                    text = stringResource(R.string.detail_opening_hours, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Verdict buttons (👍 / 👎 / ❤️) land here with #6.
        }
    }
}

@Composable
fun CategoryDot(category: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(categoryColor(category))
    )
}

@Composable
fun Poi.displayName(): String =
    name ?: stringResource(R.string.explore_unnamed, categoryDisplayName(category).lowercase())

/** "Marktplatz 8, 4750 Bütgenbach" from whichever address parts exist. */
fun Poi.addressLine(): String? {
    val streetPart = listOfNotNull(street, housenumber).joinToString(" ").ifBlank { null }
    val cityPart = listOfNotNull(postcode, city).joinToString(" ").ifBlank { null }
    val line = listOfNotNull(streetPart, cityPart).joinToString(", ")
    return line.ifBlank { null }
}
