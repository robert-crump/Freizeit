package com.example.freizeit.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.ui.common.DurationBadge
import com.example.freizeit.ui.common.categoryColor
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.theme.FavoriteRed
import com.example.freizeit.ui.theme.WantToGoBlue
import com.example.freizeit.util.GeoDistance

/**
 * The place detail sheet (#73's layout): name (wrapping, never under the icons) with the edit
 * pencil, want-to-go and favorite toggles; category; "distance | address"; travel chips; opening
 * hours; last visit; Check in. [onEdit] opens the edit form for any place, OSM or custom.
 * [onCheckIn] starts the app-wide check-in flow (#64); the caller closes the sheet once the
 * check-in is confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    item: PoiWithDistance,
    verdict: String?,
    onVerdictChange: (String?) -> Unit,
    lastVisit: String? = null,
    onEdit: () -> Unit,
    onCheckIn: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = poi.displayName(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    // Centers the 24 dp icons on the headline's first line.
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.detail_edit_place))
                    }
                    BookmarkButton(
                        isWantToGo = verdict == Verdict.VALUE_WANT_TO_GO,
                        onClick = {
                            onVerdictChange(if (verdict == Verdict.VALUE_WANT_TO_GO) null else Verdict.VALUE_WANT_TO_GO)
                        }
                    )
                    FavoriteButton(
                        isFavorite = verdict == Verdict.VALUE_FAVORITE,
                        onClick = {
                            onVerdictChange(if (verdict == Verdict.VALUE_FAVORITE) null else Verdict.VALUE_FAVORITE)
                        }
                    )
                }
            }
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

            val distanceText = item.distanceMeters?.let { stringResource(R.string.detail_distance, GeoDistance.format(it)) }
            listOfNotNull(distanceText, poi.addressLine()).takeIf { it.isNotEmpty() }?.let { parts ->
                Text(text = parts.joinToString(" | "), style = MaterialTheme.typography.bodyMedium)
            }
            item.distanceMeters?.let { DurationBadge(it) }

            poi.openingHours?.let {
                Text(
                    text = stringResource(R.string.detail_opening_hours, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            lastVisit?.let {
                Text(
                    text = stringResource(R.string.detail_last_visit, it),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = onCheckIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.home_checkin))
            }
        }
    }
}

/** Tapping the heart again clears the favorite; tapping it while unset sets it. */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(24.dp)) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(R.string.detail_verdict_favorite),
            tint = if (isFavorite) FavoriteRed else LocalContentColor.current
        )
    }
}

/** Tapping the bookmark again clears the want-to-go verdict; tapping it while unset sets it.
 *  Mutually exclusive with [FavoriteButton] via the shared single-verdict-per-place model
 *  (#31) — setting one silently clears the other. */
@Composable
private fun BookmarkButton(
    isWantToGo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(24.dp)) {
        Icon(
            imageVector = if (isWantToGo) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = stringResource(R.string.detail_verdict_want_to_go),
            tint = if (isWantToGo) WantToGoBlue else LocalContentColor.current
        )
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

/** The place's name (already the effective one, #73), or "Unnamed <category>". */
@Composable
fun Poi.displayName(): String =
    name ?: stringResource(R.string.map_unnamed, categoryDisplayName(category).lowercase())

/** "Marktplatz 8, 4750 Bütgenbach" from whichever address parts exist. */
fun Poi.addressLine(): String? {
    val streetPart = listOfNotNull(street, housenumber).joinToString(" ").ifBlank { null }
    val cityPart = listOfNotNull(postcode, city).joinToString(" ").ifBlank { null }
    val line = listOfNotNull(streetPart, cityPart).joinToString(", ")
    return line.ifBlank { null }
}
