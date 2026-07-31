package com.example.freizeit.ui.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.entity.Visit
import com.example.freizeit.util.formatVisitTimestamp

/**
 * Check-in tab root: the check-in history list, with a "+" FAB that opens [CheckInSearchScreen]
 * to record a new one (#39). [lastCheckedInName]/[checkInSnackbarHostState] are hoisted at
 * `FreizeitApp` level (shared with the search screen and [CheckInDateTimeFlow]) so the
 * "Checked in to X" banner and its Undo snackbar surface here, after auto-popping back from
 * search on confirm — [CheckInHistoryViewModel]'s own selection/delete/undo stays local to this
 * route, unrelated to check-in creation.
 */
@Composable
fun CheckInScreen(
    onOpenSearch: () -> Unit,
    lastCheckedInName: String?,
    checkInSnackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: CheckInHistoryViewModel = viewModel(factory = CheckInHistoryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val deleteSnackbarHostState = remember { SnackbarHostState() }
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

    LaunchedEffect(undoMessage) {
        if (undoMessage != null) {
            val result = deleteSnackbarHostState.showSnackbar(
                message = undoMessage,
                actionLabel = undoActionLabel
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.dismissUndo()
            }
        }
    }

    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = deleteSnackbarHostState) },
        topBar = {
            if (state.isSelecting) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    onDeleteClick = { showDeleteConfirm = true },
                    onCancelClick = viewModel::clearSelection
                )
            } else {
                Text(
                    text = stringResource(R.string.checkin_history_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                lastCheckedInName?.let { name ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text = stringResource(R.string.checkin_checked_in, name),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
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
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.visits, key = { it.id }) { visit ->
                            VisitRow(
                                visit = visit,
                                isSelecting = state.isSelecting,
                                isSelected = visit.id in state.selectedIds,
                                onLongPress = { viewModel.startSelecting(visit.id) },
                                onClick = { if (state.isSelecting) viewModel.toggleSelected(visit.id) }
                            )
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
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.checkin_search_button))
                }
            }

            SnackbarHost(hostState = checkInSnackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.checkin_history_selected_count, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VisitRow(
    visit: Visit,
    isSelecting: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isSelecting) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = visit.snapshotName ?: stringResource(R.string.checkin_history_unnamed_place),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = formatVisitTimestamp(visit.visitedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
