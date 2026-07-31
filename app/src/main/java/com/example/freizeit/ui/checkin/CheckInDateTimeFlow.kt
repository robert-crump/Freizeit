package com.example.freizeit.ui.checkin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.freizeit.R
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.util.formatVisitTimestamp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Shared "Check-in" flow used by both Home's suggestion card and the Check-In screen's row tap:
 * tapping Check-in goes straight to a date picker (defaulted to today, future days disabled),
 * confirming it opens a time picker (defaulted to now); confirming that creates the check-in
 * immediately and shows a Snackbar with Undo. Cancelling either step drops the whole attempt.
 *
 * [pendingPoi] is expected to be driven by the caller's own "which place is mid check-in" state,
 * called unconditionally every recomposition (not wrapped in a null check) so this composable's
 * own coroutine scope stays alive across a cancelled/restarted attempt for the same place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInDateTimeFlow(
    pendingPoi: Poi?,
    placeName: String,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onConfirmed: suspend (poi: Poi, visitedAt: Long) -> Long,
    onUndo: suspend (visitId: Long) -> Unit
) {
    // Declared before the early return below so this scope's lifetime tracks the caller's own
    // composition (stable across a single cancelled/restarted attempt), not just the window
    // where pendingPoi happens to be non-null — otherwise the confirm button's coroutine (which
    // dismisses the dialog as its first step) would race its own cancellation.
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.checkin_history_undo_action)
    val snackbarTemplate = stringResource(R.string.checkin_snackbar_message)

    if (pendingPoi == null) return
    val poi = pendingPoi

    // Reset naturally: this call site is skipped whenever pendingPoi is null, so Compose tears
    // down and re-creates this state the next time a (possibly identical) place is checked into.
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

    if (pickedDateMillis == null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { datePickerState.selectedDateMillis?.let { pickedDateMillis = it } },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text(stringResource(R.string.checkin_datetime_next))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.checkin_confirm_cancel))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = stringResource(R.string.checkin_datetime_title, placeName),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                }
            )
        }
    } else {
        val millis = pickedDateMillis ?: return
        val pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        val now = LocalTime.now()
        val timePickerState = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute)
        var useDial by remember { mutableStateOf(true) }

        CheckInTimePickerDialog(
            title = stringResource(R.string.checkin_datetime_title, placeName),
            onDismissRequest = onDismiss,
            toggle = {
                IconButton(onClick = { useDial = !useDial }) {
                    Icon(
                        imageVector = if (useDial) Icons.Filled.Keyboard else Icons.Filled.AccessTime,
                        contentDescription = stringResource(
                            if (useDial) {
                                R.string.checkin_datetime_switch_to_keyboard
                            } else {
                                R.string.checkin_datetime_switch_to_dial
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.checkin_confirm_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val visitedAt = pickedDate
                        .atTime(timePickerState.hour, timePickerState.minute)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    scope.launch {
                        val visitId = onConfirmed(poi, visitedAt)
                        onDismiss()
                        val message = String.format(
                            snackbarTemplate,
                            placeName,
                            formatVisitTimestamp(visitedAt)
                        )
                        val result = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = undoLabel
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndo(visitId)
                        }
                    }
                }) {
                    Text(stringResource(R.string.checkin_confirm_checkin))
                }
            }
        ) {
            if (useDial) TimePicker(state = timePickerState) else TimeInput(state = timePickerState)
        }
    }
}

/**
 * Material3 1.2 ships [TimePicker]/[TimeInput] but no dialog wrapper for them (unlike
 * [DatePickerDialog]) — this is the standard hand-rolled shape recommended in Material3's own
 * samples: a plain [Dialog] containing title/content/buttons plus a leading toggle slot.
 */
@Composable
private fun CheckInTimePickerDialog(
    title: String,
    onDismissRequest: () -> Unit,
    toggle: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    toggle()
                    Spacer(modifier = Modifier.weight(1f))
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
