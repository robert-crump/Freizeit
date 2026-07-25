package com.example.freizeit.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.ui.common.categoryDisplayName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val suggestionRadiusKm by viewModel.suggestionRadiusKm.collectAsStateWithLifecycle()
    val autoCheckInEnabled by viewModel.autoCheckInEnabled.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var showPoiBreakdown by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPoiFile(uri)
    }

    val backupExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val backupImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineMedium
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.settings_menu_description)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_menu_poi_breakdown)) },
                        onClick = {
                            menuExpanded = false
                            showPoiBreakdown = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_import_button)) },
                        onClick = {
                            menuExpanded = false
                            filePicker.launch(arrayOf("*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_backup_export)) },
                        onClick = {
                            menuExpanded = false
                            backupExportPicker.launch("freizeit-backup.json")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_backup_import)) },
                        onClick = {
                            menuExpanded = false
                            backupImportPicker.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }

        when (val status = importStatus) {
            ImportStatus.Idle -> {}
            ImportStatus.Importing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.settings_importing))
            }
            is ImportStatus.Success -> Text(
                text = stringResource(R.string.settings_import_success, status.count),
                color = MaterialTheme.colorScheme.primary
            )
            is ImportStatus.Error -> Text(
                text = status.message,
                color = MaterialTheme.colorScheme.error
            )
        }

        when (val status = backupStatus) {
            BackupStatus.Idle -> {}
            BackupStatus.Working -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.settings_backup_working))
            }
            is BackupStatus.ExportSuccess -> Text(
                text = stringResource(R.string.settings_backup_export_success, status.count),
                color = MaterialTheme.colorScheme.primary
            )
            is BackupStatus.ImportSuccess -> Text(
                text = stringResource(R.string.settings_backup_import_success, status.count),
                color = MaterialTheme.colorScheme.primary
            )
            is BackupStatus.Error -> Text(
                text = status.message,
                color = MaterialTheme.colorScheme.error
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_suggestions_section),
                    style = MaterialTheme.typography.titleMedium
                )
                SuggestionRadiusField(
                    radiusKm = suggestionRadiusKm,
                    onCommit = viewModel::setSuggestionRadiusKm
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_auto_checkin_section),
                    style = MaterialTheme.typography.titleMedium
                )
                AutoCheckInSection(
                    enabled = autoCheckInEnabled,
                    onEnabledChange = viewModel::setAutoCheckInEnabled
                )
            }
        }
    }

    if (showPoiBreakdown) {
        AlertDialog(
            onDismissRequest = { showPoiBreakdown = false },
            title = { Text(stringResource(R.string.settings_poi_section)) },
            text = { ImportSummaryContent(summary) },
            confirmButton = {
                TextButton(onClick = { showPoiBreakdown = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        )
    }
}

/**
 * Owns the whole opt-in dance: toggling on shows the disclosure dialog first (issue #24's Play
 * Store compliance requirement), and only a completed foreground-location grant flips the
 * DataStore-backed switch on — denial leaves it off with a hint, never a silently-broken feature.
 */
@Composable
private fun AutoCheckInSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    var showDisclosure by remember { mutableStateOf(false) }
    var showDeniedHint by remember { mutableStateOf(false) }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onEnabledChange(true)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onEnabledChange(true)
        }
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.none { it }) {
            showDeniedHint = true
            return@rememberLauncherForActivityResult
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> onEnabledChange(true)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_auto_checkin_toggle),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = { checked ->
                if (checked) {
                    showDeniedHint = false
                    showDisclosure = true
                } else {
                    onEnabledChange(false)
                }
            }
        )
    }

    if (showDeniedHint) {
        Text(
            text = stringResource(R.string.settings_auto_checkin_denied_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (showDisclosure) {
        AutoCheckInDisclosureDialog(
            onConfirm = {
                showDisclosure = false
                foregroundLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onDismiss = { showDisclosure = false }
        )
    }
}

@Composable
private fun AutoCheckInDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_auto_checkin_disclosure_title)) },
        text = { Text(stringResource(R.string.settings_auto_checkin_disclosure_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_auto_checkin_disclosure_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_auto_checkin_disclosure_cancel))
            }
        }
    )
}

/**
 * Commits on blur, not per keystroke, so an in-progress edit (or a momentarily invalid one)
 * never writes to DataStore or re-runs Home's distance filter mid-type. Reverts to the last
 * committed value if the field loses focus while empty/non-numeric.
 */
@Composable
private fun SuggestionRadiusField(radiusKm: Int, onCommit: (Int) -> Unit) {
    var text by remember(radiusKm) { mutableStateOf(radiusKm.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit) },
        label = { Text(stringResource(R.string.settings_radius_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.onFocusChanged { focusState ->
            if (!focusState.isFocused) {
                val parsed = text.toIntOrNull()
                if (parsed != null && parsed >= 1) {
                    onCommit(parsed)
                } else {
                    text = radiusKm.toString()
                }
            }
        }
    )
}

@Composable
private fun ImportSummaryContent(summary: PoiSummary?) {
    // No early returns inside composable lambdas: switching branches across
    // recompositions corrupts the composer's group stack (Stack.pop IOOBE).
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val info = summary?.importInfo
        if (info == null) {
            Text(
                text = stringResource(R.string.settings_no_import),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(
                    R.string.settings_last_import,
                    formatTimestamp(info.importedAt)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(4.dp))
            summary.categoryCounts.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = categoryDisplayName(entry.category),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "%,d".format(entry.count),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (summary.missingCount > 0) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(
                        R.string.settings_missing_flagged,
                        summary.missingCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
