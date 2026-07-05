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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPoiFile(uri)
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
    }
}

@Composable
private fun ImportSummaryCard(summary: PoiSummary?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (summary == null || summary.importInfo == null) {
                Text(
                    text = stringResource(R.string.settings_no_import),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(
                text = stringResource(
                    R.string.settings_last_import,
                    formatTimestamp(summary.importInfo.importedAt)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(4.dp))
            summary.categoryCounts.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = displayCategory(entry.category),
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

private fun displayCategory(category: String): String =
    category.replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
