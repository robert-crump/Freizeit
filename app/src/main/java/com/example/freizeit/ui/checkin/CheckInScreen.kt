package com.example.freizeit.ui.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Visit
import com.example.freizeit.ui.common.ScreenTitleBar
import com.example.freizeit.ui.map.displayName
import com.example.freizeit.util.bucketVisits
import com.example.freizeit.util.formatVisitDateAndTime
import com.example.freizeit.util.formatVisitTimeOnly

/**
 * Check-in tab root: the check-in history list, with a "+" FAB that opens [CheckInSearchScreen]
 * to record a new one (#39). [checkInSnackbarHostState] is hoisted at `FreizeitApp` level
 * (shared with the search screen and [CheckInDateTimeFlow]) so the "Checked into X" Undo
 * snackbar surfaces here, after auto-popping back from search on confirm. The delete Undo
 * snackbar shares that host (#78). A row tap outside selection mode calls [onOpenPlace]
 * with the visit's place id (#63); a place that's gone is reported on that same snackbar host.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CheckInScreen(
    onOpenSearch: () -> Unit,
    onOpenPlace: (placeId: String) -> Unit,
    checkInSnackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: CheckInHistoryViewModel = viewModel(factory = CheckInHistoryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = remember(state.visits) { bucketVisits(state.visits) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    var previousVisitCount by remember { mutableStateOf(0) }

    val undoMessage = if (state.undoableDeleteCount > 0) {
        pluralStringResource(
            R.plurals.checkin_history_delete_undo_message,
            state.undoableDeleteCount,
            state.undoableDeleteCount
        )
    } else {
        null
    }
    val undoActionLabel = stringResource(R.string.checkin_history_undo_action)

    // Scroll to top when a new check-in is added (detected by visits count increasing)
    LaunchedEffect(state.visits.size) {
        if (state.visits.isNotEmpty() && state.visits.size > previousVisitCount) {
            lazyListState.animateScrollToItem(0)
        }
        previousVisitCount = state.visits.size
    }

    LaunchedEffect(undoMessage) {
        if (undoMessage != null) {
            val result = checkInSnackbarHostState.showSnackbar(
                message = undoMessage,
                actionLabel = undoActionLabel,
                // Material3 defaults duration to Indefinite whenever actionLabel is non-null —
                // without this, the undo window would never actually time out on its own.
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.dismissUndo()
            }
        }
    }

    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

    // Height of the visible snackbar (0 when none); the FAB rides above it (#78).
    var snackbarHeightPx by remember { mutableIntStateOf(0) }
    val fabLift by animateDpAsState(
        targetValue = with(LocalDensity.current) { snackbarHeightPx.toDp() },
        label = "fabLift"
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            // Same padding as Settings' title, which sits in a 16 dp padded column (#78).
            val barModifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            if (state.isSelecting) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    onDeleteClick = { showDeleteConfirm = true },
                    onCancelClick = viewModel::clearSelection,
                    modifier = barModifier
                )
            } else {
                ScreenTitleBar(
                    title = stringResource(R.string.checkin_history_title),
                    modifier = barModifier
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.visits.isEmpty()) {
                    Text(
                        text = stringResource(R.string.checkin_history_empty),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Keeps the last row scrollable out from under the FAB.
                        contentPadding = PaddingValues(bottom = 88.dp),
                        state = lazyListState
                    ) {
                        sections.forEach { section ->
                            stickyHeader(key = section.label) {
                                SectionHeader(section.label)
                            }
                            itemsIndexed(section.visits, key = { _, visit -> visit.id }) { index, visit ->
                                VisitRow(
                                    visit = visit,
                                    place = state.places[visit.placeId],
                                    isFirstInGroup = index == 0,
                                    isLastInGroup = index == section.visits.lastIndex,
                                    timestampText = if (section.label == "Today") {
                                        formatVisitTimeOnly(visit.visitedAt)
                                    } else {
                                        formatVisitDateAndTime(visit.visitedAt)
                                    },
                                    isSelecting = state.isSelecting,
                                    isSelected = visit.id in state.selectedIds,
                                    onLongPress = { viewModel.startSelecting(visit.id) },
                                    onClick = {
                                        if (state.isSelecting) {
                                            viewModel.toggleSelected(visit.id)
                                        } else {
                                            onOpenPlace(visit.placeId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!state.isSelecting) {
                FloatingActionButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .offset(y = -fabLift)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.checkin_search_button))
                }
            }

            // The one snackbar host for this tab: check-in confirmations, "place no longer
            // exists" and the delete Undo all queue here, so they never stack (#78). Its height
            // stays until the exit fade ends, then the FAB slides back down.
            SnackbarHost(
                hostState = checkInSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { snackbarHeightPx = it.height }
            )
        }
    }

    if (showDeleteConfirm) {
        val count = state.selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    pluralStringResource(R.plurals.checkin_history_delete_confirm_message, count, count)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelected()
                }) {
                    Text(stringResource(R.string.checkin_history_delete_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.checkin_history_delete_confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Same bar and height as the title it replaces, so entering select mode moves nothing (#78).
    ScreenTitleBar(
        title = stringResource(R.string.checkin_history_selected_count, selectedCount),
        titleStyle = MaterialTheme.typography.titleMedium,
        modifier = modifier
    ) {
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.checkin_history_selection_delete)
            )
        }
        IconButton(onClick = onCancelClick) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.checkin_history_selection_clear)
            )
        }
    }
}

/** Inert, sticky section label ("Today", "This week", ...) — not part of multi-select, since
 *  the [LazyColumn]'s `items` blocks only ever see [Visit]s, never headers. Opaque so rows
 *  scrolling under it stay hidden. */
@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// Grouped-rows look borrowed from Hue and You's settings (#60): each section's rows are
// separate rounded tiles with a small gap, and only the group's outer corners are large.
private val GroupOuterCorner = 24.dp
private val GroupInnerCorner = 4.dp
private val GroupRowGap = 2.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VisitRow(
    visit: Visit,
    /** The place as it is now, null once it no longer exists (#73). */
    place: Poi?,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    timestampText: String,
    isSelecting: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val top = if (isFirstInGroup) GroupOuterCorner else GroupInnerCorner
    val bottom = if (isLastInGroup) GroupOuterCorner else GroupInnerCorner
    Surface(
        shape = RoundedCornerShape(top, top, bottom, bottom),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (isFirstInGroup) 0.dp else GroupRowGap)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSelecting) {
                // The row is the tap target, so the Checkbox drops its 48 dp minimum and the row
                // keeps its height in select mode (#78).
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Checkbox(checked = isSelected, onCheckedChange = null)
                }
            }
            Text(
                text = place?.displayName()
                    ?: visit.snapshotName
                    ?: stringResource(R.string.checkin_history_unnamed_place),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = timestampText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
