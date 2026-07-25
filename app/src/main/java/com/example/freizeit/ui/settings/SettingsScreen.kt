package com.example.freizeit.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.util.AutoCheckInPermissions
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
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = stringResource(R.string.settings_poi_section),
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = { filePicker.launch(arrayOf("*/*")) },
            enabled = importStatus != ImportStatus.Importing
        ) {
            Text(stringResource(R.string.settings_import_button))
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

        ImportSummaryCard(summary)

        Text(
            text = stringResource(R.string.settings_backup_section),
            style = MaterialTheme.typography.titleMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { backupExportPicker.launch("freizeit-backup.json") },
                enabled = backupStatus != BackupStatus.Working
            ) {
                Text(stringResource(R.string.settings_backup_export))
            }
            Button(
                onClick = { backupImportPicker.launch(arrayOf("*/*")) },
                enabled = backupStatus != BackupStatus.Working
            ) {
                Text(stringResource(R.string.settings_backup_import))
            }
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

        Text(
            text = stringResource(R.string.settings_suggestions_section),
            style = MaterialTheme.typography.titleMedium
        )

        SuggestionRadiusField(
            radiusKm = suggestionRadiusKm,
            onCommit = viewModel::setSuggestionRadiusKm
        )

        Text(
            text = stringResource(R.string.settings_auto_checkin_section),
            style = MaterialTheme.typography.titleMedium
        )

        val autoCheckInEnabled by viewModel.autoCheckInEnabled.collectAsStateWithLifecycle()
        AutoCheckInSection(
            enabled = autoCheckInEnabled,
            onEnabledChange = viewModel::setAutoCheckInEnabled
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showDisclosure by remember { mutableStateOf(false) }
    var showDeniedHint by remember { mutableStateOf(false) }
    var foregroundGranted by remember { mutableStateOf(AutoCheckInPermissions.hasForegroundLocation(context)) }
    var backgroundGranted by remember { mutableStateOf(AutoCheckInPermissions.hasBackgroundLocation(context)) }
    var notificationsGranted by remember { mutableStateOf(AutoCheckInPermissions.hasNotifications(context)) }

    fun refreshPermissionState() {
        foregroundGranted = AutoCheckInPermissions.hasForegroundLocation(context)
        backgroundGranted = AutoCheckInPermissions.hasBackgroundLocation(context)
        notificationsGranted = AutoCheckInPermissions.hasNotifications(context)
    }

    // Manual grants via the system app-info screen only take effect once we come back to the
    // foreground, not as a launcher callback.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
        onEnabledChange(true)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onEnabledChange(true)
        }
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissionState()
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

    if (enabled) {
        PermissionStatusRow(R.string.settings_auto_checkin_status_foreground, foregroundGranted)
        PermissionStatusRow(R.string.settings_auto_checkin_status_background, backgroundGranted)
        PermissionStatusRow(R.string.settings_auto_checkin_status_notifications, notificationsGranted)
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
private fun PermissionStatusRow(@StringRes labelRes: Int, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(
                if (granted) R.string.settings_auto_checkin_status_granted
                else R.string.settings_auto_checkin_status_denied
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
private fun ImportSummaryCard(summary: PoiSummary?) {
    // No early returns inside composable lambdas: switching branches across
    // recompositions corrupts the composer's group stack (Stack.pop IOOBE).
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
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
}

private fun formatTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
