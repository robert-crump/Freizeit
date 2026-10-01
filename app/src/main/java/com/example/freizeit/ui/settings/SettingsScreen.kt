package com.example.freizeit.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.repository.ThemeMode
import com.example.freizeit.ui.common.ScreenTitleBar
import com.example.freizeit.ui.common.categoryDisplayName
import java.time.Instant
import java.time.LocalDate
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
    val mergeCandidates by viewModel.mergeCandidates.collectAsStateWithLifecycle()
    val suggestionRadiusKm by viewModel.suggestionRadiusKm.collectAsStateWithLifecycle()
    val notifyFavorites by viewModel.notifyFavorites.collectAsStateWithLifecycle()
    val notifyWantToGo by viewModel.notifyWantToGo.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var showPoiBreakdown by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.backupResults.collect { result ->
            val message = when (result) {
                is BackupResult.ExportSuccess ->
                    context.getString(R.string.settings_backup_export_success, result.count)
                is BackupResult.ImportSuccess ->
                    context.getString(R.string.settings_backup_import_success, result.count)
                is BackupResult.Error -> result.message
            }
            snackbarHostState.showSnackbar(message)
        }
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenTitleBar(title = stringResource(R.string.tab_settings)) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.settings_menu_description)
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_menu_poi_information)) },
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

            SettingsCard(title = stringResource(R.string.settings_appearance_section)) {
                SingleChoiceRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_theme),
                    options = ThemeMode.entries,
                    selected = themeMode,
                    label = { stringResource(it.labelRes) },
                    onSelect = viewModel::setThemeMode
                )
            }

            SettingsCard(title = stringResource(R.string.settings_suggestions_section)) {
                SingleChoiceRow(
                    icon = Icons.Outlined.Place,
                    title = stringResource(R.string.settings_radius),
                    options = suggestionRadiusOptions(suggestionRadiusKm),
                    selected = suggestionRadiusKm,
                    label = { stringResource(R.string.settings_radius_value, it) },
                    onSelect = viewModel::setSuggestionRadiusKm
                )
            }

            SettingsCard(title = stringResource(R.string.settings_notifications_section)) {
                NotificationsSection(
                    favorites = notifyFavorites,
                    wantToGo = notifyWantToGo,
                    onFavoritesChange = viewModel::setNotifyFavorites,
                    onWantToGoChange = viewModel::setNotifyWantToGo
                )
            }

            SettingsCard(title = stringResource(R.string.settings_backup_section)) {
                ActionRow(
                    icon = Icons.Outlined.FileUpload,
                    title = stringResource(R.string.settings_backup_export),
                    description = stringResource(R.string.settings_backup_export_description),
                    onClick = { backupExportPicker.launch(backupFileName(LocalDate.now())) }
                )
                ActionRow(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.settings_backup_import),
                    description = stringResource(R.string.settings_backup_import_description),
                    onClick = { showImportConfirm = true }
                )
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_backup_import_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        backupImportPicker.launch(arrayOf("*/*"))
                    }
                ) {
                    Text(stringResource(R.string.settings_backup_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showPoiBreakdown) {
        AlertDialog(
            onDismissRequest = { showPoiBreakdown = false },
            title = { Text(stringResource(R.string.settings_menu_poi_information)) },
            text = { ImportSummaryContent(summary) },
            confirmButton = {
                TextButton(onClick = { showPoiBreakdown = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        )
    }

    // #49: right after an import completes, walk through any likely custom-POI duplicates one
    // at a time — mergeCandidates.first() rather than a stacked/list dialog, since confirming or
    // dismissing pops the front entry and this recomposes onto whatever's next.
    mergeCandidates.firstOrNull()?.let { candidate ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMerge(candidate) },
            title = { Text(stringResource(R.string.settings_merge_candidate_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_merge_candidate_body,
                        candidate.customPoi.name,
                        candidate.poi.name.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmMerge(candidate) }) {
                    Text(stringResource(R.string.settings_merge_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMerge(candidate) }) {
                    Text(stringResource(R.string.settings_merge_dismiss))
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

/** An icon, a title and a summary line under it. */
@Composable
private fun ActionRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Column {
            Text(text = title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** MyQuotes' App theme row: the current choice as its summary, and a single-choice dialog where
 *  picking an option applies it at once and closes the dialog. Also the Suggestion radius (#74). */
@Composable
private fun <T> SingleChoiceRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ActionRow(icon = icon, title = title, description = label(selected), onClick = { showDialog = true })

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showDialog = false
                                        onSelect(option)
                                    }
                                )
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Text(label(option))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

private enum class NotificationSwitch { FAVORITES, WANT_TO_GO }

/**
 * Owns the whole opt-in dance: turning on the first switch shows the disclosure dialog first
 * (issue #24's Play Store compliance requirement), and only a completed foreground-location grant
 * flips that DataStore-backed switch on — denial leaves it off with a hint, never a
 * silently-broken feature. With one switch already on, the other turns on without asking again.
 */
@Composable
private fun NotificationsSection(
    favorites: Boolean,
    wantToGo: Boolean,
    onFavoritesChange: (Boolean) -> Unit,
    onWantToGoChange: (Boolean) -> Unit
) {
    var pending by remember { mutableStateOf<NotificationSwitch?>(null) }
    var showDisclosure by remember { mutableStateOf(false) }
    var showDeniedHint by remember { mutableStateOf(false) }

    fun set(switch: NotificationSwitch, enabled: Boolean) = when (switch) {
        NotificationSwitch.FAVORITES -> onFavoritesChange(enabled)
        NotificationSwitch.WANT_TO_GO -> onWantToGoChange(enabled)
    }

    fun enablePending() {
        pending?.let { set(it, true) }
        pending = null
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        enablePending()
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enablePending()
        }
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.none { it }) {
            pending = null
            showDeniedHint = true
            return@rememberLauncherForActivityResult
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> enablePending()
        }
    }

    fun onSwitchChange(switch: NotificationSwitch, checked: Boolean) {
        when {
            !checked -> set(switch, false)
            favorites || wantToGo -> set(switch, true)
            else -> {
                showDeniedHint = false
                pending = switch
                showDisclosure = true
            }
        }
    }

    Text(
        text = stringResource(R.string.settings_notifications_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    NotificationSwitchRow(
        icon = Icons.Filled.Favorite,
        title = stringResource(R.string.settings_notifications_favorites),
        checked = favorites,
        onCheckedChange = { onSwitchChange(NotificationSwitch.FAVORITES, it) }
    )
    NotificationSwitchRow(
        icon = Icons.Filled.Bookmark,
        title = stringResource(R.string.settings_notifications_want_to_go),
        checked = wantToGo,
        onCheckedChange = { onSwitchChange(NotificationSwitch.WANT_TO_GO, it) }
    )

    if (showDeniedHint) {
        Text(
            text = stringResource(R.string.settings_notifications_denied_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (showDisclosure) {
        NotificationsDisclosureDialog(
            onConfirm = {
                showDisclosure = false
                foregroundLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onDismiss = {
                showDisclosure = false
                pending = null
            }
        )
    }
}

@Composable
private fun NotificationSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NotificationsDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notifications_disclosure_title)) },
        text = { Text(stringResource(R.string.settings_notifications_disclosure_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_notifications_disclosure_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
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
