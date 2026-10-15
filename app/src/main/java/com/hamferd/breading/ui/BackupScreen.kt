package com.hamferd.breading.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamferd.breading.MainViewModel
import com.hamferd.breading.R
import com.hamferd.breading.describe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Some providers (including Google Drive) label JSON files differently, so accept the likely types.
private val backupMimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Export progress to a file (Google Drive, Downloads, ...) and import it back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readBackup)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Back up & restore") },
                navigationIcon = { ToolbarIcon(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Export", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Saves your checked-off readings, notes, logged readings, and plan start dates " +
                            "to a file. Choose Google Drive in the file picker to keep it in the cloud.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { exportLauncher.launch("bible-reading-backup-${LocalDate.now()}.json") },
                        enabled = !backup.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export backup") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Import", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Restores a backup file. It's combined with what's already on this phone, " +
                            "so nothing here is deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { importLauncher.launch(backupMimeTypes) },
                        enabled = !backup.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Import backup") }
                }
            }

            if (backup.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            backup.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (backup.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    backup.pendingImport?.let { pending ->
        val exported = runCatching {
            Instant.parse(pending.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(longDate)
        }.getOrDefault(pending.exportedAt)
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Import backup?") },
            text = {
                Text(
                    "Backup from $exported with ${describe(pending)}.\n\n" +
                        "It will be combined with this phone's progress. Plan start dates from the backup " +
                        "replace the ones here.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("Import") } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
        )
    }
}
