package com.example.freizeit.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.ui.map.markerForegroundColor
import com.example.freizeit.ui.theme.LocalDarkTheme

/** Matches FilterChipDefaults' own default outlined-chip border width. */
private val BORDER_WIDTH = 1.dp
private val BAR_HEIGHT = 48.dp
private val ICON_SIZE = 24.dp

/**
 * The one search bar (#77) shared by the Map's oval, the Map search overlay and the check-in
 * search screen. Geometry is identical everywhere (outer padding included), so opening the
 * overlay over the oval swaps only the 24 dp [leadingIcon] (🔍 → ←) in place: hint, text and bar
 * don't move.
 *
 * With [onQueryChange] null the bar is read-only (the Map's oval): it shows [query] or the hint,
 * and a tap anywhere but the ✕ calls [onBarClick]. Otherwise [query] is edited in a
 * [BasicTextField] (no outline of its own), focused through [focusRequester]. The trailing ✕
 * shows whenever [query] isn't empty and calls [onClear].
 */
@Composable
fun SearchOvalBar(
    query: String,
    leadingIcon: ImageVector,
    leadingContentDescription: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onLeadingClick: (() -> Unit)? = null,
    onQueryChange: ((String) -> Unit)? = null,
    onBarClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val darkTheme = LocalDarkTheme.current
    val ovalShape = RoundedCornerShape(percent = 50)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // Same border treatment as the map's filter chips: without it, a dark oval on the
            // dark map style can be hard to make out against the tiles behind it.
            .border(BORDER_WIDTH, markerForegroundColor(darkTheme), ovalShape)
            .then(if (onBarClick != null) Modifier.clickable(onClick = onBarClick) else Modifier),
        shape = ovalShape,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                leadingIcon,
                contentDescription = leadingContentDescription,
                modifier = Modifier
                    .size(ICON_SIZE)
                    .then(
                        if (onLeadingClick != null) {
                            Modifier.clip(CircleShape).clickable(onClick = onLeadingClick)
                        } else {
                            Modifier
                        }
                    )
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_bar_placeholder),
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (onQueryChange == null) {
                    if (query.isNotEmpty()) {
                        Text(text = query, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                        textStyle = textStyle,
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                }
            }
            if (query.isNotEmpty()) {
                // A plain icon-sized tap target rather than an IconButton: IconButton's 48dp
                // minimum would make the bar taller whenever there's text (#59).
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.search_bar_clear),
                    modifier = Modifier
                        .size(ICON_SIZE)
                        .clip(CircleShape)
                        .clickable(onClick = onClear)
                )
            }
        }
    }
}
