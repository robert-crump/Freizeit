package com.example.freizeit.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.entity.Favorite
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
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteStatus by viewModel.favoriteStatus.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()

    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var editingFavorite by remember { mutableStateOf<Favorite?>(null) }

    LaunchedEffect(favoriteStatus) {
        if (favoriteStatus is FavoriteStatus.Saved) {
            showAddFavoriteDialog = false
            editingFavorite = null
            viewModel.clearFavoriteStatus()
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
            text = stringResource(R.string.settings_favorites_section),
            style = MaterialTheme.typography.titleMedium
        )

        if (favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_favorites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            favorites.forEach { favorite ->
                FavoriteRow(
                    favorite = favorite,
                    onEdit = { editingFavorite = favorite },
                    onDelete = { viewModel.deleteFavorite(favorite) }
                )
            }
        }

        Button(onClick = { showAddFavoriteDialog = true }) {
            Text(stringResource(R.string.settings_favorite_add))
        }

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
    }

    if (showAddFavoriteDialog) {
        FavoriteDialog(
            initialName = "",
            initialAddress = "",
            status = favoriteStatus,
            onSave = { name, address -> viewModel.addFavorite(name, address) },
            onDismiss = {
                showAddFavoriteDialog = false
                viewModel.clearFavoriteStatus()
            }
        )
    }

    editingFavorite?.let { favorite ->
        FavoriteDialog(
            initialName = favorite.name,
            initialAddress = favorite.address ?: "",
            status = favoriteStatus,
            onSave = { name, address -> viewModel.updateFavorite(favorite, name, address) },
            onDismiss = {
                editingFavorite = null
                viewModel.clearFavoriteStatus()
            }
        )
    }
}

@Composable
private fun FavoriteRow(favorite: Favorite, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(favorite.name, style = MaterialTheme.typography.bodyLarge)
            favorite.address?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_favorite_edit))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_favorite_delete))
        }
    }
}

@Composable
private fun FavoriteDialog(
    initialName: String,
    initialAddress: String,
    status: FavoriteStatus,
    onSave: (name: String, address: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var address by remember { mutableStateOf(initialAddress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_favorite_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_favorite_name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.settings_favorite_address)) },
                    singleLine = true
                )
                if (status is FavoriteStatus.Saving) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.settings_favorite_resolving))
                    }
                }
                if (status is FavoriteStatus.Error) {
                    Text(status.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), address.trim()) },
                enabled = name.isNotBlank() && address.isNotBlank() && status !is FavoriteStatus.Saving
            ) {
                Text(stringResource(R.string.settings_favorite_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_favorite_cancel))
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
