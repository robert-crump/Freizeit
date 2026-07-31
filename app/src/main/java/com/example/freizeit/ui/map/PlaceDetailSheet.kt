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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.ui.common.categoryColor
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.theme.FavoriteRed
import com.example.freizeit.ui.theme.WantToGoBlue
import com.example.freizeit.util.GeoDistance

/** Shared place detail sheet, opened from map markers, list rows, and Home cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    item: PoiWithDistance,
    verdict: String?,
    onVerdictChange: (String?) -> Unit,
    customName: String?,
    onCustomNameChange: (String?) -> Unit,
    lastVisit: String? = null,
    onDismiss: () -> Unit
) {
    val poi = item.poi
    var showEditNameDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = poi.displayName(customName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showEditNameDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.detail_edit_name))
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

            item.distanceMeters?.let {
                Text(
                    text = stringResource(R.string.detail_distance, GeoDistance.format(it)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            lastVisit?.let {
                Text(
                    text = stringResource(R.string.detail_last_visit, it),
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
        }
    }

    if (showEditNameDialog) {
        CustomNameDialog(
            initialName = customName ?: poi.name.orEmpty(),
            onSave = {
                onCustomNameChange(it)
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false }
        )
    }
}

/** Empty input clears the custom name, reverting display to the OSM name/fallback. */
@Composable
private fun CustomNameDialog(
    initialName: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_custom_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.detail_custom_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifBlank { null }) }) {
                Text(stringResource(R.string.detail_custom_name_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_custom_name_cancel))
            }
        }
    )
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

@Composable
fun Poi.displayName(customName: String? = null): String =
    customName ?: name ?: stringResource(R.string.map_unnamed, categoryDisplayName(category).lowercase())

/** "Marktplatz 8, 4750 Bütgenbach" from whichever address parts exist. */
fun Poi.addressLine(): String? {
    val streetPart = listOfNotNull(street, housenumber).joinToString(" ").ifBlank { null }
    val cityPart = listOfNotNull(postcode, city).joinToString(" ").ifBlank { null }
    val line = listOfNotNull(streetPart, cityPart).joinToString(", ")
    return line.ifBlank { null }
}
